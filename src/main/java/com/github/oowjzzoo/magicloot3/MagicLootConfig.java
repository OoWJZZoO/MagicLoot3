package com.github.oowjzzoo.magicloot3;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
    private static ConfigManager configNames;
    private static ConfigManager configEnch;
    private static ConfigManager configPotions;
    private static ConfigManager configEffects;
    private static ConfigManager configTiers;

    public static ConfigManager getConfig(ConfigType type) {
        switch (type) {
            case EFFECTS: return configEffects;
            case ENCHANTMENTS: return configEnch;
            case ITEMS: return configItems;
            case NAMES: return configNames;
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
        configItems = new ConfigManager(new File(dataFolder, "Items.yml"));
        configNames = new ConfigManager(new File(dataFolder, "Names.yml"));
        configEnch = new ConfigManager(new File(dataFolder, "Enchantments.yml"));
        configPotions = new ConfigManager(new File(dataFolder, "Potions.yml"));
        configEffects = new ConfigManager(new File(dataFolder, "Effects.yml"));
        configTiers = new ConfigManager(new File(dataFolder, "loot_tiers.yml"));

        configItems.setDefaultValue("treasure.DIAMOND", true);
        configItems.setDefaultValue("treasure.GOLD_INGOT", true);
        configItems.setDefaultValue("treasure.IRON_INGOT", true);
        configItems.setDefaultValue("treasure.EMERALD", true);
        configItems.setDefaultValue("treasure.QUARTZ", true);

        for (LootTier tier : LootTier.values()) {
            configTiers.setDefaultValue(tier.toString() + ".enchantments.min", 1 + (tier.getLevel() / 2));
            configTiers.setDefaultValue(tier.toString() + ".enchantments.max", 1 + tier.getLevel());
            configTiers.setDefaultValue(tier.toString() + ".effects.min", tier.getLevel() / 2);
            configTiers.setDefaultValue(tier.toString() + ".effects.max", tier.getLevel());
        }

        for (Material m : Material.values()) {
            if (!m.isBlock()) {
                for (Enchantment e : Enchantment.values()) {
                    if (e.canEnchantItem(new ItemStack(m)) && !m.toString().contains("BOOK")) {
                        configItems.setDefaultValue("loot." + m.toString(), true);
                    }
                }
            }
        }

        if (Slimefun.instance() != null) {
            for (SlimefunItem item : Slimefun.getRegistry().getAllSlimefunItems()) {
                configItems.setDefaultValue("Slimefun-Item." + item.getId(), true);
            }
        }

        for (Enchantment e : Enchantment.values()) {
            configEnch.setDefaultValue(e.getKey().getKey() + ".max-level", 10);
        }

        configNames.setDefaultValue("prefixes", prefixes);
        configNames.setDefaultValue("suffixes", suffixes);
        configNames.setDefaultValue("colors", colors);

        for (org.bukkit.potion.PotionEffectType e : org.bukkit.potion.PotionEffectType.values()) {
            if (e != null) {
                configPotions.setDefaultValue(e.getKey().getKey() + ".max-level", 5);
                configEffects.setDefaultValue(formatEffectName(e.getKey().getKey()) + ".max-level", 10);
            }
        }

        if (!new File(dataFolder, "schematics").exists()) {
            new File(dataFolder, "schematics").mkdir();
            loadRuin("GasStation");
            loadRuin("House");
            loadRuin("Outpost");
            loadRuin("Farm");
            loadRuin("Railstation");
            loadRuin("Shop");
        }

        if (!new File(dataFolder, "buildings").exists()) {
            new File(dataFolder, "buildings").mkdir();
        }
        loadBuilding("Lost_Library");

        ConfigManager cfg = new ConfigManager(new File(dataFolder, "config.yml"));
        for (String mob : cfg.getStringList("spawners")) {
            mobs.add(EntityType.valueOf(mob));
        }

        for (ConfigType type : ConfigType.values()) {
            getConfig(type).save();
        }
    }

    public static void loadSettings() {
        for (Enchantment e : Enchantment.values()) {
            if (getMaxLevel(e) > 0) ItemManager.ENCHANTMENTS.add(e);
        }

        for (org.bukkit.potion.PotionEffectType e : org.bukkit.potion.PotionEffectType.values()) {
            if (e != null) {
                if (getMaxLevel(e) > 0) {
                    ItemManager.POTIONEFFECTS.add(e);
                    ItemManager.potion.put(formatEffectName(e.getKey().getKey()), e);
                }
            }
        }

        ItemManager.PREFIX = getConfig(ConfigType.NAMES).getStringList("prefixes");
        ItemManager.SUFFIX = getConfig(ConfigType.NAMES).getStringList("suffixes");
        ItemManager.COLOR = getConfig(ConfigType.NAMES).getStringList("colors");

        for (org.bukkit.potion.PotionEffectType e : org.bukkit.potion.PotionEffectType.values()) {
            if (e != null) {
                if (getMaxLevel(formatEffectName(e.getKey().getKey())) > 0) {
                    ItemManager.EFFECTS.add(formatEffectName(e.getKey().getKey()));
                }
            }
        }

        for (Material m : Material.values()) {
            if (getConfig(ConfigType.ITEMS).contains("treasure." + m.toString())) {
                if (getConfig(ConfigType.ITEMS).getBoolean("treasure." + m.toString())) {
                    ItemManager.TREASURE.add(m);
                }
            }
            if (getConfig(ConfigType.ITEMS).contains("loot." + m.toString())) {
                if (getConfig(ConfigType.ITEMS).getBoolean("loot." + m.toString())) {
                    ItemManager.TOOLS.add(m);
                }
            }
        }

        if (Slimefun.instance() != null) {
            for (SlimefunItem item : Slimefun.getRegistry().getAllSlimefunItems()) {
                String key = "Slimefun-Item." + item.getId();
                if (getConfig(ConfigType.ITEMS).contains(key)
                        && getConfig(ConfigType.ITEMS).getBoolean(key)) {
                    ItemManager.SLIMEFUN.add(item.getItem());
                }
            }
        }

        ConfigManager cfg = new ConfigManager(new File(dataFolder, "config.yml"));
        for (LootType type : LootType.values()) {
            if (cfg.contains("enable." + type.toString())) {
                if (cfg.getBoolean("enable." + type.toString())) ItemManager.types.add(type);
            }
        }
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

    public static boolean isSlimefunItemEnabled(String item) {
        return getConfig(ConfigType.ITEMS).getBoolean("Slimefun-Item." + item);
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

    // Resource loading

    public static void loadRuin(String name) {
        InputStream stream = MagicLootConfig.class.getResourceAsStream("/schematics/" + name + ".schematic");
        if (stream == null) return;
        OutputStream out = null;
        byte[] buffer = new byte[4096];
        try {
            out = new FileOutputStream(new File(dataFolder, "schematics/" + name + ".schematic"));
            int read;
            while ((read = stream.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { stream.close(); } catch (IOException ignored) {}
            if (out != null) try { out.close(); } catch (IOException ignored) {}
        }
    }

    private static void loadBuilding(String name) {
        InputStream stream = MagicLootConfig.class.getResourceAsStream("/buildings/" + name + ".schematic");
        if (stream == null) return;
        OutputStream out = null;
        byte[] buffer = new byte[4096];
        try {
            out = new FileOutputStream(new File(dataFolder, "buildings/" + name + ".schematic"));
            int read;
            while ((read = stream.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { stream.close(); } catch (IOException ignored) {}
            if (out != null) try { out.close(); } catch (IOException ignored) {}
        }
    }

    private static void loadNames() {
        prefixes.add("Precious"); prefixes.add("Mighty"); prefixes.add("Fabulous");
        prefixes.add("Devil's"); prefixes.add("Herobrine's"); prefixes.add("Hellish");
        prefixes.add("Enchanted"); prefixes.add("Magical"); prefixes.add("Strange");
        prefixes.add("Fancy"); prefixes.add("Emerald"); prefixes.add("Ruby");
        prefixes.add("Sapphire"); prefixes.add("Golden"); prefixes.add("Asian");
        prefixes.add("Enhanced"); prefixes.add("Advanced"); prefixes.add("Awkward");
        prefixes.add("Pointless"); prefixes.add("Brave"); prefixes.add("Awesome");
        prefixes.add("Holy"); prefixes.add("Unholy"); prefixes.add("Hallowed");
        prefixes.add("Dark"); prefixes.add("Timelord"); prefixes.add("Master");
        prefixes.add("Gallifreyan"); prefixes.add("Helpful"); prefixes.add("Trusty");
        prefixes.add("Faithful"); prefixes.add("Mysterious"); prefixes.add("Legendary");
        prefixes.add("Amazing"); prefixes.add("Old"); prefixes.add("Unbelievable");
        prefixes.add("Godly"); prefixes.add("Frozen"); prefixes.add("Awakened");
        prefixes.add("Deadly"); prefixes.add("Cursed"); prefixes.add("Elemental");
        prefixes.add("Sharp"); prefixes.add("Travelling"); prefixes.add("Doomed");
        prefixes.add("Ghostly"); prefixes.add("Dirty"); prefixes.add("Faithful");
        prefixes.add("Bad"); prefixes.add("Great"); prefixes.add("Crying");

        suffixes.add("Tool"); suffixes.add("Wizard"); suffixes.add("Magician");
        suffixes.add("Kindness"); suffixes.add("Spirit"); suffixes.add("Darkness");
        suffixes.add("Lion"); suffixes.add("King"); suffixes.add("Dragon");
        suffixes.add("Heaven"); suffixes.add("Swiftness"); suffixes.add("Tool");
        suffixes.add("Absorption"); suffixes.add("Spell"); suffixes.add("Lump");
        suffixes.add("Glory"); suffixes.add("Demon"); suffixes.add("Fury");
        suffixes.add("Challenge"); suffixes.add("Wolf"); suffixes.add("Ghost");
        suffixes.add("Fire"); suffixes.add("Night"); suffixes.add("Day");
        suffixes.add("Rose"); suffixes.add("Crime"); suffixes.add("Cry");
        suffixes.add("Screwdriver"); suffixes.add("Intelligence"); suffixes.add("Madness");
        suffixes.add("Skill"); suffixes.add("Skull"); suffixes.add("Sun");
        suffixes.add("Monster"); suffixes.add("Treasure");

        colors.add("&9"); colors.add("&a"); colors.add("&6");
        colors.add("&c"); colors.add("&b"); colors.add("&e");
    }
}
