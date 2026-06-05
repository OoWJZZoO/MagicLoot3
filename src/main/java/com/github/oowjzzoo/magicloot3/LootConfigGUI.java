package com.github.oowjzzoo.magicloot3;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.groups.FlexItemGroup;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;

import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;

public final class LootConfigGUI {

    private static final String TITLE_PREFIX = ChatColor.DARK_GREEN + "战利品配置";
    private static final int MAX_ITEMS = 36;

    // Per-player cache: player UUID → (itemId → weight)
    private static final Map<UUID, Map<String, Integer>> caches = new HashMap<>();
    // Players waiting for custom weight input: UUID → {itemId, pageInfo}
    private static final Map<UUID, PendingInput> pending = new HashMap<>();
    private static Plugin plugin;
    private static ChatListener chatListener;

    private record PendingInput(String itemId, ItemGroup group, int page, int taskId) {}

    private LootConfigGUI() {}

    // ── Entry point ──

    public static void open(Player player, Plugin pl) {
        plugin = pl;
        Map<String, Integer> cache = loadFromConfig(pl);
        caches.put(player.getUniqueId(), cache);
        openMainMenu(player, 1);
    }

    // ── Config load / save ──

    private static Map<String, Integer> loadFromConfig(Plugin pl) {
        Map<String, Integer> map = new HashMap<>();
        ConfigManager cfg = MagicLootConfig.getConfig(ConfigType.ITEMS);
        if (cfg == null) return map;
        var sec = cfg.getYaml().getConfigurationSection("slimefun");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                map.put(id, cfg.getInt("slimefun." + id, 0));
            }
        }
        // Fill in unconfigured items with default 10
        if (Bukkit.getPluginManager().isPluginEnabled("Slimefun")) {
            for (SlimefunItem item : Slimefun.getRegistry().getAllSlimefunItems()) {
                map.putIfAbsent(item.getId(), 10);
            }
        }
        return map;
    }

    private static void saveAndReload(Player player) {
        Map<String, Integer> cache = caches.remove(player.getUniqueId());
        if (cache == null) return;
        ConfigManager cfg = new ConfigManager(new File(plugin.getDataFolder(), "Items.yml"));
        for (var e : cache.entrySet()) {
            cfg.getYaml().set("slimefun." + e.getKey(), e.getValue());
        }
        cfg.save();
        MagicLoot3.reload(plugin);
        player.sendMessage(ChatColor.GREEN + "战利品配置已保存并重载。");
    }

    // ── Main menu (category grid) ──

    private static void openMainMenu(Player player, int page) {
        Map<String, Integer> cache = caches.get(player.getUniqueId());
        if (cache == null) return;

        ChestMenu menu = new ChestMenu(TITLE_PREFIX + " - 分类");
        menu.setEmptySlotsClickable(false);

        List<ItemGroup> groups = getVisibleGroups(player);
        int pages = Math.max(1, (groups.size() - 1) / MAX_ITEMS + 1);
        if (page > pages) page = pages;

        for (int i = 0; i < 9; i++) {
            menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }
        menu.addItem(4, titleItem("&6← 点击分类进入 →"), ChestMenuUtils.getEmptyClickHandler());

        int start = MAX_ITEMS * (page - 1);
        int slot = 9;
        for (int i = start; i < groups.size() && slot < 45; i++, slot++) {
            ItemGroup group = groups.get(i);
            menu.addItem(slot, group.getItem(player));
            int groupIdx = i;
            menu.addMenuClickHandler(slot, (pl, s, item, action) -> {
                openCategory(pl, groups.get(groupIdx), 1);
                return false;
            });
        }

        addPagination(menu, page, pages, player, false, null);
        menu.addMenuCloseHandler(pl -> trySave(pl));
        menu.open(player);
    }

    // ── Category menu (item grid) ──

    private static void openCategory(Player player, ItemGroup group, int page) {
        Map<String, Integer> cache = caches.get(player.getUniqueId());
        if (cache == null) return;
        // Delegate to FlexItemGroup if needed
        if (group instanceof FlexItemGroup flex) {
            // Flex groups manage their own UI — skip for now
            return;
        }

        List<SlimefunItem> items = new ArrayList<>(group.getItems());
        items.removeIf(i -> i.isDisabledIn(player.getWorld()));
        int pages = Math.max(1, (items.size() - 1) / MAX_ITEMS + 1);
        if (page > pages) page = pages;

        ChestMenu menu = new ChestMenu(TITLE_PREFIX + " - " +
                ChatColor.stripColor(group.getDisplayName(player)));
        menu.setEmptySlotsClickable(false);

        // Header
        for (int i = 0; i < 9; i++) {
            menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }
        // Back button
        menu.addItem(0, ChestMenuUtils.getPreviousButton(player, 1, 1), (pl, s, it, action) -> {
            openMainMenu(pl, 1);
            return false;
        });
        // Title
        menu.addItem(4, titleItem("&6" + ChatColor.stripColor(group.getDisplayName(player))),
                ChestMenuUtils.getEmptyClickHandler());

        int start = MAX_ITEMS * (page - 1);
        int slot = 9;
        for (int i = start; i < items.size() && slot < 45; i++, slot++) {
            SlimefunItem sfItem = items.get(i);
            int weight = cache.getOrDefault(sfItem.getId(), 0);
            ItemStack display = buildDisplayItem(sfItem, weight);
            menu.addItem(slot, display);
            menu.addMenuClickHandler(slot, makeClickHandler(player, sfItem, group, page));
        }

        addPagination(menu, page, pages, player, true, group);
        menu.addMenuCloseHandler(pl -> trySave(pl));
        menu.open(player);
    }

    // ── Click handler ──

    private static ChestMenu.MenuClickHandler makeClickHandler(Player player, SlimefunItem sfItem,
                                                                ItemGroup group, int page) {
        return (pl, slot, item, action) -> {
            Map<String, Integer> cache = caches.get(pl.getUniqueId());
            if (cache == null) return false;
            String id = sfItem.getId();

            if (action.isShiftClicked()) {
                pl.closeInventory();
                startPendingInput(pl, id, group, page);
                return false;
            }

            if (action.isRightClicked()) {
                cache.put(id, 0);
            } else {
                cache.put(id, 10);
            }

            openCategory(pl, group, page);
            return false;
        };
    }

    // ── Chat input for custom weight ──

    private static void startPendingInput(Player player, String itemId, ItemGroup group, int page) {
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingInput pi = pending.remove(player.getUniqueId());
            if (pi != null) {
                player.sendMessage(ChatColor.RED + "输入超时，配置已保存。待设置的物品未修改。");
                saveAndReload(player);
            }
        }, 600L).getTaskId(); // 30 seconds

        pending.put(player.getUniqueId(), new PendingInput(itemId, group, page, taskId));
        ensureChatListener();
        player.sendMessage(ChatColor.YELLOW + "请在聊天栏输入 " + sfName(itemId) +
                ChatColor.YELLOW + " 的权重 (1-999)：");
    }

    private static void ensureChatListener() {
        if (chatListener == null) {
            chatListener = new ChatListener();
            Bukkit.getPluginManager().registerEvents(chatListener, plugin);
        }
    }

    private static String sfName(String id) {
        SlimefunItem s = SlimefunItem.getById(id);
        return s != null ? s.getItemName() : id;
    }

    static class ChatListener implements Listener {
        @EventHandler
        public void onChat(AsyncPlayerChatEvent e) {
            PendingInput pi = pending.remove(e.getPlayer().getUniqueId());
            if (pi == null) return;
            e.setCancelled(true);
            Bukkit.getScheduler().cancelTask(pi.taskId);

            Player player = e.getPlayer();
            String msg = ChatColor.stripColor(e.getMessage()).trim();

            try {
                int weight = Integer.parseInt(msg);
                if (weight < 1 || weight > 999) {
                    player.sendMessage(ChatColor.RED + "权重必须在 1-999 之间。配置已保存。待设置的物品未修改。");
                    saveAndReload(player);
                    return;
                }
                Map<String, Integer> cache = caches.get(player.getUniqueId());
                if (cache != null) cache.put(pi.itemId, weight);
                player.sendMessage(ChatColor.GREEN + "已将 " + sfName(pi.itemId) +
                        ChatColor.GREEN + " 设为 " + weight + "。");
            } catch (NumberFormatException ex) {
                player.sendMessage(ChatColor.RED + "无效数字。配置已保存。待设置的物品未修改。");
                saveAndReload(player);
                return;
            }

            // Reopen GUI
            openCategory(player, pi.group, pi.page);
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent e) {
            PendingInput pi = pending.remove(e.getPlayer().getUniqueId());
            if (pi != null) {
                Bukkit.getScheduler().cancelTask(pi.taskId);
                saveAndReload(e.getPlayer());
            }
        }
    }

    // ── Item display ──

    private static ItemStack buildDisplayItem(SlimefunItem sfItem, int weight) {
        ItemStack original = sfItem.getItem().clone();
        ItemMeta meta = original.getItemMeta();
        if (meta == null) return original;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "───────────────");
        if (weight > 0) {
            lore.add(ChatColor.GREEN + "✔ 已启用（权重: " + weight + "）");
        } else {
            lore.add(ChatColor.RED + "✘ 已禁用");
        }
        lore.add(ChatColor.GRAY + "左键启用(10) | 右键禁用(0) | Shift+点击自定义");
        meta.setLore(lore);
        original.setItemMeta(meta);
        return original;
    }

    private static ItemStack titleItem(String name) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        item.setItemMeta(meta);
        return item;
    }

    // ── Pagination ──

    private static void addPagination(ChestMenu menu, int page, int pages, Player player,
                                       boolean hasBack, ItemGroup currentGroup) {
        for (int i = 45; i < 54; i++) {
            menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }
        if (page > 1) {
            menu.addItem(46, ChestMenuUtils.getPreviousButton(player, page, pages),
                    (pl, s, it, action) -> {
                        int prev = page - 1;
                        if (hasBack) openCategory(pl, currentGroup, prev);
                        else openMainMenu(pl, prev);
                        return false;
                    });
        }
        if (page < pages) {
            menu.addItem(52, ChestMenuUtils.getNextButton(player, page, pages),
                    (pl, s, it, action) -> {
                        int next = page + 1;
                        if (hasBack) openCategory(pl, currentGroup, next);
                        else openMainMenu(pl, next);
                        return false;
                    });
        }
    }

    // ── Close handling ──

    private static void trySave(Player player) {
        // Don't save if waiting for chat input (will save then)
        if (pending.containsKey(player.getUniqueId())) return;
        if (caches.containsKey(player.getUniqueId())) {
            saveAndReload(player);
        }
    }

    // ── Visible groups (same logic as CheatSheetSlimefunGuide) ──

    private static List<ItemGroup> getVisibleGroups(Player player) {
        List<ItemGroup> groups = new ArrayList<>();
        for (ItemGroup group : Slimefun.getRegistry().getAllItemGroups()) {
            if (group instanceof FlexItemGroup flex) {
                if (flex.isVisible(player, null,
                        io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode.CHEAT_MODE)) {
                    groups.add(group);
                }
            } else if (!group.isHidden(player) && !group.getItems().isEmpty()) {
                groups.add(group);
            }
        }
        return groups;
    }
}
