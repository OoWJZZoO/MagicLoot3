package com.github.oowjzzoo.magicloot3;

import java.util.ArrayList;
import java.util.Arrays;
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
import org.bukkit.persistence.PersistentDataType;
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

public class ItemManager {

    public static List<Enchantment> enchantments = new ArrayList<>();
    public static List<PotionEffectType> potionEffectTypes = new ArrayList<>();
    public static List<String> prefixes = new ArrayList<>();
    public static List<String> suffixes = new ArrayList<>();
    public static List<String> colorCodes = new ArrayList<>();
    public static List<String> effectDisplayNames = new ArrayList<>();
    public static List<LootType> enabledLootTypes = new ArrayList<>();
    public static Map<String, PotionEffectType> potionEffectMap = new HashMap<>();
    public static Map<String, String> effectNames = new HashMap<>();
    public static final List<Material> weightedTools = new ArrayList<>();
    static int[] weightedToolsCum = {};
    static int weightedToolsTotal = 0;
    public static final List<Material> weightedTreasure = new ArrayList<>();
    static int[] weightedTreasureCum = {};
    static int weightedTreasureTotal = 0;
    public static final List<ItemStack> weightedSlimefun = new ArrayList<>();
    static int[] weightedSlimefunCum = {};
    static int weightedSlimefunTotal = 0;

    /** Select an item by cumulative-weight binary search. O(log N). */
    static <T> T pickWeighted(List<T> items, int[] cum, int total) {
        if (total <= 0 || items.isEmpty()) return null;
        int idx = Arrays.binarySearch(cum, ThreadLocalRandom.current().nextInt(total));
        return items.get(idx < 0 ? -idx - 1 : idx + 1);
    }

