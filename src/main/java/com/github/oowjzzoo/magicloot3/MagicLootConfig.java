package com.github.oowjzzoo.magicloot3;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

public class MagicLootConfig {

    public static final List<String> prefixes = new ArrayList<>();
    public static final List<String> suffixes = new ArrayList<>();
    public static final List<String> colors = new ArrayList<>();
    public static final List<String> effects = new ArrayList<>();
    public static final List<EntityType> mobs = new ArrayList<>();

    private static File dataFolder;
    private static ConfigManager configItems;
    private static ConfigManager configEnch;
    private static ConfigManager configPotions;
    private static ConfigManager configEffects;
    private static ConfigManager configTiers;

    public static ConfigManager getConfig(ConfigType type) {
        switch (type) {
            case EFFECTS: return configEffects;
            case ENCHANTMENTS: return configEnch;
            case ITEMS: return configItems;
            case POTIONS: return configPotions;
            case LOOT_TIER: return configTiers;
            default: return null;
        }
    }

    public static File getDataFolder() {
        return dataFolder;
    }

    public static void setupConfigs(JavaPlugin plugin) {
        loadNames();

        dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();

        // Save defaults from jar (only if file doesn't exist on disk)
        saveDefaultConfig("Items.yml");
        saveDefaultConfig("Enchantments.yml");
        saveDefaultConfig("Potions.yml");
        saveDefaultConfig("Effects.yml");
        saveDefaultConfig("loot_tiers.yml");

        configItems = new ConfigManager(new File(dataFolder, "Items.yml"));
        configItems.setHeader("""
                物品权重配置
                - 三个池子（tools / treasure / slimefun）完全独立
                - 权重越大，被抽中的概率越高，设为 0 即禁用
                - 修改后 /magicloot reload 生效
                """);
        configEnch = new ConfigManager(new File(dataFolder, "Enchantments.yml"));
        configPotions = new ConfigManager(new File(dataFolder, "Potions.yml"));
        configEffects = new ConfigManager(new File(dataFolder, "Effects.yml"));
        configTiers = new ConfigManager(new File(dataFolder, "loot_tiers.yml"));

        // Tools pool: any enchantable non-block material
        for (Material m : Material.values()) {
            if (m.isBlock()) continue;
            for (Enchantment e : Enchantment.values()) {
                if (e.canEnchantItem(new ItemStack(m)) && !m.toString().contains("BOOK")) {
                    configItems.setDefaultValue("tools." + m.toString(), 10);
                    break;
                }
            }
        }
        // Disable non-weapon items in tools pool
        for (String t : new String[]{"FLINT_AND_STEEL", "SHEARS", "FISHING_ROD",
                "CARROT_ON_A_STICK", "WARPED_FUNGUS_ON_A_STICK", "COMPASS",
                "CLOCK", "NAME_TAG", "LEAD", "SADDLE", "SHIELD", "BOW",
                "CROSSBOW", "TRIDENT", "ELYTRA"}) {
            configItems.setDefaultValue("tools." + t, 0);
        }
        // Treasure pool: default items
        for (String t : new String[]{"DIAMOND", "GOLD_INGOT", "IRON_INGOT", "EMERALD", "QUARTZ"}) {
            configItems.setDefaultValue("treasure." + t, 10);
        }
        // Slimefun pool
        if (Slimefun.instance() != null) {
            for (SlimefunItem item : Slimefun.getRegistry().getAllSlimefunItems()) {
                configItems.setDefaultValue("slimefun." + item.getId(), 10);
            }
        }

        for (LootTier tier : LootTier.values()) {
            if (tier == LootTier.NONE || tier == LootTier.UNKNOWN) continue;
            configTiers.setDefaultValue(tier.toString() + ".weight", getDefaultWeight(tier));
            configTiers.setDefaultValue(tier.toString() + ".applicable-weight", getDefaultAppWeight(tier));
            configTiers.setDefaultValue(tier.toString() + ".enchantments.min", 1 + (tier.getLevel() / 2));
            configTiers.setDefaultValue(tier.toString() + ".enchantments.max", 1 + tier.getLevel());
            configTiers.setDefaultValue(tier.toString() + ".effects.min", tier.getLevel() / 2);
            configTiers.setDefaultValue(tier.toString() + ".effects.max", tier.getLevel());
        }

        for (Enchantment e : Enchantment.values()) {
            configEnch.setDefaultValue(e.getKey().getKey() + ".max-level", 10);
        }

        for (org.bukkit.potion.PotionEffectType e : org.bukkit.potion.PotionEffectType.values()) {
            if (e != null) {
                configPotions.setDefaultValue(e.getKey().getKey() + ".max-level", 5);
                configEffects.setDefaultValue(formatEffectName(e.getKey().getKey()) + ".max-level", 10);
            }
        }

        ConfigManager cfg = new ConfigManager(new File(dataFolder, "config.yml"));
        for (String mob : cfg.getStringList("spawners")) {
            // Compatibility: PIG_ZOMBIE was renamed to ZOMBIFIED_PIGLIN in 1.16
            if ("PIG_ZOMBIE".equals(mob)) mob = "ZOMBIFIED_PIGLIN";
            try {
                mobs.add(EntityType.valueOf(mob));
            } catch (IllegalArgumentException ignored) {}
        }

        for (ConfigType type : ConfigType.values()) {
            getConfig(type).save();
        }
    }

