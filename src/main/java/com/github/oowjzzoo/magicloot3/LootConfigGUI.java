package com.github.oowjzzoo.magicloot3;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.groups.FlexItemGroup;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;

import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;

public final class LootConfigGUI {

    private static final String TITLE = ChatColor.DARK_GREEN + "战利品配置";
    private static final int MAX_ITEMS = 36;

    private static final Map<UUID, Map<String, Integer>> caches = new HashMap<>();
    private static final Map<UUID, PendingInput> pending = new HashMap<>();
    private static final Set<UUID> switching = new HashSet<>();
    private static Plugin plugin;
    private static ChatListener chatListener;

    private record PendingInput(String itemId, ItemGroup group, int page, int taskId) {}

    private LootConfigGUI() {}

    // ── Entry point ──

    public static void open(Player player, Plugin pl) {
        plugin = pl;
        caches.put(player.getUniqueId(), loadFromConfig(pl));
        openMainMenu(player, 1);
    }

    // ── Config load / save ──

    private static Map<String, Integer> loadFromConfig(Plugin pl) {
        Map<String, Integer> map = new HashMap<>();
        ConfigManager cfg = MagicLootConfig.getConfig(ConfigType.ITEMS);
        if (cfg != null) {
            var sec = cfg.getYaml().getConfigurationSection("slimefun");
            if (sec != null) {
                for (String id : sec.getKeys(false)) {
                    map.put(id, cfg.getInt("slimefun." + id, 0));
                }
            }
        }
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

    // ── Main menu (category grid, matching /sf cheat layout) ──

    private static void openMainMenu(Player player, int page) {
        Map<String, Integer> cache = caches.get(player.getUniqueId());
        if (cache == null) return;

        final List<ItemGroup> groups = getVisibleGroups(player);
        final int pages = Math.max(1, (groups.size() - 1) / MAX_ITEMS + 1);
        final int cur = Math.min(page, pages);

        ChestMenu menu = new ChestMenu(TITLE + " - 分类");
        menu.setEmptySlotsClickable(false);

        // Header row — matching SF's createHeader layout
        for (int i = 0; i < 9; i++) {
            menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }
        // slot 1: empty (SF puts settings button here, we don't have one)
        menu.addItem(1, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());

        int start = MAX_ITEMS * (cur - 1);
        int slot = 9;
        for (int i = start; i < groups.size() && slot < 45; i++, slot++) {
            ItemGroup group = groups.get(i);
            menu.addItem(slot, group.getItem(player));
            final int idx = i;
            menu.addMenuClickHandler(slot, (pl, s, item, action) -> {
                switching.add(pl.getUniqueId());
                openCategory(pl, groups.get(idx), 1);
                return false;
            });
        }

        // Footer row — matching SF pagination
        for (int i = 45; i < 54; i++) {
            menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }
        if (cur > 1) {
            menu.addItem(46, ChestMenuUtils.getPreviousButton(player, cur, pages));
            menu.addMenuClickHandler(46, (pl, s, it, action) -> {
                switching.add(pl.getUniqueId());
                openMainMenu(pl, cur - 1);
                return false;
            });
        }
        if (cur < pages) {
            menu.addItem(52, ChestMenuUtils.getNextButton(player, cur, pages));
            menu.addMenuClickHandler(52, (pl, s, it, action) -> {
                switching.add(pl.getUniqueId());
                openMainMenu(pl, cur + 1);
                return false;
            });
        }

        menu.addMenuCloseHandler(pl -> {
            if (!switching.remove(pl.getUniqueId())) trySave(pl);
        });
        menu.open(player);
    }

    // ── Category menu (items, matching SF openItemGroup layout) ──

    private static void openCategory(Player player, ItemGroup group, int page) {
        Map<String, Integer> cache = caches.get(player.getUniqueId());
        if (cache == null) return;

        if (group instanceof FlexItemGroup) return; // flex groups have custom UI

        final List<SlimefunItem> items = new ArrayList<>(group.getItems());
        items.removeIf(i -> i.isDisabledIn(player.getWorld()));
        final int pages = Math.max(1, (items.size() - 1) / MAX_ITEMS + 1);
        final int cur = Math.min(page, pages);

        String title = TITLE + " - " + ChatColor.stripColor(group.getDisplayName(player));
        ChestMenu menu = new ChestMenu(title);
        menu.setEmptySlotsClickable(false);

        // Header row — matching SF layout
        for (int i = 0; i < 9; i++) {
            menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }
        // Back button at slot 0 — matching SF's addBackButton pattern
        menu.addItem(0, ChestMenuUtils.getPreviousButton(player, 1, 1));
        menu.addMenuClickHandler(0, (pl, s, it, action) -> {
            switching.add(pl.getUniqueId());
            openMainMenu(pl, 1);
            return false;
        });

        int start = MAX_ITEMS * (cur - 1);
        int slot = 9;
        for (int i = start; i < items.size() && slot < 45; i++, slot++) {
            SlimefunItem sfItem = items.get(i);
            int weight = cache.getOrDefault(sfItem.getId(), 0);
            ItemStack display = buildDisplayItem(sfItem, weight);
            menu.addItem(slot, display);
            final int itemPage = cur;
            menu.addMenuClickHandler(slot, makeHandler(player, sfItem, group, itemPage));
        }

        // Footer — matching SF pagination
        for (int i = 45; i < 54; i++) {
            menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }
        if (cur > 1) {
            menu.addItem(46, ChestMenuUtils.getPreviousButton(player, cur, pages));
            menu.addMenuClickHandler(46, (pl, s, it, action) -> {
                switching.add(pl.getUniqueId());
                openCategory(pl, group, cur - 1);
                return false;
            });
        }
        if (cur < pages) {
            menu.addItem(52, ChestMenuUtils.getNextButton(player, cur, pages));
            menu.addMenuClickHandler(52, (pl, s, it, action) -> {
                switching.add(pl.getUniqueId());
                openCategory(pl, group, cur + 1);
                return false;
            });
        }

        menu.addMenuCloseHandler(pl -> {
            if (!switching.remove(pl.getUniqueId())) trySave(pl);
        });
        menu.open(player);
    }

    // ── Click handler ──

    private static ChestMenu.MenuClickHandler makeHandler(Player player, SlimefunItem sfItem,
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

            cache.put(id, action.isRightClicked() ? 0 : 10);
            switching.add(pl.getUniqueId());
            openCategory(pl, group, page);
            return false;
        };
    }

