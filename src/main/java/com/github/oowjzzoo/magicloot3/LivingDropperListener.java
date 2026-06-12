package com.github.oowjzzoo.magicloot3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
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
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import com.github.oowjzzoo.magicloot3.machines.LivingDropper;

public class LivingDropperListener implements Listener {

    private static final Map<UUID, Location> playerToOpenLoc = new HashMap<>();

    public LivingDropperListener(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // --- InventoryOpen -> shift detection for binding GUI ---

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

    // --- BlockDispenseEvent -> Simulate player drop ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent e) {
        Block block = e.getBlock();
        if (!LivingDropper.isLivingDropper(block.getLocation())) return;

        Location loc = block.getLocation();
        UUID boundUUID = LivingDropper.getBoundUUID(loc);
        Player bound;
        if (boundUUID == null || (bound = Bukkit.getPlayer(boundUUID)) == null
                || !bound.isOnline()) {
            e.setCancelled(true);
            return;
        }

        e.setCancelled(true);

        ItemStack toDrop = e.getItem().clone();
        BlockFace face = getFacing(block);

        double x = block.getX() + 0.5 + face.getModX() * 0.7;
        double y = block.getY() + 0.5 + face.getModY() * 0.7;
        double z = block.getZ() + 0.5 + face.getModZ() * 0.7;
        if (face == BlockFace.UP || face == BlockFace.DOWN) {
            y -= 0.125;
        } else {
            y -= 0.15625;
        }
        Location dropLoc = new Location(block.getWorld(), x, y, z);

        Bukkit.getScheduler().runTask(MagicLoot3.getInstance(), () -> {
            if (!removeFromDropper(block, toDrop)) return;

            Item itemEntity = block.getWorld().dropItem(dropLoc, toDrop);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            double pow = r.nextDouble() * 0.1 + 0.2;
            double spread = 0.0172275 * 6;
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

    // --- Binding GUI (9-slot MenuGUI) ---

    static void openBindingGUI(Player player, Location loc) {
        playerToOpenLoc.put(player.getUniqueId(), loc);

        // Title matches the item's display name
        var sfItem = io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
                .getById("MAGICLOOT_LIVING_DROPPER");
        String title = sfItem != null ? sfItem.getItemName()
                : Messages.get("living_dropper.gui_title");

        MenuGUI menu = new MenuGUI(9, title);
        menu.fillBg(0,1,2,3, 5,6,7,8);
        menu.setButton(4, buildBindButton(loc), (pl, s, a) -> {
            UUID current = LivingDropper.getBoundUUID(loc);
            if (current != null && current.equals(pl.getUniqueId())) {
                LivingDropper.unbind(loc);
            } else {
                LivingDropper.bind(loc, pl.getUniqueId());
            }
            menu.getInventory().setItem(4, buildBindButton(loc));
        });
        menu.onClose(() -> playerToOpenLoc.remove(player.getUniqueId()));
        menu.open(player);
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
