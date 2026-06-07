package com.github.oowjzzoo.magicloot3;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Dropper;
import org.bukkit.block.data.type.Dispenser;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import com.github.oowjzzoo.magicloot3.machines.LivingDropper;

public class LivingDropperListener implements Listener {

    private static final String GUI_TITLE = ChatColor.translateAlternateColorCodes('&',
            Messages.get("living_dropper.gui_title"));

    public LivingDropperListener(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // --- Shift+Right-click → Binding GUI ---

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null || block.getType() != Material.DROPPER) return;
        if (!LivingDropper.isLivingDropper(block.getLocation())) return;
        if (!e.getPlayer().isSneaking()) return;

        e.setCancelled(true);
        openBindingGUI(e.getPlayer(), block.getLocation());
    }

    // --- BlockDispenseEvent → Simulate player drop ---

    @EventHandler(priority = EventPriority.HIGH)
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

        // Remove one item from dropper inventory
        if (!removeFromDropper(block, toDrop)) return;

        // Create Item entity in front of dropper
        Block facingBlock = getFacingBlock(block);
        Location dropLoc = facingBlock.getLocation().add(0.5, 0.5, 0.5);
        Item itemEntity = block.getWorld().dropItem(dropLoc, toDrop);
        itemEntity.setVelocity(new Vector(0, 0, 0));

        // Fire synthetic PlayerDropItemEvent
        PlayerDropItemEvent dropEvent = new PlayerDropItemEvent(bound, itemEntity);
        Bukkit.getPluginManager().callEvent(dropEvent);

        if (dropEvent.isCancelled()) {
            itemEntity.remove();
            returnToDropper(block, toDrop);
        }
    }

    // --- Binding GUI clicks ---

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getView().getTitle().equals(GUI_TITLE))) return;
        e.setCancelled(true);
        if (e.getSlot() != 4) return;

        Player player = (Player) e.getWhoClicked();
        // Get the dropper location: the GUI title doesn't encode it, so
        // we retrieve it from the player's last right-click. Use a simple approach:
        // try each LivingDropper and open the one whose bind player just changed.
        // Better: store the open-GUI location per player.
        Location loc = playerToOpenLoc.remove(player.getUniqueId());
        if (loc == null) return;

        UUID current = LivingDropper.getBoundUUID(loc);
        if (current != null && current.equals(player.getUniqueId())) {
            // Unbind self
            LivingDropper.unbind(loc);
        } else {
            // Bind to self (overwrite any existing binding)
            LivingDropper.bind(loc, player.getUniqueId());
        }
        player.closeInventory();
        openBindingGUI(player, loc);
    }

    // --- GUI builder ---

    private static final java.util.Map<UUID, Location> playerToOpenLoc = new java.util.HashMap<>();

    static void openBindingGUI(Player player, Location loc) {
        Inventory inv = Bukkit.createInventory(null, 9, GUI_TITLE);
        playerToOpenLoc.put(player.getUniqueId(), loc);

        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bm = border.getItemMeta(); bm.setDisplayName(" "); border.setItemMeta(bm);
        for (int i = 0; i < 9; i++) {
            if (i != 4) inv.setItem(i, border);
        }

        // Bind button
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

    private static Block getFacingBlock(Block block) {
        if (block.getBlockData() instanceof Dispenser dispenser) {
            return block.getRelative(dispenser.getFacing());
        }
        return block.getRelative(org.bukkit.block.BlockFace.DOWN);
    }
}
