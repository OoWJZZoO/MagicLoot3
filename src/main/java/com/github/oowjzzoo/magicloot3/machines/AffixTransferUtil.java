package com.github.oowjzzoo.magicloot3.machines;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.github.oowjzzoo.magicloot3.ItemKeys;
import com.github.oowjzzoo.magicloot3.ItemManager;
import com.github.oowjzzoo.magicloot3.LootTier;
import com.github.oowjzzoo.magicloot3.Messages;

public final class AffixTransferUtil {

    private AffixTransferUtil() {}

    public record EffectEntry(String effectKey, boolean positive, int level) {}

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

    public static String serializeEffects(List<EffectEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (EffectEntry e : entries) {
            if (sb.length() > 0) sb.append(",");
            sb.append(e.effectKey).append(":").append(e.positive ? "+" : "-").append(":").append(e.level);
        }
        return sb.toString();
    }

    public static boolean hasPotionAffixes(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(ItemKeys.EFFECTS);
    }

    public static ItemStack stripAffixes(ItemStack item) {
        if (item == null) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.getPersistentDataContainer().remove(ItemKeys.EFFECTS);

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore = removeEffectLoreLines(lore);
        meta.setLore(lore.isEmpty() ? null : lore);

        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack appendAffixes(ItemStack item, List<EffectEntry> newEffects) {
        if (item == null) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String existingPdc = meta.getPersistentDataContainer().get(ItemKeys.EFFECTS, PersistentDataType.STRING);
        Map<String, EffectEntry> merged = new LinkedHashMap<>();
        for (EffectEntry e : parseEffects(existingPdc)) {
            merged.put(e.effectKey + ":" + (e.positive ? "+" : "-"), e);
        }
        for (EffectEntry e : newEffects) {
            merged.put(e.effectKey + ":" + (e.positive ? "+" : "-"), e);
        }

        List<EffectEntry> mergedList = new ArrayList<>(merged.values());
        meta.getPersistentDataContainer().set(ItemKeys.EFFECTS, PersistentDataType.STRING, serializeEffects(mergedList));

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore = removeEffectLoreLines(lore);
        List<String> newLore = buildEffectLore(mergedList);
        lore = insertBeforeTierLine(lore, newLore);
        meta.setLore(lore.isEmpty() ? null : lore);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Replace tier on an item without touching effects or other data.
     * Only updates if the new tier is strictly higher than the current tier.
     */
    public static ItemStack applyHighestTier(ItemStack item, LootTier current, LootTier incoming) {
        if (incoming == null || incoming == LootTier.NONE || incoming == LootTier.UNKNOWN) return item;
        if (current != null && current != LootTier.NONE && current != LootTier.UNKNOWN
                && current.getLevel() >= incoming.getLevel()) return item;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.getPersistentDataContainer().set(ItemKeys.TIER, PersistentDataType.STRING, incoming.name());

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore = removeTierLoreLine(lore);
        lore.add("");
        lore.add(Messages.get("tier_lore_prefix") + incoming.getTag());
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

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
                continue;
            }
            cleaned.add(line);
        }
        return cleaned;
    }

    public static ItemStack createAffixBook(List<EffectEntry> effects, LootTier tier) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta == null) return book;

        meta.getPersistentDataContainer().set(ItemKeys.EFFECTS, PersistentDataType.STRING, serializeEffects(effects));
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

        book.setItemMeta(meta);
        return book;
    }

    // --- Private helpers ---

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
            int insertIdx = tierIdx;
            if (insertIdx > 0 && result.get(insertIdx - 1).isEmpty()) {
                result.remove(insertIdx - 1);
                insertIdx--;
            }
            result.addAll(insertIdx, newLines);
            result.add(insertIdx + newLines.size(), "");
        } else {
            if (!result.isEmpty() && !result.get(result.size() - 1).isEmpty()) {
                result.add("");
            }
            result.addAll(newLines);
        }
        return result;
    }

    /** Remove the tier lore line (matching tier_lore_prefix) from the lore list. */
    private static List<String> removeTierLoreLine(List<String> lore) {
        String tierPrefix = ChatColor.stripColor(
                ChatColor.translateAlternateColorCodes('&', Messages.get("tier_lore_prefix")));
        List<String> result = new ArrayList<>();
        for (String line : lore) {
            if (!ChatColor.stripColor(line).startsWith(tierPrefix)) {
                result.add(line);
            }
        }
        // Also trim trailing empty lines left by removed tier line
        while (!result.isEmpty() && result.get(result.size() - 1).isEmpty()) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    private static boolean matchesEffectDisplayName(String strippedLine) {
        for (String name : ItemManager.effectDisplayNames) {
            if (strippedLine.contains(ChatColor.stripColor(name))) return true;
        }
        return false;
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
