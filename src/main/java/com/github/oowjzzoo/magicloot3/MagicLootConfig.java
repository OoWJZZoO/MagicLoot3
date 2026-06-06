package com.github.oowjzzoo.magicloot3;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
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
    private static ConfigManager configEffects;
    private static ConfigManager configTiers;

    public static ConfigManager getConfig(ConfigType type) {
        switch (type) {
            case EFFECTS: return configEffects;
            case ENCHANTMENTS: return configEnch;
            case ITEMS: return configItems;
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
        configEffects = new ConfigManager(new File(dataFolder, "Effects.yml"));
        configTiers = new ConfigManager(new File(dataFolder, "loot_tiers.yml"));

        // Tools pool: any enchantable non-block material
        for (Material m : Material.values()) {
            if (m.isBlock()) continue;
            for (Enchantment e : Enchantment.values()) {
                if (e.isCursed()) continue; // mirror SF EnchantmentRune
                if (e.canEnchantItem(new ItemStack(m)) && !m.toString().contains("BOOK")) {
                    configItems.setDefaultValue("tools." + m.toString(), 100);
                    break;
                }
            }
        }
        // Treasure pool: default items
        for (String t : new String[]{"DIAMOND", "GOLD_INGOT", "IRON_INGOT", "EMERALD", "QUARTZ"}) {
            configItems.setDefaultValue("treasure." + t, 100);
        }
        writeSfDefaults(configItems);

        for (LootTier tier : LootTier.values()) {
            if (tier == LootTier.NONE || tier == LootTier.UNKNOWN) continue;
            configTiers.setDefaultValue(tier.toString() + ".weight", 1);
            configTiers.setDefaultValue(tier.toString() + ".applicable-weight", 1);
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
                configEffects.setDefaultValue(e.getKey().getKey() + ".max-level", 5);
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
        ItemManager.enchantments.clear();
        ItemManager.potionEffectTypes.clear();
        ItemManager.potionEffectMap.clear();
        ItemManager.enabledLootTypes.clear();

        for (Enchantment e : Enchantment.values()) {
            if (getMaxLevel(e) > 0) ItemManager.enchantments.add(e);
        }

        ItemManager.effectNames.clear();
        for (org.bukkit.potion.PotionEffectType e : org.bukkit.potion.PotionEffectType.values()) {
            if (e != null && getMaxLevel(e) > 0) {
                ItemManager.potionEffectTypes.add(e);
                ItemManager.potionEffectMap.put(e.getKey().getKey(), e);
                String enKey = e.getKey().getKey();
                String display = Messages.raw("effects." + enKey);
                ItemManager.effectNames.put(enKey,
                        display != null ? org.bukkit.ChatColor.translateAlternateColorCodes('&', display)
                                : formatEffectName(enKey));
            }
        }

        ItemManager.prefixes = prefixes;
        ItemManager.suffixes = suffixes;
        ItemManager.colorCodes = colors;

        ItemManager.effectDisplayNames.clear();
        ItemManager.effectDisplayNames.addAll(ItemManager.effectNames.values());

        // Read config sections (used by weighted pool building below)
        var toolsSec = getConfig(ConfigType.ITEMS).getYaml()
                .getConfigurationSection("tools");
        var treasSec = getConfig(ConfigType.ITEMS).getYaml()
                .getConfigurationSection("treasure");
        var sfSec = getConfig(ConfigType.ITEMS).getYaml()
                .getConfigurationSection("slimefun");

        ConfigManager cfg = new ConfigManager(new File(dataFolder, "config.yml"));
        for (LootType type : LootType.values()) {
            if (cfg.contains("enable." + type.toString())) {
                if (cfg.getBoolean("enable." + type.toString())) ItemManager.enabledLootTypes.add(type);
            }
        }

        LootTier.loadWeights();

        // Build weighted item pools with cumulative weights for O(log N) selection
        ItemManager.weightedTools.clear();
        ItemManager.weightedToolsCum = buildMaterialCum(ItemManager.weightedTools, toolsSec);
        ItemManager.weightedToolsTotal = ItemManager.weightedToolsCum.length > 0
                ? ItemManager.weightedToolsCum[ItemManager.weightedToolsCum.length - 1] : 0;

        ItemManager.weightedTreasure.clear();
        ItemManager.weightedTreasureCum = buildMaterialCum(ItemManager.weightedTreasure, treasSec);
        ItemManager.weightedTreasureTotal = ItemManager.weightedTreasureCum.length > 0
                ? ItemManager.weightedTreasureCum[ItemManager.weightedTreasureCum.length - 1] : 0;

        ItemManager.weightedSlimefun.clear();
        List<ItemStack> sfItems = new ArrayList<>();
        List<Integer> sfWeights = new ArrayList<>();
        if (sfSec != null && Bukkit.getPluginManager().isPluginEnabled("Slimefun")) {
            for (String key : sfSec.getKeys(false)) {
                int w = sfSec.getInt(key, 0);
                if (w <= 0) continue;
                SlimefunItem sfItem = SlimefunItem.getById(key);
                if (sfItem instanceof MultiBlockMachine) {
                    configItems.getYaml().set("slimefun." + key, -1);
                    configItems.save();
                } else if (sfItem != null) {
                    sfItems.add(sfItem.getItem());
                    sfWeights.add(w);
                }
            }
        }
        ItemManager.weightedSlimefun.addAll(sfItems);
        int sfTotal = 0;
        int[] sfCum = new int[sfWeights.size()];
        for (int i = 0; i < sfWeights.size(); i++) {
            sfTotal += sfWeights.get(i);
            sfCum[i] = sfTotal;
        }
        ItemManager.weightedSlimefunCum = sfCum;
        ItemManager.weightedSlimefunTotal = sfTotal;
    }

    // Getters for max levels (used by ItemManager)

    public static int getMaxLevel(Enchantment e) {
        return getConfig(ConfigType.ENCHANTMENTS).getInt(e.getKey().getKey() + ".max-level");
    }

    public static int getMaxLevel(org.bukkit.potion.PotionEffectType e) {
        return getConfig(ConfigType.EFFECTS).getInt(e.getKey().getKey() + ".max-level");
    }

    /**
     * Write default weight 100 to Items.yml for any Slimefun items not yet present.
     * Called from ServerLoadEvent to catch addons that loaded after our onEnable.
     */
    public static void ensureDefaults(JavaPlugin plugin) {
        ConfigManager cfg = getConfig(ConfigType.ITEMS);
        if (cfg == null) return;
        int prev = cfg.getYaml().getConfigurationSection("slimefun") != null
                ? cfg.getYaml().getConfigurationSection("slimefun").getKeys(false).size() : 0;
        writeSfDefaults(cfg);
        cfg.save();
        int after = cfg.getYaml().getConfigurationSection("slimefun").getKeys(false).size();
        plugin.getLogger().info("[LootDefaults] total=" + after + " existed=" + prev
                + " added=" + (after - prev));
    }

    private static void writeSfDefaults(ConfigManager cfg) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Slimefun")) return;
        for (SlimefunItem item : Slimefun.getRegistry().getAllSlimefunItems()) {
            cfg.setDefaultValue("slimefun." + item.getId(), 100);
        }
    }

    private static void saveDefaultConfig(String name) {
        File dest = new File(dataFolder, name);
        if (!dest.exists()) {
            try (InputStream in = MagicLootConfig.class.getResourceAsStream("/" + name)) {
                if (in != null) java.nio.file.Files.copy(in, dest.toPath());
            } catch (IOException ignored) {}
        }
    }

    /**
     * Build cumulative weight array from a config section for Material-based items.
     * Each material with weight > 0 is added to {@code items}, and the cumulative
     * weight per item is returned. Last entry = total weight.
     */
    private static int[] buildMaterialCum(List<Material> items,
                                           org.bukkit.configuration.ConfigurationSection sec) {
        if (sec == null) return new int[0];
        List<Integer> cumList = new ArrayList<>();
        int total = 0;
        for (String key : sec.getKeys(false)) {
            int w = sec.getInt(key, 0);
            if (w <= 0) continue;
            try {
                Material m = Material.valueOf(key);
                items.add(m);
                total += w;
                cumList.add(total);
            } catch (IllegalArgumentException ignored) {}
        }
        return cumList.stream().mapToInt(i -> i).toArray();
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
