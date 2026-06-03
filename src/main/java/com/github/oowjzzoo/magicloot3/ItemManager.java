package com.github.oowjzzoo.magicloot3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

/**
 * Core loot item generation. Full implementation in a later commit.
 * This skeleton provides the static fields referenced by MagicLootConfig.
 */
public class ItemManager {

    public static List<Enchantment> ENCHANTMENTS = new ArrayList<>();
    public static List<PotionEffectType> POTIONEFFECTS = new ArrayList<>();
    public static List<String> PREFIX = new ArrayList<>();
    public static List<String> SUFFIX = new ArrayList<>();
    public static List<String> COLOR = new ArrayList<>();
    public static List<String> EFFECTS = new ArrayList<>();
    public static List<Material> TOOLS = new ArrayList<>();
    public static List<Material> TREASURE = new ArrayList<>();
    public static List<ItemStack> SLIMEFUN = new ArrayList<>();
    public static List<LootType> types = new ArrayList<>();
    public static Map<String, PotionEffectType> potion = new HashMap<>();
}
