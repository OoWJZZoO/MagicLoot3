package com.github.oowjzzoo.magicloot3.dummy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Piglin;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class TrainingDummy {

    public static NamespacedKey DUMMY_KEY;
    private static final Map<UUID, DummyStats> stats = new ConcurrentHashMap<>();
    // Piglin reference cache for ticker lookup
    private static final Map<UUID, Piglin> dummies = new ConcurrentHashMap<>();

    private static final String DEFAULT_NAME = "§e训练假人";
    private static final long IDLE_TIMEOUT_MS = 3000;

    private record DummyStats(long firstHitMs, long lastHitMs, double totalDamage) {}

    private TrainingDummy() {}

    public static void init(Plugin plugin) {
        DUMMY_KEY = new NamespacedKey(plugin, "training_dummy");
    }

    // --- Piglin creation ---

    public static Piglin spawn(Location loc) {
        Piglin piglin = (Piglin) loc.getWorld().spawnEntity(loc, EntityType.PIGLIN);
        piglin.setAI(false);
        piglin.setCollidable(false);
        piglin.setImmuneToZombification(true);
        piglin.getPersistentDataContainer().set(DUMMY_KEY, PersistentDataType.BOOLEAN, true);
        piglin.setCustomName(DEFAULT_NAME);
        piglin.setCustomNameVisible(true);
        // Clear default equipment
        EntityEquipment equip = piglin.getEquipment();
        if (equip != null) {
            equip.setHelmet(null);
            equip.setChestplate(null);
            equip.setLeggings(null);
            equip.setBoots(null);
            equip.setItemInMainHand(null);
            equip.setItemInOffHand(null);
        }
        // Set max health then heal to full
        piglin.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1024);
        piglin.setHealth(1024);
        dummies.put(piglin.getUniqueId(), piglin);
        return piglin;
    }

    // --- DPS tracking ---

    public static void recordHit(Piglin piglin, double damage) {
        long now = System.currentTimeMillis();
        UUID id = piglin.getUniqueId();
        DummyStats s = stats.get(id);
        if (s == null || now - s.lastHitMs > IDLE_TIMEOUT_MS) {
            stats.put(id, new DummyStats(now, now, damage));
        } else {
            stats.put(id, new DummyStats(s.firstHitMs, now, s.totalDamage + damage));
        }
    }

    // --- Damage display armor stand ---

    public static void showDamageNumber(Piglin piglin, double damage) {
        Location loc = piglin.getLocation().clone();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double ox = r.nextDouble(-0.5, 0.5);
        double oy = r.nextDouble(1.0, 2.0);
        double oz = r.nextDouble(-0.5, 0.5);
        loc.add(ox, oy, oz);
        ArmorStand display = piglin.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setMarker(true);
            as.setCustomNameVisible(true);
            as.setCustomName("§c§l" + String.format("%.1f", damage));
        });
        Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("MagicLoot3"),
                display::remove, 30L);
    }

    // --- DPS name ticker ---

    public static void tickAllDummies() {
        long now = System.currentTimeMillis();
        for (var it = stats.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            UUID id = entry.getKey();
            DummyStats s = entry.getValue();
            Piglin piglin = dummies.get(id);
            if (piglin == null || !piglin.isValid()) {
                it.remove();
                dummies.remove(id);
                continue;
            }
            if (now - s.lastHitMs > IDLE_TIMEOUT_MS) {
                piglin.setCustomName(DEFAULT_NAME);
                it.remove();
            } else {
                double dps = s.totalDamage / ((now - s.firstHitMs + 10) / 1000.0);
                piglin.setCustomName(String.format("§7DPS: §f§l%.1f", dps));
            }
        }
    }

    public static void removeDummy(Piglin piglin) {
        UUID id = piglin.getUniqueId();
        stats.remove(id);
        dummies.remove(id);
    }

    public static boolean isDummy(Piglin piglin) {
        return piglin.getPersistentDataContainer().has(DUMMY_KEY, PersistentDataType.BOOLEAN);
    }

    public static void cleanup() {
        stats.clear();
        dummies.clear();
    }

    // --- Equipment helpers ---

    private static final EntityEquipmentSlot[] EQUIP_ORDER = {
        new EntityEquipmentSlot(0, true),  // helmet via index
        new EntityEquipmentSlot(1, true),  // chestplate
        new EntityEquipmentSlot(2, true),  // leggings
        new EntityEquipmentSlot(3, true),  // boots
        new EntityEquipmentSlot(0, false), // main hand
        new EntityEquipmentSlot(1, false), // off hand
    };

    /** Equip item to piglin. Returns displaced item (or null). */
    public static org.bukkit.inventory.ItemStack equipToPiglin(Piglin piglin, org.bukkit.inventory.ItemStack item) {
        EntityEquipment eq = piglin.getEquipment();
        if (eq == null) return null;
        for (EntityEquipmentSlot slot : EQUIP_ORDER) {
            org.bukkit.inventory.ItemStack current;
            if (slot.armor) {
                if (!isAppropriateSlot(item, slot.index)) continue;
                current = switch (slot.index) {
                    case 0 -> eq.getHelmet();
                    case 1 -> eq.getChestplate();
                    case 2 -> eq.getLeggings();
                    case 3 -> eq.getBoots();
                    default -> null;
                };
                if (current == null || current.getType().isAir()) {
                    setArmorSlot(eq, slot.index, item);
                    return null;
                }
                setArmorSlot(eq, slot.index, item);
                return current;
            } else {
                if (slot.index == 0) {
                    current = eq.getItemInMainHand();
                    if (current == null || current.getType().isAir()) {
                        eq.setItemInMainHand(item);
                        return null;
                    }
                    eq.setItemInMainHand(item);
                    return current;
                } else {
                    current = eq.getItemInOffHand();
                    if (current == null || current.getType().isAir()) {
                        eq.setItemInOffHand(item);
                        return null;
                    }
                    eq.setItemInOffHand(item);
                    return current;
                }
            }
        }
        return item; // All slots full
    }

    /** Unequip one item from piglin. Order: boots→leggings→chestplate→helmet→offhand→mainhand */
    public static org.bukkit.inventory.ItemStack unequipFromPiglin(Piglin piglin) {
        EntityEquipment eq = piglin.getEquipment();
        if (eq == null) return null;
        // Boots first
        if (eq.getBoots() != null && !eq.getBoots().getType().isAir()) {
            var item = eq.getBoots();
            eq.setBoots(null);
            return item;
        }
        if (eq.getLeggings() != null && !eq.getLeggings().getType().isAir()) {
            var item = eq.getLeggings();
            eq.setLeggings(null);
            return item;
        }
        if (eq.getChestplate() != null && !eq.getChestplate().getType().isAir()) {
            var item = eq.getChestplate();
            eq.setChestplate(null);
            return item;
        }
        if (eq.getHelmet() != null && !eq.getHelmet().getType().isAir()) {
            var item = eq.getHelmet();
            eq.setHelmet(null);
            return item;
        }
        if (eq.getItemInOffHand() != null && !eq.getItemInOffHand().getType().isAir()) {
            var item = eq.getItemInOffHand();
            eq.setItemInOffHand(null);
            return item;
        }
        if (eq.getItemInMainHand() != null && !eq.getItemInMainHand().getType().isAir()) {
            var item = eq.getItemInMainHand();
            eq.setItemInMainHand(null);
            return item;
        }
        return null;
    }

    public static void dropEquipment(Piglin piglin) {
        EntityEquipment eq = piglin.getEquipment();
        if (eq == null) return;
        Location loc = piglin.getLocation();
        var items = new org.bukkit.inventory.ItemStack[]{
            eq.getHelmet(), eq.getChestplate(), eq.getLeggings(), eq.getBoots(),
            eq.getItemInMainHand(), eq.getItemInOffHand()
        };
        for (var item : items) {
            if (item != null && !item.getType().isAir()) {
                loc.getWorld().dropItemNaturally(loc, item);
            }
        }
    }

    // --- Private helpers ---

    private record EntityEquipmentSlot(int index, boolean armor) {}

    private static boolean isAppropriateSlot(org.bukkit.inventory.ItemStack item, int armorSlot) {
        if (item == null) return false;
        var type = item.getType();
        return switch (armorSlot) {
            case 0 -> type.name().endsWith("_HELMET") || type.name().endsWith("_SKULL") || type.name().contains("HEAD");
            case 1 -> type.name().endsWith("_CHESTPLATE");
            case 2 -> type.name().endsWith("_LEGGINGS");
            case 3 -> type.name().endsWith("_BOOTS");
            default -> false;
        };
    }

    private static void setArmorSlot(EntityEquipment eq, int slot, org.bukkit.inventory.ItemStack item) {
        switch (slot) {
            case 0 -> eq.setHelmet(item);
            case 1 -> eq.setChestplate(item);
            case 2 -> eq.setLeggings(item);
            case 3 -> eq.setBoots(item);
        }
    }
}
