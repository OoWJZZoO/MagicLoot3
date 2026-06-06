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
 * Same structure as /sf cheat: main category grid, click to drill down.
 */
final class SfLootGUI extends LootConfigGUI {

    private static final SfLootGUI INSTANCE = new SfLootGUI();
    private static ChatHandler chatListener;

    static void open(Player player, Plugin plugin) {
        PLUGIN = plugin;
        CACHES.put(player.getUniqueId(), INSTANCE.loadConfig());
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

    // ── Main page: category grid ──

    @Override protected void openMainPage(Player player, int page) {
        Map<String, Integer> cache = CACHES.get(player.getUniqueId());
        if (cache == null) return;

        List<ItemGroup> groups = getVisibleGroups(player);
        int pages = Math.max(1, (groups.size() - 1) / MAX_ITEMS + 1);
        int cur = Math.min(page, pages);

        var menu = newMenu(player, "categories");
        var sw = new HashSet<UUID>();

        int start = MAX_ITEMS * (cur - 1);
        int slot = 9;
        for (int i = start; i < groups.size() && slot < 45; i++, slot++) {
            ItemGroup group = groups.get(i);
            menu.addItem(slot, group.getItem(player));
            final int idx = i;
            menu.addMenuClickHandler(slot, (pl, s, it, a) -> {
                sw.add(pl.getUniqueId());
                openGroup(pl, groups.get(idx), 1);
                return false;
            });
        }

        addFooterBg(menu);
        addPrevNext(menu, player, cur, pages, sw,
                () -> openMainPage(player, cur - 1),
                () -> openMainPage(player, cur + 1));
        finishMenu(menu, player, sw);
    }

    // ── Route to items / sub-groups ──

    private void openGroup(Player player, ItemGroup group, int page) {
        if (group instanceof NestedItemGroup nested) {
            openSubGroups(player, nested, page);
        } else if (group instanceof FlexItemGroup) {
            openFlatItems(player, group, page);
        } else {
            openItems(player, group, page);
        }
    }

    // ── Sub-group page (NestedItemGroup) ──

    private void openSubGroups(Player player, NestedItemGroup parent, int page) {
        Map<String, Integer> cache = CACHES.get(player.getUniqueId());
        if (cache == null) return;

        // Find all SubItemGroup children
        List<SubItemGroup> subs = new ArrayList<>();
        for (ItemGroup g : Slimefun.getRegistry().getAllItemGroups()) {
            if (g instanceof SubItemGroup sub && sub.getParent() == parent)
                subs.add(sub);
        }
        subs.sort(Comparator.comparing(
                g -> ChatColor.stripColor(g.getDisplayName(player))));

        int pages = Math.max(1, (subs.size() - 1) / MAX_ITEMS + 1);
        final int cur = Math.min(page, pages);

        var menu = newMenu(player, ChatColor.stripColor(parent.getDisplayName(player)));
        var sw = new HashSet<UUID>();
        addBack(menu, 1, sw, () -> openMainPage(player, 1));

        int start = MAX_ITEMS * (cur - 1);
        int slot = 9;
        for (int i = start; i < subs.size() && slot < 45; i++, slot++) {
            SubItemGroup sub = subs.get(i);
            menu.addItem(slot, sub.getItem(player));
            final SubItemGroup snap = sub;
            menu.addMenuClickHandler(slot, (pl, s, it, a) -> {
                sw.add(pl.getUniqueId());
                openItems(pl, snap, 1);
                return false;
            });
        }

        addPrevNext(menu, player, cur, pages, sw,
                () -> openSubGroups(player, parent, cur - 1),
                () -> openSubGroups(player, parent, cur + 1));
        finishMenu(menu, player, sw);
    }

    // ── Items page (non-flex group or SubItemGroup) ──

    private void openItems(Player player, ItemGroup group, int page) {
        Map<String, Integer> cache = CACHES.get(player.getUniqueId());
        if (cache == null) return;

        List<SlimefunItem> items = new ArrayList<>(group.getItems());
        items.removeIf(i -> i.isDisabledIn(player.getWorld()));
        int pages = Math.max(1, (items.size() - 1) / MAX_ITEMS + 1);
        final int cur = Math.min(page, pages);

        var menu = newMenu(player, ChatColor.stripColor(group.getDisplayName(player)));
        var sw = new HashSet<UUID>();

        addBack(menu, 1, sw, () -> openBack(player, group));

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
                    () -> openItems(player, group, pageSnap)));
        }

        addPrevNext(menu, player, cur, pages, sw,
                () -> openItems(player, group, cur - 1),
                () -> openItems(player, group, cur + 1));
        finishMenu(menu, player, sw);
    }

    // ── Fallback for unknown flex groups: show sub-groups by same addon ──

    private void openFlatItems(Player player, ItemGroup flex, int page) {
        Map<String, Integer> cache = CACHES.get(player.getUniqueId());
        if (cache == null) return;

        // Find visible non-flex groups from the same addon
        List<ItemGroup> subs = new ArrayList<>();
        var addon = flex.getAddon();
        for (ItemGroup g : Slimefun.getRegistry().getAllItemGroups()) {
            if (g instanceof FlexItemGroup || g.isHidden(player) || g.getItems().isEmpty()) continue;
            if (g.getAddon() == addon) subs.add(g);
        }
        subs.sort(Comparator.comparing(
                g -> ChatColor.stripColor(g.getDisplayName(player))));

        int pages = Math.max(1, (subs.size() - 1) / MAX_ITEMS + 1);
        final int cur = Math.min(page, pages);

        var menu = newMenu(player, ChatColor.stripColor(flex.getDisplayName(player)));
        var sw = new HashSet<UUID>();
        addBack(menu, 1, sw, () -> openMainPage(player, 1));

        int start = MAX_ITEMS * (cur - 1);
        int slot = 9;
        for (int i = start; i < subs.size() && slot < 45; i++, slot++) {
            ItemGroup sub = subs.get(i);
            menu.addItem(slot, sub.getItem(player));
            final ItemGroup snap = sub;
            menu.addMenuClickHandler(slot, (pl, s, it, a) -> {
                sw.add(pl.getUniqueId());
                openItems(pl, snap, 1);
                return false;
            });
        }

        addFooterBg(menu);
        addPrevNext(menu, player, cur, pages, sw,
                () -> openFlatItems(player, flex, cur - 1),
                () -> openFlatItems(player, flex, cur + 1));
        finishMenu(menu, player, sw);
    }

    // ── Back routing ──

    private void openBack(Player player, ItemGroup group) {
        if (group instanceof SubItemGroup sub)
            openSubGroups(player, sub.getParent(), 1);
        else
            openMainPage(player, 1);
    }

    // ── Visible groups (matching CheatSheetSlimefunGuide) ──

    private List<ItemGroup> getVisibleGroups(Player player) {
        List<ItemGroup> groups = new ArrayList<>();
        for (ItemGroup group : Slimefun.getRegistry().getAllItemGroups()) {
            if (group instanceof FlexItemGroup flex) {
                if (flex.isVisible(player, null, SlimefunGuideMode.CHEAT_MODE))
                    groups.add(group);
            } else if (!group.isHidden(player) && !group.getItems().isEmpty())
                groups.add(group);
        }
        return groups;
    }
}
