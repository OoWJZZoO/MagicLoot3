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

        e.setCancelled(true);

        Location loc = block.getLocation();
        UUID boundUUID = LivingDropper.getBoundUUID(loc);
        if (boundUUID == null) return;

        Player bound = Bukkit.getPlayer(boundUUID);
        if (bound == null || !bound.isOnline()) return;

        ItemStack toDrop = e.getItem().clone();

        if (!removeFromDropper(block, toDrop)) return;

        // Spawn item at vanilla dropper position: block center + facing * 0.5
        BlockFace face = getFacing(block);
        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5)
                .add(face.getModX() * 0.5, face.getModY() * 0.5, face.getModZ() * 0.5);
        Item itemEntity = block.getWorld().dropItem(dropLoc, toDrop);

        // Dispenser-style velocity: slight push in facing direction + random spread
        ThreadLocalRandom r = ThreadLocalRandom.current();
        itemEntity.setVelocity(new Vector(
                face.getModX() * 0.1 + (r.nextDouble() - 0.5) * 0.1,
                face.getModY() * 0.1 + (r.nextDouble() - 0.5) * 0.1,
                face.getModZ() * 0.1 + (r.nextDouble() - 0.5) * 0.1));

        PlayerDropItemEvent dropEvent = new PlayerDropItemEvent(bound, itemEntity);
        Bukkit.getPluginManager().callEvent(dropEvent);

        if (dropEvent.isCancelled()) {
            itemEntity.remove();
            returnToDropper(block, toDrop);
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
        Location loc = playerToOpenLoc.remove(player.getUniqueId());
        if (loc == null) return;

        UUID current = LivingDropper.getBoundUUID(loc);
        if (current != null && current.equals(player.getUniqueId())) {
            LivingDropper.unbind(loc);
        } else {
            LivingDropper.bind(loc, player.getUniqueId());
        }
        player.closeInventory();
        openBindingGUI(player, loc);
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

        UUID boundUUID = LivingDropper.getBoundUUID(loc);
        ItemStack btn = new ItemStack(Material.NAME_TAG);
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
        inv.setItem(4, btn);

        player.openInventory(inv);
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
}
