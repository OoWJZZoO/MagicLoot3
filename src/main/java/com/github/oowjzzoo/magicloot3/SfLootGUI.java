package com.github.oowjzzoo.magicloot3;

import java.util.ArrayList;
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
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

/**
 * Slimefun loot weight config GUI — /ml sf_loot
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
                openCategory(pl, groups.get(idx), 1);
                return false;
            });
        }

        addPrevNext(menu, player, cur, pages, sw,
                () -> openMainPage(player, cur - 1),
                () -> openMainPage(player, cur + 1));
        finishMenu(menu, player, sw);
    }

    private void openCategory(Player player, ItemGroup group, int page) {
        Map<String, Integer> cache = CACHES.get(player.getUniqueId());
        if (cache == null) return;

        List<SlimefunItem> items;
        String title;
        if (group instanceof FlexItemGroup) {
            // Flex groups can't expose items via getItems() — collect all items
            // from all visible non-flex groups instead.
            items = collectAllVisibleItems(player);
            title = ChatColor.stripColor(group.getDisplayName(player));
        } else {
            items = new ArrayList<>(group.getItems());
            title = ChatColor.stripColor(group.getDisplayName(player));
        }
        items.removeIf(i -> i.isDisabledIn(player.getWorld()));
        int pages = Math.max(1, (items.size() - 1) / MAX_ITEMS + 1);
        int cur = Math.min(page, pages);

        var menu = newMenu(player, ChatColor.stripColor(group.getDisplayName(player)));
        var sw = new HashSet<UUID>();

        addBack(menu, 1, sw, () -> openMainPage(player, 1));

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
                    () -> openCategory(player, group, pageSnap)));
        }

        addPrevNext(menu, player, cur, pages, sw,
                () -> openCategory(player, group, cur - 1),
                () -> openCategory(player, group, cur + 1));
        finishMenu(menu, player, sw);
    }

    /** Collect all SlimefunItems from ALL visible non-flex groups. */
    private List<SlimefunItem> collectAllVisibleItems(Player player) {
        List<SlimefunItem> all = new ArrayList<>();
        for (ItemGroup g : Slimefun.getRegistry().getAllItemGroups()) {
            if (g instanceof FlexItemGroup || g.isHidden(player) || g.getItems().isEmpty())
                continue;
            for (SlimefunItem item : g.getItems())
                if (!item.isDisabledIn(player.getWorld())) all.add(item);
        }
        all.sort(java.util.Comparator.comparing(
                (SlimefunItem i) -> ChatColor.stripColor(i.getItemGroup().getDisplayName(player)))
                .thenComparing(i -> ChatColor.stripColor(i.getItemName())));
        return all;
    }

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
