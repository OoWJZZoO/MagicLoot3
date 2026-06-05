package com.github.oowjzzoo.magicloot3.machines;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.github.oowjzzoo.magicloot3.ItemKeys;
import com.github.oowjzzoo.magicloot3.ItemManager;
import com.github.oowjzzoo.magicloot3.LootTier;
import com.github.oowjzzoo.magicloot3.MagicLoot3;
import com.github.oowjzzoo.magicloot3.Messages;

public final class AffixTransferUtil {

    private AffixTransferUtil() {}

    public record EffectEntry(String effectKey, boolean positive, int level) {}

    /** Parse PDC effects string "speed:+:2,strength:-:1" into entries. */
    public static List<EffectEntry> parseEffects(String pdcData) {
        List<EffectEntry> entries = new ArrayList<>();
        if (pdcData == null || pdcData.isEmpty()) return entries;
        for (String entry : pdcData.split(",")) {
            String[] parts = entry.split(":");
            if (parts.length < 3) continue;
            try {
                entries.add(new EffectEntry(parts[0], "+".equals(parts[1]), Integer.parseInt(parts[2])));
            } catch (NumberFormatException ignored) {}
        }
        return entries;
    }

    /** Serialize entries back to PDC string "speed:+:2,strength:-:1". */
    public static String serializeEffects(List<EffectEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (EffectEntry e : entries) {
            if (sb.length() > 0) sb.append(",");
            sb.append(e.effectKey).append(":").append(e.positive ? "+" : "-").append(":").append(e.level);
        }
        return sb.toString();
    }

    /** Check if an item has potion affix PDC data. */
    public static boolean hasPotionAffixes(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(ItemKeys.EFFECTS);
    }

    /**
     * Remove all potion affix data (PDC + lore) from an item.
     * Preserves display name, tier, enchantments, and all other NBT.
     */
    public static ItemStack stripAffixes(ItemStack item) {
        if (item == null) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Remove PDC effects key
        meta.getPersistentDataContainer().remove(ItemKeys.EFFECTS);

        // Remove effect lore lines
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore = removeEffectLoreLines(lore);
        meta.setLore(lore.isEmpty() ? null : lore);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Append potion affixes to an item. Merges with existing effects:
     * same key + same polarity → overwrites level; otherwise adds new entry.
     */
    public static ItemStack appendAffixes(ItemStack item, List<EffectEntry> newEffects) {
        if (item == null) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Merge with existing effects
        String existingPdc = meta.getPersistentDataContainer().get(ItemKeys.EFFECTS, PersistentDataType.STRING);
        Map<String, EffectEntry> merged = new LinkedHashMap<>();
        for (EffectEntry e : parseEffects(existingPdc)) {
            merged.put(e.effectKey + ":" + (e.positive ? "+" : "-"), e);
        }
        for (EffectEntry e : newEffects) {
            merged.put(e.effectKey + ":" + (e.positive ? "+" : "-"), e);
        }

        // Write merged PDC
        List<EffectEntry> mergedList = new ArrayList<>(merged.values());
        String serialized = serializeEffects(mergedList);
        meta.getPersistentDataContainer().set(ItemKeys.EFFECTS, PersistentDataType.STRING, serialized);

        // Rebuild lore: strip old effect lines, insert new ones before tier line
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore = removeEffectLoreLines(lore);
        List<String> newLore = buildEffectLore(mergedList);
        lore = insertBeforeTierLine(lore, newLore);
        meta.setLore(lore.isEmpty() ? null : lore);

        item.setItemMeta(meta);

        // Verify PDC was actually written
        ItemMeta verify = item.getItemMeta();
        String verifyPdc = verify != null ? verify.getPersistentDataContainer()
                .get(ItemKeys.EFFECTS, PersistentDataType.STRING) : null;
        debug("appendAffixes: wrote PDC=" + serialized
                + " | verify after setItemMeta=" + verifyPdc
                + " | loreLines=" + (verify != null && verify.hasLore() ? verify.getLore().size() : 0));

        return item;
    }

    /** Build lore lines for a list of effect entries (random colors). */
    public static List<String> buildEffectLore(List<EffectEntry> entries) {
        List<String> lines = new ArrayList<>();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (EffectEntry e : entries) {
            String displayName = ItemManager.effectNames.getOrDefault(e.effectKey, e.effectKey);
            String color = ItemManager.colorCodes.isEmpty() ? "&e"
                    : ItemManager.colorCodes.get(r.nextInt(ItemManager.colorCodes.size()));
            lines.add(ChatColor.translateAlternateColorCodes('&',
                    color + (e.positive ? "+" : "-") + " " + displayName + " " + (e.level + 1)));
        }
        return lines;
    }

    /**
     * Remove lore lines that match the potion affix pattern
     * (color code + polarity + display name + level number).
     */
    public static List<String> removeEffectLoreLines(List<String> lore) {
        List<String> cleaned = new ArrayList<>();
        for (String line : lore) {
            String stripped = ChatColor.stripColor(line);
            if (stripped == null) {
                cleaned.add(line);
                continue;
            }
            stripped = stripped.trim();
            if ((stripped.startsWith("+ ") || stripped.startsWith("- "))
                    && matchesEffectDisplayName(stripped)
                    && endsWithNumber(stripped)) {
                continue; // skip this effect line
            }
            cleaned.add(line);
        }
        return cleaned;
    }

    /** Create an enchanted book with potion affix PDC + lore. */
    public static ItemStack createAffixBook(List<EffectEntry> effects, LootTier tier) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta == null) return book;

