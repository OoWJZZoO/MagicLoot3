package com.github.oowjzzoo.magicloot3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;

public enum LootTier {

    NONE("§4ERROR", 0, 0, 0),
    UNKNOWN("§cUnknown", 0, 0, 0),

    COMMON("§aCommon", 0, 13, 5),
    UNCOMMON("§5Uncommon", 1, 10, 2),
    RARE("§9Rare", 2, 9, 11),
    EPIC("§e§lEpic", 3, 4, 0),
    LEGENDARY("§6§lLEGENDARY", 4, 1, 4);

    private String tag;
    private int level;
    private int color1, color2;

    LootTier(String tag, int level, int color1, int color2) {
        this.tag = tag;
        this.level = level;
        this.color1 = color1;
        this.color2 = color2;
    }

    public int getLevel() {
        return level;
    }

    public String getTag() {
        return tag;
    }

    public int getPrimaryColor() {
        return color1;
    }

    public int getSecondaryColor() {
        return color2;
    }

    private static final List<LootTier> tiers = new ArrayList<>();
    private static final List<LootTier> applicable = new ArrayList<>();

    static {
        // Weighted random pools
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

    public static LootTier get(ItemStack item) {
        if (item == null) return LootTier.NONE;
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) return LootTier.NONE;
        for (String line : item.getItemMeta().getLore()) {
            if (line.contains("§b§d§e")) {
                return LootTier.valueOf(ChatColor.stripColor(line.split("§b§d§e")[1]).toUpperCase());
            }
        }
        return LootTier.NONE;
    }

    public static LootTier getRandomApplicable() {
        return applicable.get(ThreadLocalRandom.current().nextInt(applicable.size()));
    }
}
