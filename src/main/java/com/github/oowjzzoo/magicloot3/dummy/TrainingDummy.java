package com.github.oowjzzoo.magicloot3.dummy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class TrainingDummy {

    public static NamespacedKey DUMMY_KEY;
    private static Plugin plugin;
    private static final Map<UUID, DummyStats> stats = new ConcurrentHashMap<>();
    private static final Map<UUID, LivingEntity> dummies = new ConcurrentHashMap<>();
    private static final Map<UUID, String> dummyType = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<UUID>> dummyAttackers = new ConcurrentHashMap<>();

    private static final String DEFAULT_NAME = "§e训练假人";
    private static final long IDLE_TIMEOUT_MS = 3000;

    private record HitRecord(long time, double damage) {}
    private static final long SLIDING_WINDOW_MS = 3000;

    private static final class DummyStats {
        long firstHitMs, lastHitMs;
        double totalDamage;
        final List<HitRecord> hits = new ArrayList<>();
    }

    private TrainingDummy() {}

    public static void init(Plugin p) {
        plugin = p;
        DUMMY_KEY = new NamespacedKey(plugin, "training_dummy");
    }

    // --- Spawning ---

    public static Piglin spawnPiglin(Location loc) {
        Piglin e = (Piglin) loc.getWorld().spawnEntity(loc, EntityType.PIGLIN);
        e.setAI(false);
        e.setImmuneToZombification(true);
        e.setAdult();
        configureCommon(e, loc, DEFAULT_NAME, "TRAINING_DUMMY");
        return e;
    }

    public static Skeleton spawnSkeleton(Location loc) {
        Skeleton e = (Skeleton) loc.getWorld().spawnEntity(loc, EntityType.SKELETON);
        e.setAI(false);
        e.setShouldBurnInDay(false);
        configureCommon(e, loc, DEFAULT_NAME, "TRAINING_DUMMY_UNDEAD");
        return e;
    }

    private static void configureCommon(LivingEntity e, Location loc, String name, String typeId) {
        e.getPersistentDataContainer().set(DUMMY_KEY, PersistentDataType.BOOLEAN, true);
        e.setCustomName(name);
        e.setCustomNameVisible(true);
        EntityEquipment equip = e.getEquipment();
        if (equip != null) {
            equip.setHelmet(null); equip.setChestplate(null); equip.setLeggings(null);
            equip.setBoots(null); equip.setItemInMainHand(null); equip.setItemInOffHand(null);
        }
        e.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1024);
        e.setHealth(1024);
        UUID id = e.getUniqueId();
        dummies.put(id, e);
        dummyType.put(id, typeId);
    }

    // --- DPS tracking ---

    public static void recordHit(LivingEntity dummy, double damage, Player attacker) {
        long now = System.currentTimeMillis();
        UUID id = dummy.getUniqueId();
        if (!dummies.containsKey(id)) { dummies.put(id, dummy); }
        DummyStats s = stats.get(id);
        if (s == null || now - s.lastHitMs > IDLE_TIMEOUT_MS) {
            s = new DummyStats(); s.firstHitMs = now;
        }
        s.lastHitMs = now;
        s.totalDamage += damage;
        s.hits.add(new HitRecord(now, damage));
        stats.put(id, s);
        if (attacker != null) {
            dummyAttackers.computeIfAbsent(id, k -> new HashSet<>()).add(attacker.getUniqueId());
        }
    }

    // --- Damage display armor stand ---

    public static void showDamageNumber(LivingEntity dummy, double damage) {
        Location loc = dummy.getLocation().clone();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        loc.add(r.nextDouble(-0.5, 0.5), r.nextDouble(1.0, 2.0), r.nextDouble(-0.5, 0.5));
        ArmorStand display = dummy.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setVisible(false); as.setMarker(true); as.setCustomNameVisible(true);
            as.setCustomName("§c§l" + String.format("%.1f", damage));
        });
        Bukkit.getScheduler().runTaskLater(plugin, display::remove, 30L);
    }

    // --- DPS name ticker ---

    public static void tickAllDummies() {
        long now = System.currentTimeMillis();
        for (var it = stats.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            UUID id = entry.getKey();
            DummyStats s = entry.getValue();
            LivingEntity dummy = dummies.get(id);
            if (dummy == null) {
                org.bukkit.entity.Entity e = Bukkit.getEntity(id);
                if (e instanceof LivingEntity le && le.isValid()) { dummy = le; dummies.put(id, le); }
            }
            if (dummy == null || !dummy.isValid()) {
                it.remove(); dummies.remove(id); dummyAttackers.remove(id); continue;
            }
            if (now - s.lastHitMs > IDLE_TIMEOUT_MS) {
                dummy.setCustomName(DEFAULT_NAME);
                dummy.setCustomNameVisible(true);
                dummyAttackers.remove(id); it.remove();
            } else {
                long cutoff = now - SLIDING_WINDOW_MS;
                s.hits.removeIf(h -> h.time < cutoff);
                double dps;
                long elapsed = now - s.firstHitMs;
                if (elapsed < SLIDING_WINDOW_MS) {
                    dps = s.totalDamage / ((elapsed + 10) / 1000.0);
                } else {
                    double recentDmg = 0;
                    for (HitRecord h : s.hits) recentDmg += h.damage;
                    dps = recentDmg / (SLIDING_WINDOW_MS / 1000.0);
                }
                dummy.setCustomName(String.format("§7DPS: §f§l%.1f", dps));
                dummy.setCustomNameVisible(true);
                String actionMsg = String.format("§7DPS: §f§l%.1f", dps);
                Set<UUID> attackers = dummyAttackers.get(id);
                if (attackers != null) {
                    for (UUID aid : attackers) {
                        Player ap = Bukkit.getPlayer(aid);
                        if (ap != null && ap.isOnline()) ap.sendActionBar(actionMsg);
                    }
                }
            }
        }
    }

    public static void removeDummy(LivingEntity dummy) {
        UUID id = dummy.getUniqueId();
        stats.remove(id); dummies.remove(id); dummyAttackers.remove(id); dummyType.remove(id);
    }

    public static boolean isDummy(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(DUMMY_KEY, PersistentDataType.BOOLEAN);
    }

    public static String getDummyType(LivingEntity entity) {
        return dummyType.getOrDefault(entity.getUniqueId(), "TRAINING_DUMMY");
    }

    public static void cleanup() { stats.clear(); dummies.clear(); dummyAttackers.clear(); dummyType.clear(); }

    public static int cleanupStaleDummies() {
        int removed = 0;
        for (var it = dummies.entrySet().iterator(); it.hasNext();) {
            if (!it.next().getValue().isValid()) { it.remove(); removed++; }
        }
        return removed;
    }

    // --- Equipment helpers ---

    private record EquipmentSlot(int index, boolean armor) {}
    private static final EquipmentSlot[] EQUIP_ORDER = {
        new EquipmentSlot(0, true), new EquipmentSlot(1, true),
        new EquipmentSlot(2, true), new EquipmentSlot(3, true),
        new EquipmentSlot(0, false), new EquipmentSlot(1, false),
    };

    public static ItemStack equipToDummy(LivingEntity dummy, ItemStack item) {
        EntityEquipment eq = dummy.getEquipment();
        if (eq == null) return null;
        for (EquipmentSlot slot : EQUIP_ORDER) {
            if (slot.armor) {
                if (!isAppropriateSlot(item, slot.index)) continue;
                ItemStack current = switch (slot.index) {
                    case 0 -> eq.getHelmet(); case 1 -> eq.getChestplate();
                    case 2 -> eq.getLeggings(); case 3 -> eq.getBoots(); default -> null;
                };
                if (current == null || current.getType().isAir()) { setArmorSlot(eq, slot.index, item); return null; }
                setArmorSlot(eq, slot.index, item); return current;
            } else {
                if (slot.index == 0) {
                    ItemStack current = eq.getItemInMainHand();
                    if (current == null || current.getType().isAir()) { eq.setItemInMainHand(item); return null; }
                    eq.setItemInMainHand(item); return current;
                } else {
                    ItemStack current = eq.getItemInOffHand();
                    if (current == null || current.getType().isAir()) { eq.setItemInOffHand(item); return null; }
                    eq.setItemInOffHand(item); return current;
                }
            }
        }
        return item;
    }

    public static ItemStack unequipFromDummy(LivingEntity dummy) {
        EntityEquipment eq = dummy.getEquipment();
        if (eq == null) return null;
        var boots = eq.getBoots(); if (boots != null && !boots.getType().isAir()) { eq.setBoots(null); return boots; }
        var legs = eq.getLeggings(); if (legs != null && !legs.getType().isAir()) { eq.setLeggings(null); return legs; }
        var chest = eq.getChestplate(); if (chest != null && !chest.getType().isAir()) { eq.setChestplate(null); return chest; }
        var helm = eq.getHelmet(); if (helm != null && !helm.getType().isAir()) { eq.setHelmet(null); return helm; }
        var off = eq.getItemInOffHand(); if (off != null && !off.getType().isAir()) { eq.setItemInOffHand(null); return off; }
        var main = eq.getItemInMainHand(); if (main != null && !main.getType().isAir()) { eq.setItemInMainHand(null); return main; }
        return null;
    }

    public static void dropEquipment(LivingEntity dummy) {
        EntityEquipment eq = dummy.getEquipment();
        if (eq == null) return;
        Location loc = dummy.getLocation();
        for (ItemStack item : new ItemStack[]{eq.getHelmet(), eq.getChestplate(), eq.getLeggings(),
                eq.getBoots(), eq.getItemInMainHand(), eq.getItemInOffHand()}) {
            if (item != null && !item.getType().isAir()) loc.getWorld().dropItemNaturally(loc, item);
        }
    }

    private static boolean isAppropriateSlot(ItemStack item, int armorSlot) {
        if (item == null) return false;
        String name = item.getType().name();
        return switch (armorSlot) {
            case 0 -> name.endsWith("_HELMET") || name.contains("HEAD");
            case 1 -> name.endsWith("_CHESTPLATE");
            case 2 -> name.endsWith("_LEGGINGS");
            case 3 -> name.endsWith("_BOOTS");
            default -> false;
        };
    }

    private static void setArmorSlot(EntityEquipment eq, int slot, ItemStack item) {
        switch (slot) {
            case 0 -> eq.setHelmet(item); case 1 -> eq.setChestplate(item);
            case 2 -> eq.setLeggings(item); case 3 -> eq.setBoots(item);
        }
    }
}
