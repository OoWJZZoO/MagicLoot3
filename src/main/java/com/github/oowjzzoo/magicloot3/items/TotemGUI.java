package com.github.oowjzzoo.magicloot3.items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.github.oowjzzoo.magicloot3.ItemKeys;
import com.github.oowjzzoo.magicloot3.MenuGUI;
import com.github.oowjzzoo.magicloot3.Messages;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;

final class TotemGUI {

    private static final String[] ATTR_KEYS = {
        "attack_damage", "entity_interaction_range", "max_health",
        "block_break_speed", "safe_fall_distance", "movement_speed",
        "jump_strength", "step_height", "special"
    };
    private static final int N_SLOTS = 9;

    /** Materials for row-2 description items, indexed by slot. */
    private static final Material[] DESC_MATERIALS = {
        Material.IRON_SWORD,          // 0: attack_damage
        Material.IRON_SPEAR,         // 1: entity_interaction_range (铁矛)
        Material.APPLE,               // 2: max_health
        Material.IRON_PICKAXE,        // 3: block_break_speed
        Material.HAY_BLOCK,           // 4: safe_fall_distance (干草块)
        Material.FEATHER,             // 5: movement_speed (羽毛)
        Material.WIND_CHARGE,         // 6: jump_strength (风弹)
        Material.LEATHER_HORSE_ARMOR, // 7: step_height (皮质马铠甲)
        Material.LIGHT                // 8: special
    };

    private TotemGUI() {}

    static void open(Player player, ItemStack totem) {
        ItemMeta meta = totem.getItemMeta();
        if (meta == null) return;

        boolean sealed = isSealed(meta);

        String title = Messages.get("gui.totem.title");
        MenuGUI menu = new MenuGUI(27, title).allowBottom().lockMainHand();

        menu.fillBg(0, 1, 2, 3, 5, 6, 7, 8);

        // Row 2: attribute descriptions (both sealed and unsealed)
        for (int i = 0; i < N_SLOTS; i++)
            menu.setDisplay(9 + i, buildDesc(i));

        if (sealed) {
            // Sealed: show sealed info, hide gem slots
            menu.setDisplay(4, buildSealedInfo(totem));
            for (int i = 0; i < N_SLOTS; i++)
                menu.setDisplay(18 + i, bgPane());
        } else {
            // Unsealed: slot 4 = seal button (if gems) or info display
            Map<Integer, String> gems = readGems(meta);
            int capacity = readCapacity(meta);
            int filled = gems.size();
            int sealCost = calculateSealCost(meta);

            if (sealCost > 0) {
                menu.setButton(4, buildSealButton(sealCost), (p, s, a) -> {
                    onSealClick(p, totem, menu, sealCost);
                });
            } else {
                menu.setDisplay(4, buildInfoDisplay(totem));
            }

            // Row 3: gem slots
            for (int i = 0; i < N_SLOTS; i++) {
                String sfId = gems.get(i);
                final int slotIdx = i;
                if (sfId != null) {
                    menu.setButton(18 + i, buildFilled(sfId), (p, s, a) -> {
                        onFilledClick(p, totem, menu, cursor(p), slotIdx);
                    });
                } else {
                    ItemStack icon = filled >= capacity ? buildBarrier() : buildEmpty();
                    menu.setButton(18 + i, icon, (p, s, a) -> {
                        insertGem(p, totem, menu, cursor(p), slotIdx);
                    });
                }
            }
        }

        menu.fillEmpty();
        menu.open(player);
    }

    /** Rebuild gem slot buttons in-place after a change, without closing the menu. */
    private static void refresh(MenuGUI menu, ItemStack totem) {
        ItemMeta meta = totem.getItemMeta();
        if (meta == null) return;

        // Sealed totems should never be refreshed (they close inventory on seal)
        if (isSealed(meta)) return;

        Map<Integer, String> gems = readGems(meta);
        int capacity = readCapacity(meta);
        int filled = gems.size();
        int sealCost = calculateSealCost(meta);

        // Refresh slot 4: seal button (if gems) or info display
        if (sealCost > 0) {
            menu.setButton(4, buildSealButton(sealCost), (p, s, a) -> {
                onSealClick(p, totem, menu, sealCost);
            });
        } else {
            menu.setDisplay(4, buildInfoDisplay(totem));
        }

        for (int i = 0; i < N_SLOTS; i++) {
            String sfId = gems.get(i);
            final int slotIdx = i;
            if (sfId != null) {
                menu.setButton(18 + i, buildFilled(sfId), (p, s, a) -> {
                    onFilledClick(p, totem, menu, cursor(p), slotIdx);
                });
            } else {
                ItemStack icon = filled >= capacity ? buildBarrier() : buildEmpty();
                menu.setButton(18 + i, icon, (p, s, a) -> {
                    insertGem(p, totem, menu, cursor(p), slotIdx);
                });
            }
        }
    }

