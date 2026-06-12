package com.github.oowjzzoo.magicloot3.dummy;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.github.oowjzzoo.magicloot3.Messages;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;

public class TrainingDummyListener implements Listener {

    private final Plugin plugin;
    private static final java.util.Map<UUID, Long> lastInteract = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long INTERACT_COOLDOWN = 300;

    public static int cleanupStaleInteract() {
        int removed = 0;
        var it = lastInteract.entrySet().iterator();
        while (it.hasNext()) {
            if (Bukkit.getPlayer(it.next().getKey()) == null) { it.remove(); removed++; }
        }
        return removed;
    }

    public TrainingDummyListener(Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // --- Placement ---

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) return;
        ItemStack item = e.getItem();
        if (item == null) return;
        SlimefunItem sfItem = SlimefunItem.getByItem(item);
        if (sfItem == null) return;
        String id = sfItem.getId();
        if (!"MAGICLOOT_TRAINING_DUMMY".equals(id) && !"MAGICLOOT_TRAINING_DUMMY_UNDEAD".equals(id)) return;

        if (e.getClickedBlock() == null) return;
        e.setCancelled(true);
        Player player = e.getPlayer();

        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
        else player.getInventory().setItem(e.getHand(), null);

        Location loc = e.getClickedBlock().getRelative(e.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        if ("MAGICLOOT_TRAINING_DUMMY_UNDEAD".equals(id)) {
            TrainingDummy.spawnSkeleton(loc);
        } else {
            TrainingDummy.spawnPiglin(loc);
        }
        player.getWorld().playSound(loc, Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1, 1);
    }

    // --- Damage ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity dummy)) return;
        if (!TrainingDummy.isDummy(dummy)) return;

        double finalDamage = e.getFinalDamage();
        Player attacker = null;
        if (e instanceof EntityDamageByEntityEvent dmg) {
            if (dmg.getDamager() instanceof Player p) attacker = p;
            else if (dmg.getDamager() instanceof org.bukkit.entity.Projectile proj
                    && proj.getShooter() instanceof Player p) attacker = p;
        }
        TrainingDummy.recordHit(dummy, finalDamage, attacker);
        TrainingDummy.showDamageNumber(dummy, finalDamage);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (dummy.isValid()) dummy.setHealth(1024);
        });
    }

    // --- Prevent mob targeting ---

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof LivingEntity le && TrainingDummy.isDummy(le)) {
            e.setCancelled(true);
        }
    }

    // --- Death ---

    @EventHandler
    public void onDummyDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof LivingEntity dummy)) return;
        if (!TrainingDummy.isDummy(dummy)) return;

        e.setDroppedExp(0);
        e.getDrops().clear();
        TrainingDummy.dropEquipment(dummy);
        String typeId = TrainingDummy.getDummyType(dummy);
        SlimefunItem sfItem = SlimefunItem.getById(typeId);
        if (sfItem != null)
            dummy.getWorld().dropItemNaturally(dummy.getLocation(), sfItem.getItem().clone());
        TrainingDummy.removeDummy(dummy);

        Player killer = dummy.getKiller();
        if (killer != null) {
            Bukkit.broadcastMessage(Messages.get("dummy.kill_broadcast", killer.getName()));
        }
    }

    // --- Player interaction ---

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof LivingEntity dummy)) return;
        if (!TrainingDummy.isDummy(dummy)) return;
        e.setCancelled(true);

        Player player = e.getPlayer();
        long now = System.currentTimeMillis();
        UUID pid = player.getUniqueId();
        Long last = lastInteract.get(pid);
        if (last != null && now - last < INTERACT_COOLDOWN) return;
        lastInteract.put(pid, now);
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean emptyHand = hand == null || hand.getType().isAir();
        boolean sneaking = player.isSneaking();

        if (sneaking) {
            TrainingDummy.dropEquipment(dummy);
            String typeId = TrainingDummy.getDummyType(dummy);
            SlimefunItem sfItem = SlimefunItem.getById(typeId);
            if (sfItem != null)
                dummy.getWorld().dropItemNaturally(dummy.getLocation(), sfItem.getItem().clone());
            TrainingDummy.removeDummy(dummy);
            dummy.remove();
            dummy.getWorld().playSound(dummy.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.BLOCKS, 1, 1);
        } else if (!emptyHand) {
            ItemStack oneItem = hand.clone();
            oneItem.setAmount(1);
            ItemStack returned = TrainingDummy.equipToDummy(dummy, oneItem);
            if (returned != null && returned.isSimilar(oneItem) && returned.getAmount() == oneItem.getAmount()) return;
            if (hand.getAmount() > 1) hand.setAmount(hand.getAmount() - 1);
            else player.getInventory().setItemInMainHand(null);
            player.updateInventory();
            if (returned != null) {
                var leftover = player.getInventory().addItem(returned);
                for (var drop : leftover.values()) dummy.getWorld().dropItemNaturally(dummy.getLocation(), drop);
            }
            dummy.getWorld().playSound(dummy.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, SoundCategory.BLOCKS, 1, 1);
        } else {
            ItemStack removed = TrainingDummy.unequipFromDummy(dummy);
            if (removed != null) {
                var leftover = player.getInventory().addItem(removed);
                for (var drop : leftover.values()) dummy.getWorld().dropItemNaturally(dummy.getLocation(), drop);
                player.updateInventory();
                dummy.getWorld().playSound(dummy.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, SoundCategory.BLOCKS, 1, 1);
            }
        }
    }

    // --- Block item pickup ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof LivingEntity le && TrainingDummy.isDummy(le)) {
            e.setCancelled(true);
        }
    }
}
