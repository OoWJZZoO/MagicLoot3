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
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

/**
 * Slimefun loot weight config GUI — /ml sf_loot.
 * Flat list of all visible SF items, exactly matching /sf cheat coverage.
 */
final class SfLootGUI extends LootConfigGUI {

    private static final SfLootGUI INSTANCE = new SfLootGUI();
    private static ChatHandler chatListener;
    /** All visible SF items, sorted by group name then item name. */
    private static List<SlimefunItem> ALL_ITEMS;

    static void open(Player player, Plugin plugin) {
        PLUGIN = plugin;
        CACHES.put(player.getUniqueId(), INSTANCE.loadConfig());
        if (chatListener == null) {
            chatListener = new ChatHandler();
            Bukkit.getPluginManager().registerEvents(chatListener, plugin);
        }
        allItems(player);
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
        if (cache == null || ALL_ITEMS == null) return;

        int pages = Math.max(1, (ALL_ITEMS.size() - 1) / MAX_ITEMS + 1);
        final int cur = Math.min(page, pages);

        var menu = newMenu(player, "categories");
        var sw = new HashSet<UUID>();
        int tw = computeTotal(cache);

        int start = MAX_ITEMS * (cur - 1);
        int slot = 9;
        for (int i = start; i < ALL_ITEMS.size() && slot < 45; i++, slot++) {
            SlimefunItem sfItem = ALL_ITEMS.get(i);
            String id = sfItem.getId();
            int weight = cache.getOrDefault(id, 0);
            // Append group name to display
            ItemStack icon = buildItem(id, weight, tw);
            addGroupLine(icon, sfItem.getItemGroup(), player);
            menu.addItem(slot, icon);
            final int pageSnap = cur;
            menu.addMenuClickHandler(slot, makeClickHandler(player, id, sw,
                    () -> openMainPage(player, pageSnap)));
        }

        addPrevNext(menu, player, cur, pages, sw,
                () -> openMainPage(player, cur - 1),
                () -> openMainPage(player, cur + 1));
        finishMenu(menu, player, sw);
    }

    /** Append the group name to the lore of a display item. */
    private static void addGroupLine(ItemStack icon, ItemGroup group, Player player) {
        var meta = icon.getItemMeta();
        if (meta == null) return;
        var lore = new ArrayList<>(meta.getLore());
        lore.add(ChatColor.DARK_GRAY + "   " + group.getDisplayName(player));
        meta.setLore(lore);
        icon.setItemMeta(meta);
    }

    /** Build sorted list of all Slimefun items from all visible groups. */
    private static void allItems(Player player) {
        List<SlimefunItem> items = new ArrayList<>();
        for (ItemGroup group : Slimefun.getRegistry().getAllItemGroups()) {
            if (group instanceof FlexItemGroup flex) {
                if (!flex.isVisible(player, null, SlimefunGuideMode.CHEAT_MODE))
                    continue;
                // Flex groups don't expose items — they own sub-groups
                continue;
            }
            if (group.isHidden(player) || group.getItems().isEmpty()) continue;
            for (SlimefunItem item : group.getItems()) {
                if (!item.isDisabledIn(player.getWorld()))
                    items.add(item);
            }
        }
        items.sort(Comparator.comparing(
                (SlimefunItem i) -> strip(i.getItemGroup().getDisplayName(player))
        ).thenComparing(i -> strip(i.getItemName())));
        ALL_ITEMS = items;
    }

    private static String strip(String s) { return ChatColor.stripColor(s); }
}
