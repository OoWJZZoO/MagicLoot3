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
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;

/**
 * Abstract base for /ml sf_loot and /ml tools_loot weight-config GUIs.
 */
abstract class LootGUI {

    static final int MAX_ITEMS = 36;
    static final int WEIGHT_MAX = 999999;
    static final Map<UUID, Map<String, Integer>> CACHES = new HashMap<>();
    static final Set<UUID> READONLY = new HashSet<>();
    static Plugin PLUGIN;

    static boolean isReadonly(Player p) { return READONLY.contains(p.getUniqueId()); }

    // ── Subclass contract ──

    protected abstract String configSection();
    protected abstract String itemName(String id);
    protected abstract ItemStack itemIcon(String id);
    protected abstract void openMainPage(Player player, int page);

    // ── Config ──

    Map<String, Integer> loadConfig() {
        Map<String, Integer> map = new HashMap<>();
        ConfigManager cfg = MagicLootConfig.getConfig(ConfigType.ITEMS);
        if (cfg != null) {
            var sec = cfg.getYaml().getConfigurationSection(configSection());
            if (sec != null)
                for (String id : sec.getKeys(false))
                    map.put(id, cfg.getInt(configSection() + "." + id, 0));
        }
        return map;
    }

    void saveConfig(Player player) {
        Map<String, Integer> cache = CACHES.remove(player.getUniqueId());
        if (cache == null) return;
        ConfigManager cfg = new ConfigManager(new File(PLUGIN.getDataFolder(), "Items.yml"));
        for (var e : cache.entrySet())
            cfg.getYaml().set(configSection() + "." + e.getKey(), e.getValue());
        cfg.save();
        MagicLoot3.reload(PLUGIN);
        player.sendMessage(m("saved"));
    }

    // ── UI builders ──

    /** @param subtitle already-localized subtitle (caller calls {@link #m} if needed) */
    ChestMenu newMenu(Player player, String subtitle) {
        ChestMenu m = new ChestMenu(m("title") + " - " + subtitle);
        m.setEmptySlotsClickable(false);
        for (int i = 0; i < 9; i++)
            m.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        return m;
    }

    /** Add close handler, then open. Call {@link #addFooterBg} before {@link #addPrevNext}. */
    void finishMenu(ChestMenu menu, Player player, Set<UUID> switching) {
        menu.addMenuCloseHandler(pl -> {
            if (!switching.remove(pl.getUniqueId())) trySave(pl);
        });
        menu.open(player);
    }

    /** Add footer backgrounds to all slots 45-53. */
    void addFooterBg(ChestMenu menu) {
        for (int i = 45; i < 54; i++)
            menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
    }

    void addBack(ChestMenu menu, int slot, Set<UUID> switching, Runnable action) {
        ItemStack btn = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = btn.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&7⇦ " + m("back")));
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.setCustomModelData(2200003);
        meta.getPersistentDataContainer().set(
                new NamespacedKey("slimefun", "slimefun_item"),
                PersistentDataType.STRING, "_UI_BACK");
        btn.setItemMeta(meta);
        menu.addItem(slot, btn);
        menu.addMenuClickHandler(slot, (pl, s, it, a) -> { switching.add(pl.getUniqueId()); action.run(); return false; });
    }

    void addPrevNext(ChestMenu menu, Player player, int cur, int pages, Set<UUID> switching,
                      Runnable prev, Runnable next) {
        if (cur > 1) {
            menu.addItem(46, ChestMenuUtils.getPreviousButton(player, cur, pages));
            menu.addMenuClickHandler(46, (pl, s, it, a) -> { switching.add(pl.getUniqueId()); prev.run(); return false; });
        }
        if (cur < pages) {
            menu.addItem(52, ChestMenuUtils.getNextButton(player, cur, pages));
            menu.addMenuClickHandler(52, (pl, s, it, a) -> { switching.add(pl.getUniqueId()); next.run(); return false; });
        }
    }

    // ── Display ──

    ItemStack buildItem(String id, int weight, int totalWeight, Player player) {
        ItemStack icon = itemIcon(id);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "───────────────");
        if (weight == -1) {
            lore.add(ChatColor.translateAlternateColorCodes('&', m("multiblock")));
        } else {
            if (weight > 0) {
                String prob = totalWeight > 0
                        ? formatProbability(100.0 * weight / totalWeight) : "0";
                lore.add(ChatColor.translateAlternateColorCodes('&',
                        m("enabled", String.valueOf(weight), prob)));
            } else {
                lore.add(ChatColor.translateAlternateColorCodes('&', m("disabled")));
            }
            if (player == null || !isReadonly(player)) {
                lore.add(ChatColor.translateAlternateColorCodes('&', m("help")));
            }
        }
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    /** Formats a probability as a percentage with 2 significant digits, never scientific notation. */
    static String formatProbability(double pct) {
        if (pct <= 0) return "0";
        if (pct >= 100) return String.format("%.0f", pct);
        int digits = 1 - (int) Math.floor(Math.log10(pct));
        digits = Math.max(0, Math.min(digits, 6));
        return String.format("%." + digits + "f", pct);
    }

    int computeTotal(Map<String, Integer> cache) {
        int t = 0;
        for (int w : cache.values()) if (w > 0) t += w;
        return t;
    }

    // ── Click handler ──

    ChestMenu.MenuClickHandler makeClickHandler(Player player, String id, Set<UUID> switching,
                                                  Runnable refresh) {
        return (pl, slot, item, action) -> {
            if (isReadonly(pl)) return false;
            Map<String, Integer> cache = CACHES.get(pl.getUniqueId());
            if (cache == null) return false;
            if (cache.getOrDefault(id, 0) == -1) return false;
            if (action.isShiftClicked()) {
                ChatHandler.start(this, pl, id, refresh);
                pl.closeInventory();
                return false;
            }
            cache.put(id, action.isRightClicked() ? 0 : 100);
            switching.add(pl.getUniqueId());
            refresh.run();
            return false;
        };
    }

    // ── Close ──

    void trySave(Player player) {
        if (READONLY.remove(player.getUniqueId())) return;
        if (ChatHandler.PENDING.containsKey(player.getUniqueId())) return;
        if (CACHES.containsKey(player.getUniqueId())) saveConfig(player);
    }

    // ── Messages ──

    static String m(String key, Object... args) {
        String v = Messages.get("gui.loot_config." + key, args);
        return v != null ? v : "???" + key;
    }
}
