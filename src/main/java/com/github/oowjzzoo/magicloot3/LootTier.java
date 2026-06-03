package com.github.oowjzzoo.magicloot3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public enum LootTier {

    NONE(0, 0, 0),
    UNKNOWN(0, 0, 0),

    COMMON(0, 13, 5),
    UNCOMMON(1, 10, 2),
    RARE(2, 9, 11),
    EPIC(3, 4, 0),
    LEGENDARY(4, 1, 4);

    private int level;
    private int color1, color2;

    LootTier(int level, int color1, int color2) {
        this.level = level;
        this.color1 = color1;
        this.color2 = color2;
    }

    public int getLevel() { return level; }

    public String getTag() {
        return Messages.get("tiers." + this.name());
    }

    public int getPrimaryColor() { return color1; }
    public int getSecondaryColor() { return color2; }

    private static final List<LootTier> tiers = new ArrayList<>();
    private static final List<LootTier> applicable = new ArrayList<>();

    static {
        for (int i = 0; i < 11; i++) tiers.add(LootTier.COMMON);
        for (int i = 0; i < 7; i++) tiers.add(LootTier.UNCOMMON);
        for (int i = 0; i < 4; i++) tiers.add(LootTier.RARE);
        for (int i = 0; i < 3; i++) tiers.add(LootTier.EPIC);
        tiers.add(LootTier.LEGENDARY);

        for (int i = 0; i < 3; i++) applicable.add(LootTier.COMMON);
        for (int i = 0; i < 3; i++) applicable.add(LootTier.UNCOMMON);
        for (int i = 0; i < 2; i++) applicable.add(LootTier.RARE);
        for (int i = 0; i < 2; i++) applicable.add(LootTier.EPIC);
        applicable.add(LootTier.LEGENDARY);
    }

    public static LootTier getRandom() {
        return tiers.get(ThreadLocalRandom.current().nextInt(tiers.size()));
    }

    /**
     * Reads tier from PersistentDataContainer. Language-independent.
     */
    public static LootTier get(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return LootTier.NONE;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return LootTier.NONE;
        String tag = meta.getPersistentDataContainer().get(ItemKeys.TIER, PersistentDataType.STRING);
        if (tag == null) return LootTier.NONE;
        try {
            return LootTier.valueOf(tag);
        } catch (IllegalArgumentException e) {
            return LootTier.NONE;
        }
    }

    public static LootTier getRandomApplicable() {
        return applicable.get(ThreadLocalRandom.current().nextInt(applicable.size()));
    }
}
