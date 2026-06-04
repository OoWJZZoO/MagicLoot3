package com.github.oowjzzoo.magicloot3;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import com.github.oowjzzoo.magicloot3.util.SkullCreator;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

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
    public static Map<String, String> effectNames = new HashMap<>();

    public static ItemStack createItem(LootType type) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (type == LootType.RANDOM && !types.isEmpty()) {
            type = types.get(random.nextInt(types.size()));
        }
        if (type == LootType.SLIMEFUN && Slimefun.instance() == null) {
            type = LootType.TREASURE;
        }

        ItemStack item = new ItemStack(Material.AIR);
        try {
            switch (type) {
                case BOOK -> {
                    item.setType(Material.ENCHANTED_BOOK);
                    item = applyTier(item, LootTier.getRandom());
                }
                case TREASURE -> {
                    if (!TREASURE.isEmpty()) {
                        item.setType(TREASURE.get(random.nextInt(TREASURE.size())));
                        item.setAmount(random.nextInt(8) + 2); // 2-9 items
                    }
                }
                case POTION -> {
                    boolean isSplash = random.nextBoolean();
                    item.setType(isSplash ? Material.SPLASH_POTION : Material.LINGERING_POTION);
                    String pPrefix = PREFIX.get(random.nextInt(PREFIX.size()));
                    String pSuffix = SUFFIX.get(random.nextInt(SUFFIX.size()));
                    String name = COLOR.get(random.nextInt(COLOR.size()))
                            + pPrefix + needsSpace(pPrefix, pSuffix) + pSuffix;
                    PotionMeta meta = (PotionMeta) item.getItemMeta();
                    meta.setBasePotionType(isSplash ? PotionType.AWKWARD : PotionType.AWKWARD);
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
                    for (int i = 0; i < random.nextInt(2) + 1; i++) {
                        if (!POTIONEFFECTS.isEmpty()) {
                            PotionEffectType e = POTIONEFFECTS.get(random.nextInt(POTIONEFFECTS.size()));
                            int maxLvl = MagicLootConfig.getMaxLevel(e);
                            meta.addCustomEffect(new PotionEffect(e, (random.nextInt(8) + 2) * 10 * 20,
                                    maxLvl == 1 ? 1 : random.nextInt(maxLvl - 1) + 1), true);
                        }
                    }
                    item.setItemMeta(meta);
                }
                case TOOL -> {
                    if (TOOLS.isEmpty()) break;
                    if (random.nextInt(100) < 10) {
                        item.setType(Material.ARROW);
                        item.setAmount(4 + random.nextInt(20));
                    } else {
                        item.setType(TOOLS.get(random.nextInt(TOOLS.size())));
                        if (item.getType().getMaxDurability() == 0) break;
                        item = applyTier(item, LootTier.getRandom());
                        applyDamage(item, (item.getType().getMaxDurability() / 4) * 3);
                    }
                }
                case SLIMEFUN -> {
                    if (!SLIMEFUN.isEmpty()) {
                        item = SLIMEFUN.get(random.nextInt(SLIMEFUN.size())).clone();
                        item.setAmount(random.nextInt(5) + 2); // 2-6 items
                        if (item.getType() != Material.PLAYER_HEAD
                                && item.getType().getMaxStackSize() < item.getAmount()) {
                            item.setAmount(item.getType().getMaxStackSize());
                        }
                    }
                }
                case UNANALIZED -> {
                    if (TOOLS.isEmpty()) break;
                    item.setType(TOOLS.get(random.nextInt(TOOLS.size())));
                    ItemMeta im = item.getItemMeta();
                    im.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&7&kMEH WANNA BE EXAMINED"));
                    List<String> lore = new ArrayList<>();
                    lore.add("");
                    lore.add(Messages.get("tier_lore_prefix") + Messages.get("tiers.UNKNOWN"));
                    im.setLore(lore);
                    im.getPersistentDataContainer().set(ItemKeys.TIER,
                            org.bukkit.persistence.PersistentDataType.STRING, "UNKNOWN");
                    item.setItemMeta(im);
                    if (item.getType().getMaxDurability() > 0) {
                        applyDamage(item, (item.getType().getMaxDurability() / 4) * 3);
                    }
                }
                default -> {}
            }
        } catch (Exception x) {
            x.printStackTrace();
        }
        return item;
    }

    public static ItemStack applyTier(ItemStack item, LootTier tier) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        String prefix = PREFIX.get(random.nextInt(PREFIX.size()));
        String suffix = SUFFIX.get(random.nextInt(SUFFIX.size()));
        String name = COLOR.get(random.nextInt(COLOR.size())) + prefix
                + needsSpace(prefix, suffix) + suffix;

        // Clear existing enchantments
        for (Enchantment e : item.getEnchantments().keySet()) {
            item.removeEnchantment(e);
        }

        ItemMeta im = item.getItemMeta();
        if (im == null) return item;

        im.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> lore = new ArrayList<>();
        List<String> effectData = new ArrayList<>();

        int effectsMax = MagicLootConfig.getConfig(ConfigType.LOOT_TIER).getInt(tier.toString() + ".effects.max");
        int effectsMin = MagicLootConfig.getConfig(ConfigType.LOOT_TIER).getInt(tier.toString() + ".effects.min");

        if (effectsMax > 0 && !POTIONEFFECTS.isEmpty()) {
            int range = Math.max(effectsMax - effectsMin, 1);
            for (int i = 0; i < random.nextInt(range) + effectsMin; i++) {
                PotionEffectType e = POTIONEFFECTS.get(random.nextInt(POTIONEFFECTS.size()));
                String enKey = e.getKey().getKey();
                int maxLvl = MagicLootConfig.getMaxLevel(e);
                if (maxLvl <= 0) continue;
                int level = maxLvl > 1 ? (random.nextInt(maxLvl - 1) + 1) : 1;
                String apply = random.nextInt(10) > 5 ? "+" : "-";
                effectData.add(enKey + ":" + apply + ":" + level);
                String displayName = ItemManager.effectNames.getOrDefault(enKey, enKey);
                lore.add(ChatColor.translateAlternateColorCodes('&',
                        COLOR.get(random.nextInt(COLOR.size())) + apply + " " + displayName + " " + level));
            }
        }

        lore.add("");
        lore.add(Messages.get("tier_lore_prefix") + tier.getTag());
        im.setLore(lore);

        // Write tier and effects to PDC (language-independent)
        im.getPersistentDataContainer().set(ItemKeys.TIER,
                org.bukkit.persistence.PersistentDataType.STRING, tier.name());
        if (!effectData.isEmpty()) {
            im.getPersistentDataContainer().set(ItemKeys.EFFECTS,
                    org.bukkit.persistence.PersistentDataType.STRING, String.join(",", effectData));
        }

        // Leather armor color
        if (im instanceof LeatherArmorMeta leatherMeta) {
            leatherMeta.setColor(Color.fromRGB(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
        }

        // Enchantment handling for books
        int enchMax = MagicLootConfig.getConfig(ConfigType.LOOT_TIER).getInt(tier.toString() + ".enchantments.max");
        int enchMin = MagicLootConfig.getConfig(ConfigType.LOOT_TIER).getInt(tier.toString() + ".enchantments.min");

        if (im instanceof EnchantmentStorageMeta storageMeta) {
            if (enchMax > 0 && !ENCHANTMENTS.isEmpty()) {
                int range = Math.max(enchMax - enchMin, 1);
                for (int i = 0; i < random.nextInt(range) + enchMin; i++) {
                    Enchantment e = ENCHANTMENTS.get(random.nextInt(ENCHANTMENTS.size()));
                    int maxLvl = MagicLootConfig.getMaxLevel(e);
                    if (maxLvl > 0) {
                        storageMeta.addStoredEnchant(e, maxLvl == 1 ? 1 : random.nextInt(maxLvl - 1) + 1, true);
                    }
                }
            }
        }
        item.setItemMeta(im);

        // Enchantments for non-book items
        if (enchMax > 0 && !ENCHANTMENTS.isEmpty()) {
            int range = Math.max(enchMax - enchMin, 1);
            for (int i = 0; i < random.nextInt(range) + enchMin; i++) {
                Enchantment e = ENCHANTMENTS.get(random.nextInt(ENCHANTMENTS.size()));
                int maxLvl = MagicLootConfig.getMaxLevel(e);
                if (maxLvl > 0) {
                    item.addUnsafeEnchantment(e, maxLvl == 1 ? 1 : random.nextInt(maxLvl - 1) + 1);
                }
            }
        }
        return item;
    }

    private static void applyDamage(ItemStack item, int damage) {
        if (item.getItemMeta() instanceof Damageable damageable) {
            damageable.setDamage(Math.min(damage, item.getType().getMaxDurability() - 1));
            item.setItemMeta(damageable);
        }
    }

    /** Returns " " if prefix ends with a letter and suffix starts with one, else "". */
    private static String needsSpace(String prefix, String suffix) {
        char last = prefix.charAt(prefix.length() - 1);
        char first = suffix.charAt(0);
        if (Character.isLetter(last) && Character.isLetter(first)) return " ";
        return "";
    }

    public static void fillChest(Block block) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (block.getState() instanceof Chest chest) {
            Inventory inv = chest.getInventory();
            for (int i = 0; i < random.nextInt(8) + 2; i++) {
                inv.setItem(random.nextInt(inv.getSize()), createItem(LootType.RANDOM));
            }
        }
    }

    @SuppressWarnings("deprecation")
    public static void equipEntity(Entity n) {
        if (!(n instanceof LivingEntity entity)) return;

        entity.getEquipment().setHelmet(null);
        entity.getEquipment().setChestplate(null);
        entity.getEquipment().setLeggings(null);
        entity.getEquipment().setBoots(null);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Calendar calendar = Calendar.getInstance();

        if (random.nextInt(100) < 30
                && calendar.get(Calendar.MONTH) == Calendar.DECEMBER
                && calendar.get(Calendar.DAY_OF_MONTH) < 26
                && calendar.get(Calendar.DAY_OF_MONTH) > 21) {
            // Christmas event
            entity.getEquipment().setHelmetDropChance(0.2F);
            entity.getEquipment().setChestplateDropChance(0F);
            entity.getEquipment().setLeggingsDropChance(0F);
            entity.getEquipment().setBootsDropChance(0F);
            entity.getEquipment().setItemInMainHandDropChance(0.7F);

            entity.getEquipment().setHelmet(SkullCreator.createSkull(
                    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2JjYmIzZTRhMzhhYzJhMDVmNjk1NWNkMmM5ODk1YWQ5ZjI4NGM2ZTgyZTc1NWM5NGM1NDljNWJkYzg1MyJ9fX0=",
                    Messages.get("santa_hat")));
            entity.getEquipment().setItemInMainHand(applyTier(createItem(LootType.TOOL), LootTier.LEGENDARY));
        } else {
            entity.getEquipment().setHelmetDropChance(0.7F);
            entity.getEquipment().setChestplateDropChance(0.7F);
            entity.getEquipment().setLeggingsDropChance(0.7F);
            entity.getEquipment().setBootsDropChance(0.7F);
            entity.getEquipment().setItemInMainHandDropChance(0.7F);

            for (int i = 0; i < random.nextInt(3); i++) {
                ItemStack item = createItem(LootType.TOOL);
                String typeName = item.getType().toString();
                if (typeName.endsWith("_HELMET") || typeName.endsWith("_SKULL")) {
                    entity.getEquipment().setHelmet(item);
                } else if (typeName.endsWith("_CHESTPLATE")) {
                    entity.getEquipment().setChestplate(item);
                } else if (typeName.endsWith("_LEGGINGS")) {
                    entity.getEquipment().setLeggings(item);
                } else if (typeName.endsWith("_BOOTS")) {
                    entity.getEquipment().setBoots(item);
                } else {
                    entity.getEquipment().setItemInMainHand(item);
                }
            }
        }
    }
}