        // Write effects PDC
        meta.getPersistentDataContainer().set(ItemKeys.EFFECTS, PersistentDataType.STRING, serializeEffects(effects));
        // Write tier PDC (preserving source equipment's tier)
        if (tier != null && tier != LootTier.NONE && tier != LootTier.UNKNOWN) {
            meta.getPersistentDataContainer().set(ItemKeys.TIER, PersistentDataType.STRING, tier.name());
        }

        List<String> lore = new ArrayList<>();
        lore.addAll(buildEffectLore(effects));
        lore.add("");
        if (tier != null && tier != LootTier.NONE && tier != LootTier.UNKNOWN) {
            lore.add(Messages.get("tier_lore_prefix") + tier.getTag());
        }
        meta.setLore(lore);

        // A book with no stored enchants but with lore still looks like an enchanted book in inventory
        book.setItemMeta(meta);
        return book;
    }

    // --- Private helpers ---

    /** Insert new lore lines before the last blank-line+tier-line section, or append if none found. */
    private static List<String> insertBeforeTierLine(List<String> lore, List<String> newLines) {
        if (newLines.isEmpty()) return lore;
        String tierPrefix = ChatColor.translateAlternateColorCodes('&', Messages.get("tier_lore_prefix"));
        int tierIdx = -1;
        for (int i = lore.size() - 1; i >= 0; i--) {
            if (ChatColor.stripColor(lore.get(i)).startsWith(
                    ChatColor.stripColor(tierPrefix))) {
                tierIdx = i;
                break;
            }
        }

        List<String> result = new ArrayList<>(lore);
        if (tierIdx >= 0) {
            // Insert new effect lines + blank line before tier line
            // also remove any existing blank line just before the tier line
            int insertIdx = tierIdx;
            if (insertIdx > 0 && result.get(insertIdx - 1).isEmpty()) {
                result.remove(insertIdx - 1);
                insertIdx--;
            }
            result.addAll(insertIdx, newLines);
            result.add(insertIdx + newLines.size(), "");
        } else {
            // No tier line found — just append
            if (!result.isEmpty() && !result.get(result.size() - 1).isEmpty()) {
                result.add("");
            }
            result.addAll(newLines);
        }
        return result;
    }

    /**
     * Apply tier transfer: output tier = max(equipTier, bookTier).
     * Special handling for UNKNOWN equipment: replaces garbled name with a random
     * name from the pools, and sets tier to the book's tier (or COMMON if the book
     * has no tier). If neither item has a tier, the item is returned unchanged.
     */
    public static ItemStack applyTierTransfer(ItemStack item, LootTier equipTier, LootTier bookTier) {
        boolean equipNone = equipTier == null || equipTier == LootTier.NONE;
        boolean bookNone  = bookTier  == null || bookTier  == LootTier.NONE || bookTier == LootTier.UNKNOWN;
        if (equipNone && bookNone) return item;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (equipTier == LootTier.UNKNOWN) {
            String newName = generateRandomName();
            meta.setDisplayName(newName);
            LootTier target = bookNone ? LootTier.COMMON : bookTier;
            meta.getPersistentDataContainer().set(ItemKeys.TIER, PersistentDataType.STRING, target.name());
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            meta.setLore(replaceTierLore(lore, target));
        } else if (!bookNone && bookTier.getLevel() > equipTier.getLevel()) {
            meta.getPersistentDataContainer().set(ItemKeys.TIER, PersistentDataType.STRING, bookTier.name());
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            meta.setLore(replaceTierLore(lore, bookTier));
        } else {
            return item;
        }

        item.setItemMeta(meta);

        // Verify
        ItemMeta verify = item.getItemMeta();
        debug("applyTierTransfer: equipTier=" + equipTier + " bookTier=" + bookTier
                + " → result PDC tier=" + (verify != null ? verify.getPersistentDataContainer()
                        .get(ItemKeys.TIER, PersistentDataType.STRING) : "null"));

        return item;
    }

    private static String generateRandomName() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (ItemManager.prefixes.isEmpty() || ItemManager.suffixes.isEmpty()
                || ItemManager.colorCodes.isEmpty()) {
            return ChatColor.translateAlternateColorCodes('&',
                    "&7" + Messages.get("tiers.UNKNOWN"));
        }
        String prefix = ItemManager.prefixes.get(r.nextInt(ItemManager.prefixes.size()));
        String suffix = ItemManager.suffixes.get(r.nextInt(ItemManager.suffixes.size()));
        String color = ItemManager.colorCodes.get(r.nextInt(ItemManager.colorCodes.size()));
        char last = prefix.charAt(prefix.length() - 1);
        char first = suffix.charAt(0);
        boolean space = ((last >= 'A' && last <= 'Z') || (last >= 'a' && last <= 'z'))
                     && ((first >= 'A' && first <= 'Z') || (first >= 'a' && first <= 'z'));
        return ChatColor.translateAlternateColorCodes('&',
                color + prefix + (space ? " " : "") + suffix);
    }

    private static List<String> replaceTierLore(List<String> lore, LootTier newTier) {
        String tierPrefix = ChatColor.stripColor(
                ChatColor.translateAlternateColorCodes('&', Messages.get("tier_lore_prefix")));
        lore.removeIf(line -> ChatColor.stripColor(line).startsWith(tierPrefix));
        while (!lore.isEmpty() && lore.get(lore.size() - 1).isEmpty()) {
            lore.remove(lore.size() - 1);
        }
        lore.add("");
        lore.add(Messages.get("tier_lore_prefix") + newTier.getTag());
        return lore;
    }

    private static boolean matchesEffectDisplayName(String strippedLine) {
        for (String name : ItemManager.effectDisplayNames) {
            if (strippedLine.contains(ChatColor.stripColor(name))) return true;
        }
        return false;
    }

    private static void debug(String msg) {
        if (MagicLoot3.getInstance() != null && MagicLoot3.isDebug()) {
            MagicLoot3.getInstance().getLogger().log(Level.INFO, "[DEBUG] " + msg);
        }
    }

    private static boolean endsWithNumber(String s) {
        int lastSpace = s.lastIndexOf(' ');
        if (lastSpace < 0) return false;
        try {
            Integer.parseInt(s.substring(lastSpace + 1));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