    // ── PDC helpers (sparse format: "0:GEM_ID,2:GEM_ID") ──

    private static Map<Integer, String> readGems(ItemMeta meta) {
        Map<Integer, String> map = new LinkedHashMap<>();
        String data = meta.getPersistentDataContainer().get(ItemKeys.TOTEM_GEMS, PersistentDataType.STRING);
        if (data != null && !data.isEmpty()) {
            for (String entry : data.split(",")) {
                String[] kv = entry.split(":");
                if (kv.length != 2) continue;
                try { map.put(Integer.parseInt(kv[0]), kv[1]); }
                catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    private static int readTier(ItemMeta meta) {
        Integer v = meta.getPersistentDataContainer().get(ItemKeys.TOTEM_TIER, PersistentDataType.INTEGER);
        return v != null ? v : 3; // backward compat: pre-tier totems default to 3 (非凡)
    }

    private static int readCapacity(ItemMeta meta) {
        Integer v = meta.getPersistentDataContainer().get(ItemKeys.TOTEM_CAPACITY, PersistentDataType.INTEGER);
        if (v != null) return v;
        // Fallback for totems created without TOTEM_CAPACITY: use tier as base slot count
        return readTier(meta);
    }

    private static void writeGems(ItemStack totem, Map<Integer, String> gems) {
        ItemMeta meta = totem.getItemMeta();
        if (meta == null) return;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, String> e : gems.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        meta.getPersistentDataContainer().set(ItemKeys.TOTEM_GEMS,
                PersistentDataType.STRING, sb.toString());
        totem.setItemMeta(meta);
    }

    private static void writeCapacity(ItemStack totem, int cap) {
        ItemMeta meta = totem.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(ItemKeys.TOTEM_CAPACITY,
                PersistentDataType.INTEGER, cap);
        totem.setItemMeta(meta);
    }

    // ── Sealed totem helpers ──

    private static boolean isSealed(ItemMeta meta) {
        Byte v = meta.getPersistentDataContainer().get(ItemKeys.TOTEM_SEALED, PersistentDataType.BYTE);
        return v != null && v == 1;
    }

    private static int getForgeCount(ItemMeta meta) {
        Integer v = meta.getPersistentDataContainer().get(ItemKeys.TOTEM_FORGE_COUNT, PersistentDataType.INTEGER);
        return v != null ? v : 0;
    }

    private static int getTotemCost(ItemMeta meta) {
        Integer v = meta.getPersistentDataContainer().get(ItemKeys.TOTEM_COST, PersistentDataType.INTEGER);
        return v != null ? v : 0;
    }

    /** Calculate the projected sealing cost from currently embedded gems. Returns -1 if no gems. */
    private static int calculateSealCost(ItemMeta meta) {
        Map<Integer, String> gems = readGems(meta);
        if (gems.isEmpty()) return -1;

        double sumCost = 0.0;
        for (String sfId : gems.values()) {
            SlimefunItem sfItem = SlimefunItem.getById(sfId);
            if (sfItem != null) {
                sumCost += GemStone.getCost(sfItem.getItem());
            }
        }

        double tierMult = getTierMultiplier(readTier(meta));
        return (int) Math.floor((sumCost * tierMult) * (sumCost * tierMult));
    }

    private static double getTierMultiplier(int tier) {
        return switch (tier) {
            case 1 -> 0.6;
            case 2 -> 0.8;
            case 3 -> 1.0;
            case 4 -> 1.2;
            case 5 -> 1.4;
            default -> 1.0;
        };
    }

    // ── Item builders ──

    private static ItemStack buildDesc(int slot) {
        ItemStack icon = new ItemStack(DESC_MATERIALS[slot]);
        ItemMeta m = icon.getItemMeta();
        String key = ATTR_KEYS[slot];
        String name = Messages.raw("totem_slot." + key);
        if (name == null || name.isEmpty()) name = key;
        m.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e" + name));
        icon.setItemMeta(m);
        return icon;
    }

    /** Build the totem info display at GUI slot 4 (first row center).
     *  Shows tier, slot capacity, and mirrors the real totem's AttributeModifiers for tooltip display. */
    private static ItemStack buildInfoDisplay(ItemStack totem) {
        ItemMeta totemMeta = totem.getItemMeta();
        if (totemMeta == null) return new ItemStack(Material.BARRIER);

        ItemStack icon = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta im = icon.getItemMeta();

        int tier = readTier(totemMeta);
        String tierName = Messages.get("totem_tier." + tier);
        im.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&f品级：") + tierName);

        int baseSlots = tier; // tier value = base slot count by design
        int capacity = readCapacity(totemMeta);
        int filled = readGems(totemMeta).size();

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.translateAlternateColorCodes('&', "&f初始槽位：" + baseSlots));
        lore.add(ChatColor.translateAlternateColorCodes('&', "&f实际槽位：" + filled + "/" + capacity));
        im.setLore(lore);

        // Copy AttributeModifiers so the client auto-shows attribute stats in the tooltip
        TotemItem.copyAttributeModifiers(totemMeta, im);

        icon.setItemMeta(im);
        return icon;
    }

    /** Build the sealed totem info display at GUI slot 4.
     *  Shows seal cost, forge count, and mirrors the real totem's AttributeModifiers. */
    private static ItemStack buildSealedInfo(ItemStack totem) {
        ItemStack icon = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta im = icon.getItemMeta();
        ItemMeta totemMeta = totem.getItemMeta();
        if (totemMeta == null) return icon;

        im.setDisplayName(ChatColor.translateAlternateColorCodes('&', Messages.get("totem.sealed_title")));
        int cost = getTotemCost(totemMeta);
        int forge = getForgeCount(totemMeta);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.translateAlternateColorCodes('&', "&7封定费用: &f" + cost));
        lore.add(ChatColor.translateAlternateColorCodes('&', "&7叠锻次数: &b" + forge));

        // Copy AttributeModifiers so the client auto-shows attribute stats in the tooltip
        TotemItem.copyAttributeModifiers(totemMeta, im);

        im.setLore(lore);
        icon.setItemMeta(im);
        return icon;
    }

    /** Build the clickable seal button at slot 4 (when gems are present but totem is not yet sealed). */
    private static ItemStack buildSealButton(int sealCost) {
        ItemStack icon = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta im = icon.getItemMeta();
        im.setDisplayName(ChatColor.translateAlternateColorCodes('&', Messages.get("gui.totem.seal_button_title")));
        List<String> lore = new ArrayList<>();
        for (String line : Messages.getList("gui.totem.seal_button_lore")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line.replace("{0}", String.valueOf(sealCost))));
        }
        im.setLore(lore);
        icon.setItemMeta(im);
        return icon;
    }

    private static ItemStack buildEmpty() {
        ItemStack icon = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta m = icon.getItemMeta();
        m.setDisplayName(Messages.get("gui.totem.insert_title"));
        List<String> lore = new ArrayList<>();
        for (String line : Messages.getList("gui.totem.insert_lore")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        m.setLore(lore);
        icon.setItemMeta(m);
        return icon;
    }

    private static ItemStack buildBarrier() {
        ItemStack icon = new ItemStack(Material.BARRIER);
        ItemMeta m = icon.getItemMeta();
        m.setDisplayName(Messages.get("gui.totem.full_title"));
        icon.setItemMeta(m);
        return icon;
    }

    private static ItemStack buildFilled(String sfId) {
        io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem sf =
                io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem.getById(sfId);
        return sf != null ? sf.getItem().clone() : new ItemStack(Material.BARRIER);
    }

    // ── Click handlers ──

    @SuppressWarnings("deprecation")
    private static ItemStack cursor(Player p) { return p.getItemOnCursor(); }

    /** Chisel click on filled slot — destroy gem, recalc capacity. */
    private static void onFilledClick(Player player, ItemStack totem, MenuGUI menu,
                                       ItemStack cur, int slotIdx) {
        if (!isChisel(cur)) return;

        Map<Integer, String> gems = readGems(totem.getItemMeta());
        int cap = readCapacity(totem.getItemMeta());
        String removedId = gems.get(slotIdx);
        if (removedId == null) return;

        // Calculate capacity after removal (undo gem's own slot + its expansion)
        int gemCap = gemCapacity(removedId);
        int afterCap = cap - gemCap;
        int afterFilled = gems.size() - 1;
        if (afterFilled > afterCap) return; // would exceed capacity, reject

        // Damage chisel using vanilla durability
        cur.damage(1, player);
        if (cur.getAmount() > 1) cur.setAmount(cur.getAmount() - 1);
        // damage() already handles break + sound

        gems.remove(slotIdx);
        writeGems(totem, gems);
        writeCapacity(totem, afterCap);
        TotemItem.rebuildAttributes(totem);
        refresh(menu, totem);
    }

    /** Insert gem from cursor. */
    @SuppressWarnings("deprecation")
    private static void insertGem(Player player, ItemStack totem, MenuGUI menu,
                                   ItemStack cur, int slotIdx) {
        if (!GemStone.isGem(cur)) return;
        if (!slotMatches(GemStone.getSlots(cur), slotIdx)) return;

        Map<Integer, String> gems = readGems(totem.getItemMeta());
        int cap = readCapacity(totem.getItemMeta());
        int filled = gems.size();
        int gemCap = GemStone.getCapacity(cur);

        if (filled >= cap && filled >= cap + gemCap) return;

        io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem sfItem =
                io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem.getByItem(cur);
        String sfId = sfItem != null ? sfItem.getId() : null;
        if (sfId == null) return;

        if (cur.getAmount() > 1) cur.setAmount(cur.getAmount() - 1);
        else player.setItemOnCursor(null);

        gems.put(slotIdx, sfId);
        writeGems(totem, gems);
        if (gemCap > 0) writeCapacity(totem, cap + gemCap);

        TotemItem.rebuildAttributes(totem);
        refresh(menu, totem);
    }

    /** Handle seal button click at slot 4. */
    private static void onSealClick(Player player, ItemStack totem, MenuGUI menu, int displayedCost) {
        ItemMeta meta = totem.getItemMeta();
        if (meta == null) return;

        // Re-read gems and recalculate to prevent stale exploits
        Map<Integer, String> gems = readGems(meta);
        if (gems.isEmpty()) {
            player.sendMessage(Messages.get("totem.seal.no_gems"));
            return;
        }

        int cost = calculateSealCost(meta);
        if (cost <= 0) return;

        if (player.getLevel() < cost) {
            player.sendMessage(Messages.get("totem.seal.no_xp", cost, player.getLevel()));
            return;
        }

        player.setLevel(player.getLevel() - cost);

        // Clear gem data — attributes remain on ItemMeta
        meta.getPersistentDataContainer().remove(ItemKeys.TOTEM_GEMS);
        meta.getPersistentDataContainer().remove(ItemKeys.TOTEM_CAPACITY);
        // Set sealed state
        meta.getPersistentDataContainer().set(ItemKeys.TOTEM_SEALED, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(ItemKeys.TOTEM_COST, PersistentDataType.INTEGER, cost);
        meta.getPersistentDataContainer().set(ItemKeys.TOTEM_FORGE_COUNT, PersistentDataType.INTEGER, 0);
        totem.setItemMeta(meta);

        // Update lore to reflect sealed state
        TotemItem.updateSealedLore(totem);

        player.sendMessage(Messages.get("totem.seal.success", cost));
        player.closeInventory();
    }

    // ── Helpers ──

    private static boolean isChisel(ItemStack item) {
        io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem sf =
                io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem.getByItem(item);
        return sf != null && "MAGICLOOT_CHISEL".equals(sf.getId());
    }

    private static boolean slotMatches(String gemSlots, int slotIdx) {
        if (gemSlots == null || gemSlots.isEmpty()) return false;
        for (String s : gemSlots.split(",")) {
            if (Integer.parseInt(s.trim()) == slotIdx) return true;
        }
        return false;
    }

    /** Standard gray glass pane background for disabled slots. */
    private static ItemStack bgPane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = pane.getItemMeta();
        m.setDisplayName(" ");
        pane.setItemMeta(m);
        return pane;
    }

    /** Look up a gem's capacity from its template. */
    private static int gemCapacity(String sfId) {
        io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem sf =
                io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem.getById(sfId);
        return sf != null ? GemStone.getCapacity(sf.getItem()) : 0;
    }
}
