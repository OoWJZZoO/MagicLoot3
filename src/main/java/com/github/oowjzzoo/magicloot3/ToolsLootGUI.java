package com.github.oowjzzoo.magicloot3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Tools/Treasure loot weight config GUI — /ml tools_loot.
 * Flat list (no categories), items sourced from Items.yml tools section.
 */
final class ToolsLootGUI extends LootGUI {

    private static final ToolsLootGUI INSTANCE = new ToolsLootGUI();
    private static ChatHandler chatListener;

    static void open(Player player, Plugin plugin) {
        INSTANCE.ensureListener(plugin);
        Map<String, Integer> cache = INSTANCE.loadConfig();
        // Fill defaults for items not yet in config
        if (Bukkit.getPluginManager().isPluginEnabled("Slimefun")) {
            for (String key : INSTANCE.allMaterialKeys()) {
                cache.putIfAbsent(key, 100);
            }
        }
        CACHES.put(player.getUniqueId(), cache);
        INSTANCE.openMainPage(player, 1);
    }

    private void ensureListener(Plugin plugin) {
        PLUGIN = plugin;
        if (chatListener == null) {
            chatListener = new ChatHandler();
            Bukkit.getPluginManager().registerEvents(chatListener, plugin);
        }
    }

    @Override protected String configSection() { return "tools"; }

    @Override protected String itemName(String id) {
        try { return Material.valueOf(id).toString().toLowerCase().replace('_', ' '); }
        catch (IllegalArgumentException e) { return id; }
    }

    @Override protected ItemStack itemIcon(String id) {
        try { return new ItemStack(Material.valueOf(id)); }
        catch (IllegalArgumentException e) { return new ItemStack(Material.BARRIER); }
    }

    @Override protected void openMainPage(Player player, int page) {
        Map<String, Integer> cache = CACHES.get(player.getUniqueId());
        if (cache == null) return;

        List<String> keys = new ArrayList<>(cache.keySet());
        keys.sort(Comparator.naturalOrder());
        int pages = Math.max(1, (keys.size() - 1) / MAX_ITEMS + 1);
        final int cur = Math.min(page, pages);

        var menu = newMenu(player, m("tools"));
        var sw = new HashSet<UUID>();
        if (isReadonly(player)) {
            addBack(menu, 1, sw, () -> LootViewerGUI.open(player, PLUGIN));
        }
        int tw = computeTotal(cache);

        int start = MAX_ITEMS * (cur - 1);
        int slot = 9;
        for (int i = start; i < keys.size() && slot < 45; i++, slot++) {
            String id = keys.get(i);
            int weight = cache.getOrDefault(id, 0);
            menu.addItem(slot, buildItem(id, weight, tw, player));
            final int pageSnap = cur;
            menu.addMenuClickHandler(slot, makeClickHandler(player, id, sw,
                    () -> openMainPage(player, pageSnap)));
        }

        addFooterBg(menu);
        addPrevNext(menu, player, cur, pages, sw,
                () -> openMainPage(player, cur - 1),
                () -> openMainPage(player, cur + 1));
        finishMenu(menu, player, sw);
    }

    /** All material keys that would appear in the tools section. */
    private List<String> allMaterialKeys() {
        List<String> keys = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.isBlock() || m.toString().contains("BOOK")) continue;
            boolean ok = false;
            for (org.bukkit.enchantments.Enchantment e : org.bukkit.enchantments.Enchantment.values()) {
                if (e.isCursed()) continue;
                if (e.canEnchantItem(new ItemStack(m))) { ok = true; break; }
            }
            if (ok) keys.add(m.toString());
        }
        return keys;
    }
}
