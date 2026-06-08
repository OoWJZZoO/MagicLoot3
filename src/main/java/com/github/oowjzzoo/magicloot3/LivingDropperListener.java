package com.github.oowjzzoo.magicloot3;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dropper;
import org.bukkit.block.data.type.Dispenser;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import com.github.oowjzzoo.magicloot3.machines.LivingDropper;

public class LivingDropperListener implements Listener {

    /** Unique holder to identify our binding GUI inventory. */
    private static final InventoryHolder BINDING_HOLDER = new InventoryHolder() {
        @Override public Inventory getInventory() { return null; }
    };

    private static final String GUI_TITLE = ChatColor.translateAlternateColorCodes('&',
            Messages.get("living_dropper.gui_title"));

    public LivingDropperListener(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // --- InventoryOpen → shift detection for binding GUI ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (e.getInventory().getType() != InventoryType.DROPPER) return;
        Location loc = e.getInventory().getLocation();
        if (loc == null) return;
        if (!LivingDropper.isLivingDropper(loc)) return;
        if (!e.getPlayer().isSneaking()) return;

        e.setCancelled(true);
        openBindingGUI((Player) e.getPlayer(), loc);
    }

    // --- BlockDispenseEvent → Simulate player drop ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent e) {
        Block block = e.getBlock();
        if (!LivingDropper.isLivingDropper(block.getLocation())) return;

        Location loc = block.getLocation();
        UUID boundUUID = LivingDropper.getBoundUUID(loc);
        Player bound;
        if (boundUUID == null || (bound = Bukkit.getPlayer(boundUUID)) == null
                || !bound.isOnline()) {
            // Unbound or offline: cancel so vanilla dropper doesn't dispense either
            e.setCancelled(true);
            return;
        }

        // Vanilla removed the item from inventory BEFORE firing this event.
        // On cancel, Paper puts it back AFTER the event. So we must cancel
        // (to suppress vanilla's own Item entity), then schedule inventory
        // removal + our own Item entity for after the put-back.
        e.setCancelled(true);

        ItemStack toDrop = e.getItem().clone();
        BlockFace face = getFacing(block);

        // Exact vanilla position: blockCenter + facing * 0.7 (DispenserBlock.getDispensePosition)
        double x = block.getX() + 0.5 + face.getModX() * 0.7;
        double y = block.getY() + 0.5 + face.getModY() * 0.7;
        double z = block.getZ() + 0.5 + face.getModZ() * 0.7;
        // Y adjustment from DefaultDispenseItemBehavior.spawnItem
        if (face == BlockFace.UP || face == BlockFace.DOWN) {
            y -= 0.125;
        } else {
            y -= 0.15625;
        }
        Location dropLoc = new Location(block.getWorld(), x, y, z);

