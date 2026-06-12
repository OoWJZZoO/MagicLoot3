package com.github.oowjzzoo.magicloot3;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** PersistentDataContainer keys for MagicLoot3 items and entities. */
public final class ItemKeys {

    /** Tier stored on item: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, UNKNOWN */
    public static NamespacedKey TIER;
    /** Potion effects stored on item: "speed:2", "strength:3" */
    public static NamespacedKey EFFECTS;
    /** Tag on villager marking it as a Lost Librarian */
    public static NamespacedKey LIBRARIAN;
    /** Tag on item frame marking it as a librarian's display frame */
    public static NamespacedKey LIBRARIAN_FRAME;
    /** Attributes stored on gem: "attack_damage:5.0,movement_speed:-0.02" */
    public static NamespacedKey GEM_ATTRS;
    /** Compatible slot mask stored on gem (comma-separated) */
    public static NamespacedKey GEM_SLOTS;
    /** Slotted gem SF IDs stored on totem (comma-separated, 9 slots) */
    public static NamespacedKey TOTEM_GEMS;
    /** Capacity increase stored on gem */
    public static NamespacedKey GEM_CAPACITY;
    /** Current max gem capacity stored on totem */
    public static NamespacedKey TOTEM_CAPACITY;
    /** Tier stored on totem: 1=Common, 2=Uncommon, 3=Extraordinary, etc. Separate from equipment tier system. */
    public static NamespacedKey TOTEM_TIER;
    /** Cost value stored on gem (used in sealing cost calculation) */
    public static NamespacedKey GEM_COST;
    /** Sealed marker on totem (BYTE, 0 or 1) */
    public static NamespacedKey TOTEM_SEALED;
    /** Calculated sealing cost stored on totem after sealing */
    public static NamespacedKey TOTEM_COST;
    /** Forge count stored on sealed totem (increments with each forge, decrements on resurrect) */
    public static NamespacedKey TOTEM_FORGE_COUNT;
    /** Shooter UUID stored on projectile entities (for potion affix resolution on hit) */
    public static NamespacedKey SHOOTER_UUID;

    private ItemKeys() {}

    public static void init(Plugin plugin) {
        TIER = new NamespacedKey(plugin, "tier");
        EFFECTS = new NamespacedKey(plugin, "effects");
        LIBRARIAN = new NamespacedKey(plugin, "librarian");
        LIBRARIAN_FRAME = new NamespacedKey(plugin, "librarian_frame");
        GEM_ATTRS = new NamespacedKey(plugin, "gem_attrs");
        GEM_SLOTS = new NamespacedKey(plugin, "gem_slots");
        TOTEM_GEMS = new NamespacedKey(plugin, "totem_gems");
        GEM_CAPACITY = new NamespacedKey(plugin, "gem_capacity");
        TOTEM_CAPACITY = new NamespacedKey(plugin, "totem_capacity");
        TOTEM_TIER = new NamespacedKey(plugin, "totem_tier");
        GEM_COST = new NamespacedKey(plugin, "gem_cost");
        TOTEM_SEALED = new NamespacedKey(plugin, "totem_sealed");
        TOTEM_COST = new NamespacedKey(plugin, "totem_cost");
        TOTEM_FORGE_COUNT = new NamespacedKey(plugin, "totem_forge_count");
        SHOOTER_UUID = new NamespacedKey(plugin, "shooter_uuid");
    }
}