    public static ItemStack createItem(LootType type) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (type == LootType.RANDOM && !enabledLootTypes.isEmpty()) {
            type = enabledLootTypes.get(random.nextInt(enabledLootTypes.size()));
        }
        if (type == LootType.SLIMEFUN && !Bukkit.getPluginManager().isPluginEnabled("Slimefun")) {
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
                    if (weightedTreasureTotal > 0) {
                        item.setType(pickWeighted(weightedTreasure, weightedTreasureCum, weightedTreasureTotal));
                        var cfg = MagicLoot3.getInstance().getConfig();
                        int tMin = cfg.getInt("chest.treasure-stack.min", 2);
                        int tMax = cfg.getInt("chest.treasure-stack.max", 9);
                        item.setAmount(tMin + random.nextInt(Math.max(1, tMax - tMin + 1)));
                    }
                }
                case POTION -> {
                    boolean isSplash = random.nextBoolean();
                    item.setType(isSplash ? Material.SPLASH_POTION : Material.LINGERING_POTION);
                    String pPrefix = prefixes.get(random.nextInt(prefixes.size()));
                    String pSuffix = suffixes.get(random.nextInt(suffixes.size()));
                    String name = colorCodes.get(random.nextInt(colorCodes.size()))
                            + pPrefix + needsSpace(pPrefix, pSuffix) + pSuffix;
                    PotionMeta meta = (PotionMeta) item.getItemMeta();
                    meta.setBasePotionType(isSplash ? PotionType.AWKWARD : PotionType.AWKWARD);
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
                    for (int i = 0; i < random.nextInt(2) + 1; i++) {
                        if (!potionEffectTypes.isEmpty()) {
                            PotionEffectType e = potionEffectTypes.get(random.nextInt(potionEffectTypes.size()));
                            int maxLvl = MagicLootConfig.getMaxLevel(e);
                            meta.addCustomEffect(new PotionEffect(e, (random.nextInt(8) + 2) * 10 * 20,
                                    random.nextInt(maxLvl)), true);
                        }
                    }
                    item.setItemMeta(meta);
                }
                case TOOL -> {
                    if (weightedToolsTotal <= 0) break;
                    if (random.nextInt(100) < 10) {
                        item.setType(Material.ARROW);
                        item.setAmount(4 + random.nextInt(20));
                    } else {
                        item.setType(pickWeighted(weightedTools, weightedToolsCum, weightedToolsTotal));
                        if (item.getType().getMaxDurability() == 0) break;
                        item = applyTier(item, LootTier.getRandom());
                        damageRandomPercent(item);
                    }
                }
                case SLIMEFUN -> {
                    if (weightedSlimefunTotal > 0) {
                        item = pickWeighted(weightedSlimefun, weightedSlimefunCum, weightedSlimefunTotal).clone();
                        var cfg = MagicLoot3.getInstance().getConfig();
                        int sMin = cfg.getInt("chest.slimefun-stack.min", 2);
                        int sMax = cfg.getInt("chest.slimefun-stack.max", 6);
                        item.setAmount(sMin + random.nextInt(Math.max(1, sMax - sMin + 1)));
                        if (item.getType() != Material.PLAYER_HEAD
                                && item.getType().getMaxStackSize() < item.getAmount()) {
                            item.setAmount(item.getType().getMaxStackSize());
                        }
                    }
                }
                case UNANALIZED -> {
                    if (weightedToolsTotal <= 0) break;
                    item.setType(pickWeighted(weightedTools, weightedToolsCum, weightedToolsTotal));
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
                        damageRandomPercent(item);
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

        String prefix = prefixes.get(random.nextInt(prefixes.size()));
        String suffix = suffixes.get(random.nextInt(suffixes.size()));
        String name = colorCodes.get(random.nextInt(colorCodes.size())) + prefix
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

        if (effectsMax > 0 && !potionEffectTypes.isEmpty()) {
            int range = Math.max(effectsMax - effectsMin, 1);
            for (int i = 0; i < random.nextInt(range) + effectsMin; i++) {
                PotionEffectType e = potionEffectTypes.get(random.nextInt(potionEffectTypes.size()));
                String enKey = e.getKey().getKey();
                int maxLvl = MagicLootConfig.getMaxLevel(e);
                if (maxLvl <= 0) continue;
                int level = random.nextInt(maxLvl);
                String apply = random.nextInt(10) > 5 ? "+" : "-";
                effectData.add(enKey + ":" + apply + ":" + level);
                String displayName = ItemManager.effectNames.getOrDefault(enKey, enKey);
                lore.add(ChatColor.translateAlternateColorCodes('&',
                        colorCodes.get(random.nextInt(colorCodes.size())) + apply + " " + displayName + " " + (level + 1)));
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
            if (enchMax > 0 && !enchantments.isEmpty()) {
                int range = Math.max(enchMax - enchMin, 1);
                for (int i = 0; i < random.nextInt(range) + enchMin; i++) {
                    Enchantment e = enchantments.get(random.nextInt(enchantments.size()));
                    int maxLvl = MagicLootConfig.getMaxLevel(e);
                    if (maxLvl > 0) {
                        storageMeta.addStoredEnchant(e, random.nextInt(maxLvl) + 1, true);
                    }
                }
            }
        }
        item.setItemMeta(im);

        // Enchantments for non-book items only
        if (!(im instanceof EnchantmentStorageMeta) && enchMax > 0 && !enchantments.isEmpty()) {
            int range = Math.max(enchMax - enchMin, 1);
            for (int i = 0; i < random.nextInt(range) + enchMin; i++) {
                Enchantment e = enchantments.get(random.nextInt(enchantments.size()));
                int maxLvl = MagicLootConfig.getMaxLevel(e);
                if (maxLvl > 0) {
                    item.addUnsafeEnchantment(e, random.nextInt(maxLvl) + 1);
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

    private static void damageRandomPercent(ItemStack item) {
        int maxDura = item.getType().getMaxDurability();
        if (maxDura <= 0) return;
        var cfg = MagicLoot3.getInstance().getConfig();
        int lossMin = cfg.getInt("durability.min", 10);
        int lossMax = cfg.getInt("durability.max", 90);
        int percent = lossMin + ThreadLocalRandom.current().nextInt(Math.max(1, lossMax - lossMin + 1));
        int damage = maxDura - (maxDura * percent / 100);
        applyDamage(item, Math.max(0, damage));
    }

    /** Adds a potion effect affix to the player's held item. */
    @SuppressWarnings("deprecation")
    public static void addEffectToItem(Player player, String enKey, String polarity, Integer levelArg) {
        PotionEffectType type = potionEffectMap.get(enKey);
        if (type == null) {
            player.sendMessage("§cUnknown effect: " + enKey);
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            player.sendMessage("§cYou must hold an item.");
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String apply = (polarity != null) ? polarity
                : (r.nextInt(10) > 5 ? "+" : "-");
        int level = (levelArg != null) ? levelArg : r.nextInt(256);
        if (level < 0 || level > 255) {
            player.sendMessage("§cLevel must be 0~255.");
            return;
        }
        String displayName = effectNames.getOrDefault(enKey, enKey);

        // Update PDC effects
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        String existing = meta.getPersistentDataContainer().get(ItemKeys.EFFECTS,
                org.bukkit.persistence.PersistentDataType.STRING);
        String newData = (existing != null && !existing.isEmpty())
                ? existing + "," + enKey + ":" + apply + ":" + level
                : enKey + ":" + apply + ":" + level;
        meta.getPersistentDataContainer().set(ItemKeys.EFFECTS,
                org.bukkit.persistence.PersistentDataType.STRING, newData);

        // Update lore
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(ChatColor.translateAlternateColorCodes('&',
                (colorCodes.isEmpty() ? "&e" : colorCodes.get(r.nextInt(colorCodes.size())))
                        + apply + " " + displayName + " " + (level + 1)));
        meta.setLore(lore);
        item.setItemMeta(meta);

        player.sendMessage("§aAdded " + apply + displayName + " " + (level + 1)
                + " to your item.");
    }

    /** Returns " " if prefix ends with a letter and suffix starts with one, else "". */
    private static String needsSpace(String prefix, String suffix) {
        char last = prefix.charAt(prefix.length() - 1);
        char first = suffix.charAt(0);
        if ((last >= 'A' && last <= 'Z') || (last >= 'a' && last <= 'z'))
            if ((first >= 'A' && first <= 'Z') || (first >= 'a' && first <= 'z'))
                return " ";
        return "";
    }

    public static void fillChest(Block block) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (block.getState() instanceof Chest chest) {
            var cfg = MagicLoot3.getInstance().getConfig();
            int min = cfg.getInt("chest.items.min", 2);
            int max = cfg.getInt("chest.items.max", 9);
            int count = min + random.nextInt(Math.max(1, max - min + 1));
            Inventory inv = chest.getInventory();
            for (int i = 0; i < count; i++) {
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