        Bukkit.getScheduler().runTask(MagicLoot3.getInstance(), () -> {
            if (!removeFromDropper(block, toDrop)) return;

            Item itemEntity = block.getWorld().dropItem(dropLoc, toDrop);
            // Exact vanilla velocity from DefaultDispenseItemBehavior.spawnItem
            ThreadLocalRandom r = ThreadLocalRandom.current();
            double pow = r.nextDouble() * 0.1 + 0.2;
            double spread = 0.0172275 * 6; // accuracy = 6
            itemEntity.setVelocity(new Vector(
                    face.getModX() * pow + (r.nextDouble() - r.nextDouble()) * spread,
                    0.2 + (r.nextDouble() - r.nextDouble()) * spread,
                    face.getModZ() * pow + (r.nextDouble() - r.nextDouble()) * spread));

            PlayerDropItemEvent dropEvent = new PlayerDropItemEvent(bound, itemEntity);
            Bukkit.getPluginManager().callEvent(dropEvent);

            if (dropEvent.isCancelled()) {
                itemEntity.remove();
                returnToDropper(block, toDrop);
            }
        });
    }

    // --- Clean up on close ---

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() == BINDING_HOLDER) {
            playerToOpenLoc.remove(e.getPlayer().getUniqueId());
        }
    }

    // --- Binding GUI clicks ---

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() != BINDING_HOLDER) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
        if (e.getSlot() != 4) return;

        Player player = (Player) e.getWhoClicked();
        Location loc = playerToOpenLoc.get(player.getUniqueId());
        if (loc == null) return;

        UUID current = LivingDropper.getBoundUUID(loc);
        if (current != null && current.equals(player.getUniqueId())) {
            LivingDropper.unbind(loc);
        } else {
            LivingDropper.bind(loc, player.getUniqueId());
        }
        e.getInventory().setItem(4, buildBindButton(loc));
    }

    // --- GUI builder ---

    private static final java.util.Map<UUID, Location> playerToOpenLoc = new java.util.HashMap<>();

    static void openBindingGUI(Player player, Location loc) {
        Inventory inv = Bukkit.createInventory(BINDING_HOLDER, 9, GUI_TITLE);
        playerToOpenLoc.put(player.getUniqueId(), loc);

        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bm = border.getItemMeta(); bm.setDisplayName(" "); border.setItemMeta(bm);
        for (int i = 0; i < 9; i++) {
            if (i != 4) inv.setItem(i, border);
        }

        inv.setItem(4, buildBindButton(loc));
        player.openInventory(inv);
    }

    static ItemStack buildBindButton(Location loc) {
        UUID boundUUID = LivingDropper.getBoundUUID(loc);
        ItemStack btn;
        if (boundUUID != null) {
            btn = new ItemStack(Material.PLAYER_HEAD);
            if (btn.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skull) {
                skull.setOwningPlayer(Bukkit.getOfflinePlayer(boundUUID));
                btn.setItemMeta(skull);
            }
        } else {
            btn = new ItemStack(Material.NAME_TAG);
        }
        ItemMeta meta = btn.getItemMeta();
        meta.setDisplayName(Messages.get("living_dropper.bind_button_title"));

        List<String> lore = new java.util.ArrayList<>();
        if (boundUUID != null) {
            Player bound = Bukkit.getPlayer(boundUUID);
            String name = bound != null ? bound.getName() : boundUUID.toString().substring(0, 8);
            lore.add(Messages.get("living_dropper.bound_to", name));
            lore.add(bound != null && bound.isOnline()
                    ? Messages.get("living_dropper.online")
                    : Messages.get("living_dropper.offline"));
        } else {
            lore.add(Messages.get("living_dropper.unbound"));
        }
        lore.add("");
        lore.add(Messages.get("living_dropper.click_to_bind"));
        meta.setLore(lore);
        btn.setItemMeta(meta);
        return btn;
    }

    // --- Dropper inventory helpers ---

    private static boolean removeFromDropper(Block block, ItemStack target) {
        if (!(block.getState() instanceof Dropper dropper)) return false;
        Inventory inv = dropper.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack slot = inv.getItem(i);
            if (slot != null && slot.isSimilar(target)) {
                if (slot.getAmount() > 1) {
                    slot.setAmount(slot.getAmount() - 1);
                } else {
                    inv.setItem(i, null);
                }
                return true;
            }
        }
        return false;
    }

    private static void returnToDropper(Block block, ItemStack item) {
        if (!(block.getState() instanceof Dropper dropper)) return;
        dropper.getInventory().addItem(item);
    }

    private static BlockFace getFacing(Block block) {
        if (block.getBlockData() instanceof Dispenser dispenser) {
            return dispenser.getFacing();
        }
        return BlockFace.DOWN;
    }

    static int cleanupStalePlayerLocs() {
        int removed = 0;
        var it = playerToOpenLoc.entrySet().iterator();
        while (it.hasNext()) {
            if (Bukkit.getPlayer(it.next().getKey()) == null) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }
}
