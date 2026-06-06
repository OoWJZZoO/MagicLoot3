package com.github.oowjzzoo.magicloot3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Shared chat-input handler for weight configuration GUIs.
 * One static listener handles all concurrent GUIs.
 */
final class ChatHandler implements Listener {

    static final Map<UUID, Entry> PENDING = new HashMap<>();

    record Entry(LootConfigGUI gui, String itemId, Runnable reopen, int taskId) {}

    static void start(LootConfigGUI gui, Player player, String itemId, Runnable reopen) {
        Entry old = PENDING.remove(player.getUniqueId());
        if (old != null) Bukkit.getScheduler().cancelTask(old.taskId);
        int taskId = Bukkit.getScheduler().runTaskLater(LootConfigGUI.PLUGIN, () -> {
            Entry e = PENDING.remove(player.getUniqueId());
            if (e != null) {
                player.sendMessage(LootConfigGUI.m("input_timeout"));
                e.gui.saveConfig(player);
            }
        }, 600L).getTaskId();
        PENDING.put(player.getUniqueId(), new Entry(gui, itemId, reopen, taskId));
        player.sendMessage(LootConfigGUI.m("input_prompt", gui.itemName(itemId)));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Entry entry = PENDING.remove(e.getPlayer().getUniqueId());
        if (entry == null) return;
        e.setCancelled(true);
        Bukkit.getScheduler().cancelTask(entry.taskId);
        Bukkit.getScheduler().runTask(LootConfigGUI.PLUGIN, () -> {
            Player player = e.getPlayer();
            try {
                int w = Integer.parseInt(ChatColor.stripColor(e.getMessage()).trim());
                if (w < 1 || w > LootConfigGUI.WEIGHT_MAX) {
                    player.sendMessage(LootConfigGUI.m("invalid_range"));
                    entry.gui.saveConfig(player);
                    return;
                }
                Map<String, Integer> cache = LootConfigGUI.CACHES.get(player.getUniqueId());
                if (cache != null) cache.put(entry.itemId, w);
                player.sendMessage(LootConfigGUI.m("set_weight",
                        entry.gui.itemName(entry.itemId), String.valueOf(w)));
            } catch (NumberFormatException ex) {
                player.sendMessage(LootConfigGUI.m("invalid_number"));
                entry.gui.saveConfig(player);
                return;
            }
            entry.reopen.run();
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Entry entry = PENDING.remove(e.getPlayer().getUniqueId());
        if (entry != null) {
            Bukkit.getScheduler().cancelTask(entry.taskId);
            entry.gui.saveConfig(e.getPlayer());
        }
    }
}
