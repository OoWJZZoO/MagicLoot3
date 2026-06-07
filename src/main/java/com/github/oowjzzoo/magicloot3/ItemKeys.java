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

    private ItemKeys() {}

    public static void init(Plugin plugin) {
        TIER = new NamespacedKey(plugin, "tier");
        EFFECTS = new NamespacedKey(plugin, "effects");
        LIBRARIAN = new NamespacedKey(plugin, "librarian");
        LIBRARIAN_FRAME = new NamespacedKey(plugin, "librarian_frame");
    }
}