    public static void loadSettings() {
        ItemManager.ENCHANTMENTS.clear();
        ItemManager.POTIONEFFECTS.clear();
        ItemManager.potion.clear();
        ItemManager.TOOLS.clear();
        ItemManager.TREASURE.clear();
        ItemManager.SLIMEFUN.clear();
        ItemManager.types.clear();

        for (Enchantment e : Enchantment.values()) {
            if (getMaxLevel(e) > 0) ItemManager.ENCHANTMENTS.add(e);
        }

        for (org.bukkit.potion.PotionEffectType e : org.bukkit.potion.PotionEffectType.values()) {
            if (e != null) {
                if (getMaxLevel(e) > 0) {
                    ItemManager.POTIONEFFECTS.add(e);
                    ItemManager.potion.put(e.getKey().getKey(), e);
                }
            }
        }

        // Rebuild EFFNAME map from language file for display
        ItemManager.effectNames.clear();
        for (org.bukkit.potion.PotionEffectType e : org.bukkit.potion.PotionEffectType.values()) {
            if (e != null) {
                String enKey = e.getKey().getKey();
                String enName = formatEffectName(enKey);
                if (getMaxLevel(enName) > 0) {
                    ItemManager.effectNames.put(enKey,
                            Messages.get("effects." + enKey, enName));
                }
            }
        }

        ItemManager.PREFIX = prefixes;
        ItemManager.SUFFIX = suffixes;
        ItemManager.COLOR = colors;

        ItemManager.EFFECTS.clear();
        ItemManager.EFFECTS.addAll(ItemManager.effectNames.values());

        // Tools pool — explicit config section
        var toolsSec = getConfig(ConfigType.ITEMS).getYaml()
                .getConfigurationSection("tools");
        if (toolsSec != null) {
            for (String key : toolsSec.getKeys(false)) {
                int w = toolsSec.getInt(key, 0);
                if (w <= 0) continue;
                try { ItemManager.TOOLS.add(Material.valueOf(key)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        // Treasure pool
        var treasSec = getConfig(ConfigType.ITEMS).getYaml()
                .getConfigurationSection("treasure");
        if (treasSec != null) {
            for (String key : treasSec.getKeys(false)) {
                int w = treasSec.getInt(key, 0);
                if (w <= 0) continue;
                try { ItemManager.TREASURE.add(Material.valueOf(key)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        // Slimefun pool
        var sfSec = getConfig(ConfigType.ITEMS).getYaml()
                .getConfigurationSection("slimefun");
        if (sfSec != null && Slimefun.instance() != null) {
            for (String key : sfSec.getKeys(false)) {
                int w = sfSec.getInt(key, 0);
                if (w <= 0) continue;
                SlimefunItem sfItem = SlimefunItem.getById(key);
                if (sfItem != null) ItemManager.SLIMEFUN.add(sfItem.getItem());
            }
        }

        ConfigManager cfg = new ConfigManager(new File(dataFolder, "config.yml"));
        for (LootType type : LootType.values()) {
            if (cfg.contains("enable." + type.toString())) {
                if (cfg.getBoolean("enable." + type.toString())) ItemManager.types.add(type);
            }
        }

        LootTier.loadWeights();

        // Build weighted item pools from config sections
        ItemManager.weightedTools.clear();
        if (toolsSec != null) {
            for (String key : toolsSec.getKeys(false)) {
                int w = toolsSec.getInt(key, 0);
                if (w <= 0) continue;
                try {
                    Material m = Material.valueOf(key);
                    for (int i = 0; i < w; i++) ItemManager.weightedTools.add(m);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        ItemManager.weightedTreasure.clear();
        if (treasSec != null) {
            for (String key : treasSec.getKeys(false)) {
                int w = treasSec.getInt(key, 0);
                if (w <= 0) continue;
                try {
                    Material m = Material.valueOf(key);
                    for (int i = 0; i < w; i++) ItemManager.weightedTreasure.add(m);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        ItemManager.weightedSlimefun.clear();
        if (sfSec != null) {
            for (String key : sfSec.getKeys(false)) {
                int w = sfSec.getInt(key, 0);
                if (w <= 0) continue;
                SlimefunItem sfItem = SlimefunItem.getById(key);
                if (sfItem != null) {
                    ItemStack item = sfItem.getItem();
                    for (int i = 0; i < w; i++) ItemManager.weightedSlimefun.add(item);
                }
            }
        }
    }

    private static int getDefaultWeight(LootTier tier) {
        return switch (tier) {
            case COMMON -> 11;
            case UNCOMMON -> 7;
            case RARE -> 4;
            case EPIC -> 3;
            case LEGENDARY -> 1;
            default -> 0;
        };
    }

    private static int getDefaultAppWeight(LootTier tier) {
        return switch (tier) {
            case COMMON, UNCOMMON -> 3;
            case RARE, EPIC -> 2;
            case LEGENDARY -> 1;
            default -> 0;
        };
    }

    // Getters for max levels (used by ItemManager)

    public static int getMaxLevel(Enchantment e) {
        return getConfig(ConfigType.ENCHANTMENTS).getInt(e.getKey().getKey() + ".max-level");
    }

    public static int getMaxLevel(org.bukkit.potion.PotionEffectType e) {
        return getConfig(ConfigType.POTIONS).getInt(e.getKey().getKey() + ".max-level");
    }

    public static int getMaxLevel(String e) {
        return getConfig(ConfigType.EFFECTS).getInt(e + ".max-level");
    }

    private static void saveDefaultConfig(String name) {
        File dest = new File(dataFolder, name);
        if (!dest.exists()) {
            try (InputStream in = MagicLootConfig.class.getResourceAsStream("/" + name)) {
                if (in != null) java.nio.file.Files.copy(in, dest.toPath());
            } catch (IOException ignored) {}
        }
    }

    // Helper: format effect name for display (e.g. "fire_resistance" -> "Fire Resistance")

    public static String formatEffectName(String key) {
        StringBuilder sb = new StringBuilder();
        for (String part : key.split("_")) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private static void loadNames() {
        prefixes.clear();
        suffixes.clear();
        colors.clear();
        prefixes.addAll(Messages.getList("prefixes"));
        suffixes.addAll(Messages.getList("suffixes"));
        colors.addAll(Messages.getList("colors"));
    }
}
