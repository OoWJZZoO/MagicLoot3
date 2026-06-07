package com.github.oowjzzoo.magicloot3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.groups.FlexItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.NestedItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.SubItemGroup;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

/**
 * Slimefun loot weight config GUI — /ml sf_loot.
 */
final class SfLootGUI extends LootConfigGUI {

    private static final SfLootGUI INSTANCE = new SfLootGUI();
    private static ChatHandler chatListener;

    static void open(Player player, Plugin plugin) {
        PLUGIN = plugin;
        Map<String, Integer> cache = INSTANCE.loadConfig();
        // Fill defaults for SF items not yet in config (addons installed after ours)
        if (Bukkit.getPluginManager().isPluginEnabled("Slimefun")) {
            for (SlimefunItem item : Slimefun.getRegistry().getAllSlimefunItems()) {
                if (item.getId().startsWith("JEG") || "MAGICLOOT_UNIDENTIFIED".equals(item.getId()) || "MAGICLOOT_PLAYER_HEAD".equals(item.getId())) continue;
                cache.putIfAbsent(item.getId(), 100);
            }
        }
        CACHES.put(player.getUniqueId(), cache);
        if (chatListener == null) {
            chatListener = new ChatHandler();
            Bukkit.getPluginManager().registerEvents(chatListener, plugin);
        }
        INSTANCE.openMainPage(player, 1);
    }

    @Override protected String configSection() { return "slimefun"; }

    @Override protected String itemName(String id) {
        SlimefunItem s = SlimefunItem.getById(id);
        return s != null ? s.getItemName() : id;
    }

    @Override protected ItemStack itemIcon(String id) {
        SlimefunItem s = SlimefunItem.getById(id);
        return s != null ? s.getItem().clone() : new ItemStack(org.bukkit.Material.BARRIER);
    }

    // ── Main page ──

    @Override protected void openMainPage(Player player, int page) {
        Map<String, Integer> cache = CACHES.get(player.getUniqueId());
        if (cache == null) return;

        List<ItemGroup> groups = getVisibleGroups(player);
        int total = groups.size() + 1; // +1 for the catch-all
        int pages = Math.max(1, (total - 1) / MAX_ITEMS + 1);
        int cur = Math.min(page, pages);

        var menu = newMenu(player, m("categories"));
        var sw = new HashSet<UUID>();

        int start = MAX_ITEMS * (cur - 1);
        int end = start + MAX_ITEMS;
        int slot = 9;
        for (int i = start; i < groups.size() && slot < 45; i++, slot++) {
            ItemGroup group = groups.get(i);
            menu.addItem(slot, group.getItem(player));
            final int idx = i;
            menu.addMenuClickHandler(slot, (pl, s, it, a) -> {
                sw.add(pl.getUniqueId());
                // When drilling into a main-page group, back always returns to main page
                Runnable back = () -> openMainPage(pl, 1);
                openGroup(pl, groups.get(idx), 1, back);
                return false;
            });
        }

        // Catch-all: show all items from config if it falls on this page
        int catchIdx = groups.size();
        if (catchIdx >= start && catchIdx < end && slot < 45) {
            ItemStack icon = new ItemStack(org.bukkit.Material.CHEST);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&f&l" + m("all_items")));
            icon.setItemMeta(meta);
            menu.addItem(slot, icon);
            menu.addMenuClickHandler(slot, (pl, s, it, a) -> {
                sw.add(pl.getUniqueId());
                openAllConfigItems(pl, 1, () -> openMainPage(pl, 1));
                return false;
            });
        }

        addFooterBg(menu);
        addPrevNext(menu, player, cur, pages, sw,
                () -> openMainPage(player, cur - 1),
                () -> openMainPage(player, cur + 1));
        finishMenu(menu, player, sw);
    }

    // ── Route ──

    @SuppressWarnings("unchecked")
    private void openGroup(Player player, ItemGroup group, int page, Runnable back) {
        if (group instanceof NestedItemGroup nested) {
            showSubGroups(player, nested, findNestedChildren(nested, player), page, back);
            return;
        }
        if (group instanceof FlexItemGroup) {
            List<ItemGroup> subs = null;
            try {
                var m = group.getClass().getDeclaredMethod("getItemGroup");
                m.setAccessible(true);
                subs = (List<ItemGroup>) m.invoke(group);
            } catch (Exception ignored) {}
            // If no getItemGroup(), collect non-flex groups from the same addon
            if (subs == null || subs.isEmpty()) {
                subs = new ArrayList<>();
                var addon = group.getAddon();
                for (ItemGroup g : Slimefun.getRegistry().getAllItemGroups()) {
                    if (g instanceof FlexItemGroup || g.getItems().isEmpty()) continue;
                    if (g.getAddon() == addon) subs.add(g);
                }
            }
            showSubGroups(player, group, subs, page, back);
            return;
        }
        openItems(player, group, page, back);
    }

    // ── Sub-group page ──