    // ── Chat input ──

    private static void startPendingInput(Player player, String itemId, ItemGroup group, int page) {
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingInput pi = pending.remove(player.getUniqueId());
            if (pi != null) {
                player.sendMessage(ChatColor.RED + "输入超时，配置已保存。待设置的物品未修改。");
                saveAndReload(player);
            }
        }, 600L).getTaskId();

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
            try {
                int w = Integer.parseInt(ChatColor.stripColor(e.getMessage()).trim());
                if (w < 1 || w > 999) {
                    player.sendMessage(ChatColor.RED + "权重必须在 1-999 之间。配置已保存。");
                    saveAndReload(player);
                    return;
                }
                Map<String, Integer> cache = caches.get(player.getUniqueId());
                if (cache != null) cache.put(pi.itemId, w);
                player.sendMessage(ChatColor.GREEN + "已将 " + sfName(pi.itemId) +
                        ChatColor.GREEN + " 设为 " + w + "。");
            } catch (NumberFormatException ex) {
                player.sendMessage(ChatColor.RED + "无效数字。配置已保存。");
                saveAndReload(player);
                return;
            }

            switching.add(player.getUniqueId());
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
        lore.add(weight > 0
                ? ChatColor.GREEN + "✔ 已启用（权重: " + weight + "）"
                : ChatColor.RED + "✘ 已禁用");
        lore.add(ChatColor.GRAY + "左键启用(10) | 右键禁用(0) | Shift+点击自定义");
        meta.setLore(lore);
        original.setItemMeta(meta);
        return original;
    }

    // ── Close handling ──

    private static void trySave(Player player) {
        if (pending.containsKey(player.getUniqueId())) return;
        if (caches.containsKey(player.getUniqueId())) {
            saveAndReload(player);
        }
    }

    // ── Visible groups (matching CheatSheetSlimefunGuide logic) ──

    private static List<ItemGroup> getVisibleGroups(Player player) {
        List<ItemGroup> groups = new ArrayList<>();
        for (ItemGroup group : Slimefun.getRegistry().getAllItemGroups()) {
            if (group instanceof FlexItemGroup flex) {
                if (flex.isVisible(player, null, SlimefunGuideMode.CHEAT_MODE)) {
                    groups.add(group);
                }
            } else if (!group.isHidden(player) && !group.getItems().isEmpty()) {
                groups.add(group);
            }
        }
        return groups;
    }
}
