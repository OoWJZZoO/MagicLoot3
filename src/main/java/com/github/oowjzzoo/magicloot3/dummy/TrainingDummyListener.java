package com.github.oowjzzoo.magicloot3.dummy;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;

public class TrainingDummyListener implements Listener {

    private final Plugin plugin;
    // Pending transformations: player UUID → expected placement time
    private static final java.util.Map<UUID, Long> pendingPlacements = new java.util.concurrent.ConcurrentHashMap<>();

    public TrainingDummyListener(Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // --- Armor stand → Piglin transformation ---

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) return;
        ItemStack item = e.getItem();
        if (item == null) return;
        SlimefunItem sfItem = SlimefunItem.getByItem(item);
        if (sfItem == null || !"TRAINING_DUMMY".equals(sfItem.getId())) return;

        // Cancel to prevent vanilla armor stand placement
        e.setCancelled(true);
        Player player = e.getPlayer();

        // Consume one item
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItem(e.getHand(), null);
        }

        // Determine placement location
        Location loc;
        if (e.getClickedBlock() != null) {
            loc = e.getClickedBlock().getRelative(e.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        } else {
            loc = player.getLocation();
        }

        // Spawn piglin directly instead of armor stand
        Piglin piglin = TrainingDummy.spawn(loc);
        piglin.getWorld().playSound(loc, Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1, 1);
        player.sendMessage(ChatColor.GREEN + "训练假人已放置!");
    }

    // --- Damage: display number + record DPS + heal ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Piglin piglin)) return;
        if (!TrainingDummy.isDummy(piglin)) return;

        double finalDamage = e.getFinalDamage();
        TrainingDummy.showDamageNumber(piglin, finalDamage);
        TrainingDummy.recordHit(piglin, finalDamage);

        // Heal to full next tick
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (piglin.isValid()) piglin.setHealth(1024);
        });
    }

    // --- Player interaction ---

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Piglin piglin)) return;
        if (!TrainingDummy.isDummy(piglin)) return;
        e.setCancelled(true);

        Player player = e.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean emptyHand = hand == null || hand.getType().isAir();
        boolean sneaking = player.isSneaking();

        if (sneaking && emptyHand) {
            // Remove dummy: drop equipment + SF item
            TrainingDummy.dropEquipment(piglin);
            SlimefunItem sfItem = SlimefunItem.getById("TRAINING_DUMMY");
            if (sfItem != null)
                piglin.getWorld().dropItemNaturally(piglin.getLocation(), sfItem.getItem().clone());
            TrainingDummy.removeDummy(piglin);
            piglin.remove();
            piglin.getWorld().playSound(piglin.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.BLOCKS, 1, 1);
        } else if (!emptyHand) {
            // Equip item to piglin
            ItemStack returned = TrainingDummy.equipToPiglin(piglin, hand.clone());
            if (returned != null && returned.equals(hand)) {
                // Nothing equipped (all slots full or not appropriate)
                return;
            }
            // Consume player's hand item
            if (hand.getAmount() > 1) {
                hand.setAmount(hand.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            player.updateInventory();
            // Return displaced item to player or drop
            if (returned != null) {
                var leftover = player.getInventory().addItem(returned);
                for (var drop : leftover.values()) {
                    piglin.getWorld().dropItemNaturally(piglin.getLocation(), drop);
                }
            }
            piglin.getWorld().playSound(piglin.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, SoundCategory.BLOCKS, 1, 1);
        } else {
            // Empty hand: unequip one piece
            ItemStack removed = TrainingDummy.unequipFromPiglin(piglin);
            if (removed != null) {
                var leftover = player.getInventory().addItem(removed);
                for (var drop : leftover.values()) {
                    piglin.getWorld().dropItemNaturally(piglin.getLocation(), drop);
                }
                player.updateInventory();
                piglin.getWorld().playSound(piglin.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, SoundCategory.BLOCKS, 1, 1);
            }
        }
    }

    // --- Block item pickup ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Piglin piglin && TrainingDummy.isDummy(piglin)) {
            e.setCancelled(true);
        }
    }
}