    private List<ItemGroup> findNestedChildren(NestedItemGroup parent, Player player) {
        List<ItemGroup> subs = new ArrayList<>();
        for (ItemGroup g : Slimefun.getRegistry().getAllItemGroups()) {
            if (g instanceof SubItemGroup sub && sub.getParent() == parent)
                subs.add(sub);
        }
        subs.sort(Comparator.comparing(
                g -> ChatColor.stripColor(g.getDisplayName(player))));
        return subs;
    }

    private void showSubGroups(Player player, ItemGroup parent, List<? extends ItemGroup> subs,
                                int page, Runnable back) {
        subs.sort(Comparator.comparing(
                g -> ChatColor.stripColor(g.getDisplayName(player))));

        int pages = Math.max(1, (subs.size() - 1) / MAX_ITEMS + 1);
        final int cur = Math.min(page, pages);

        var menu = newMenu(player, ChatColor.stripColor(parent.getDisplayName(player)));
        var sw = new HashSet<UUID>();
        addBack(menu, 1, sw, back);

        int start = MAX_ITEMS * (cur - 1);
        int slot = 9;
        for (int i = start; i < subs.size() && slot < 45; i++, slot++) {
            ItemGroup sub = subs.get(i);
            menu.addItem(slot, sub.getItem(player));
            final ItemGroup snap = sub;
            menu.addMenuClickHandler(slot, (pl, s, it, a) -> {
                sw.add(pl.getUniqueId());
                final int pageSnap = cur;
                Runnable subBack = () -> showSubGroups(player, parent, subs, pageSnap, back);
                openGroup(pl, snap, 1, subBack);
                return false;
            });
        }

        addFooterBg(menu);
        addPrevNext(menu, player, cur, pages, sw,
                () -> showSubGroups(player, parent, subs, cur - 1, back),
                () -> showSubGroups(player, parent, subs, cur + 1, back));
        finishMenu(menu, player, sw);
    }

    // ── Items page ──

    private void openItems(Player player, ItemGroup group, int page, Runnable back) {
        Map<String, Integer> cache = CACHES.get(player.getUniqueId());
        if (cache == null) return;

        List<SlimefunItem> items = new ArrayList<>(group.getItems());
        items.removeIf(i -> i.isDisabledIn(player.getWorld()));
        int pages = Math.max(1, (items.size() - 1) / MAX_ITEMS + 1);
        final int cur = Math.min(page, pages);

        var menu = newMenu(player, ChatColor.stripColor(group.getDisplayName(player)));
        var sw = new HashSet<UUID>();

        addBack(menu, 1, sw, back);

        int tw = computeTotal(cache);
        int start = MAX_ITEMS * (cur - 1);
        int slot = 9;
        for (int i = start; i < items.size() && slot < 45; i++, slot++) {
            SlimefunItem sfItem = items.get(i);
            String id = sfItem.getId();
            int weight = cache.getOrDefault(id, 0);
            menu.addItem(slot, buildItem(id, weight, tw));
            final int pageSnap = cur;
            menu.addMenuClickHandler(slot, makeClickHandler(player, id, sw,
                    () -> openItems(player, group, pageSnap, back)));
        }

        addFooterBg(menu);
        addPrevNext(menu, player, cur, pages, sw,
                () -> openItems(player, group, cur - 1, back),
                () -> openItems(player, group, cur + 1, back));
        finishMenu(menu, player, sw);
    }

    // ── All config items page (catch-all) ──

    private void openAllConfigItems(Player player, int page, Runnable back) {
        Map<String, Integer> cache = CACHES.get(player.getUniqueId());
        if (cache == null) return;

        List<String> ids = new ArrayList<>(cache.keySet());
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        int pages = Math.max(1, (ids.size() - 1) / MAX_ITEMS + 1);
        final int cur = Math.min(page, pages);

        var menu = newMenu(player, m("all_items"));
        var sw = new HashSet<UUID>();
        addBack(menu, 1, sw, back);

        int tw = computeTotal(cache);
        int start = MAX_ITEMS * (cur - 1);
        int slot = 9;
        for (int i = start; i < ids.size() && slot < 45; i++, slot++) {
            String id = ids.get(i);
            int weight = cache.getOrDefault(id, 0);
            menu.addItem(slot, buildItem(id, weight, tw));
            final int pageSnap = cur;
            menu.addMenuClickHandler(slot, makeClickHandler(player, id, sw,
                    () -> openAllConfigItems(player, pageSnap, back)));
        }

        addFooterBg(menu);
        addPrevNext(menu, player, cur, pages, sw,
                () -> openAllConfigItems(player, cur - 1, back),
                () -> openAllConfigItems(player, cur + 1, back));
        finishMenu(menu, player, sw);
    }

    // ── Visible groups ──

    private List<ItemGroup> getVisibleGroups(Player player) {
        List<ItemGroup> groups = new ArrayList<>();
        for (ItemGroup group : Slimefun.getRegistry().getAllItemGroups()) {
            if (group instanceof SubItemGroup) continue;
            if (group instanceof FlexItemGroup flex) {
                if (!flex.isVisible(player, null, SlimefunGuideMode.CHEAT_MODE)) continue;
            } else if (group.isHidden(player)) continue;
            groups.add(group);
        }
        return groups;
    }
}
