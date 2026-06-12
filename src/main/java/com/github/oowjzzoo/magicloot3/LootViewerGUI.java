package com.github.oowjzzoo.magicloot3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;

/**
 * Read-only viewer GUI for /ml browse — SF loot, tools loot, enchantment limits, potion effect limits.
 */
final class LootViewerGUI extends LootGUI {

    private static final LootViewerGUI INSTANCE = new LootViewerGUI();
    private static final String[] ROMAN = {
        "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
        "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX",
        "XXI", "XXII", "XXIII", "XXIV", "XXV", "XXVI", "XXVII", "XXVIII", "XXIX", "XXX",
        "XXXI", "XXXII", "XXXIII", "XXXIV", "XXXV", "XXXVI", "XXXVII", "XXXVIII", "XXXIX", "XL",
        "XLI", "XLII", "XLIII", "XLIV", "XLV", "XLVI", "XLVII", "XLVIII", "XLIX", "L"
    };

    private static String roman(int n) {
        return n >= 1 && n <= ROMAN.length ? ROMAN[n - 1] : String.valueOf(n);
    }

    static void open(Player player, Plugin plugin) {
        PLUGIN = plugin;
        INSTANCE.openMainPage(player, 1);
    }

    @Override protected String configSection() { return "slimefun"; }
    @Override protected String itemName(String id) { return id; }
    @Override protected ItemStack itemIcon(String id) { return new ItemStack(Material.BARRIER); }

    // ── Main page ──

    @Override
    protected void openMainPage(Player player, int page) {
        var menu = newMenu(player, m("browse_title"));
        var sw = new HashSet<UUID>();

        addCategoryButton(menu, 19, Material.NETHER_STAR, m("sf_loot"), sw,
                () -> { READONLY.add(player.getUniqueId()); player.closeInventory(); SfLootGUI.open(player, PLUGIN); });
        addCategoryButton(menu, 21, Material.DIAMOND_SWORD, m("tools_loot"), sw,
                () -> { READONLY.add(player.getUniqueId()); player.closeInventory(); ToolsLootGUI.open(player, PLUGIN); });
        addCategoryButton(menu, 23, Material.ENCHANTED_BOOK, m("enchants"), sw,
                () -> openEnchants(player, 1));
        addCategoryButton(menu, 25, Material.POTION, m("effects"), sw,
                () -> openEffects(player, 1));

        addFooterBg(menu);
        finishMenu(menu, player, sw);
    }

    private void addCategoryButton(ChestMenu menu, int slot, Material mat, String name,
                                    Set<UUID> sw, Runnable action) {
        ItemStack icon = new ItemStack(mat);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        icon.setItemMeta(meta);
        menu.addItem(slot, icon);
        menu.addMenuClickHandler(slot, (pl, s, it, a) -> {
            sw.add(pl.getUniqueId());
            action.run();
            return false;
        });
    }

    // ── Enchantment limits viewer ──

    private void openEnchants(Player player, int page) {
        List<Enchantment> all = new ArrayList<>();
        for (Enchantment e : Enchantment.values()) {
            if (!e.isCursed() && MagicLootConfig.getMaxLevel(e) > 0) all.add(e);
        }
        all.sort(Comparator.comparing(e -> e.getKey().getKey()));

        int groupsPerPage = MAX_ITEMS;
        int totalGroups = Math.max(1, (all.size() + 7) / 8);
        int pages = Math.max(1, (totalGroups - 1) / groupsPerPage + 1);
        final int cur = Math.min(page, pages);

        var menu = newMenu(player, m("enchants"));
        var sw = new HashSet<UUID>();
        addBack(menu, 1, sw, () -> openMainPage(player, 1));

        int startGroup = groupsPerPage * (cur - 1);
        int slot = 9;
        for (int g = startGroup; g < totalGroups && slot < 45; g++, slot++) {
            int from = g * 8;
            int to = Math.min(all.size(), from + 8);
            if (from >= all.size()) break;
            menu.addItem(slot, buildEnchantGroup(all.subList(from, to), from, to));
            menu.addMenuClickHandler(slot, ChestMenuUtils.getEmptyClickHandler());
        }

        addFooterBg(menu);
        addPrevNext(menu, player, cur, pages, sw,
                () -> openEnchants(player, cur - 1),
                () -> openEnchants(player, cur + 1));
        finishMenu(menu, player, sw);
    }

    private ItemStack buildEnchantGroup(List<Enchantment> group, int from, int to) {
        ItemStack icon = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', m("enchant_group")));
        if (meta instanceof EnchantmentStorageMeta esm) {
            for (Enchantment e : group) {
                esm.addStoredEnchant(e, MagicLootConfig.getMaxLevel(e), true);
            }
        }
        icon.setItemMeta(meta);
        return icon;
    }

    // ── Potion effect limits viewer ──

    private void openEffects(Player player, int page) {
        List<PotionEffectType> all = new ArrayList<>();
        for (PotionEffectType e : PotionEffectType.values()) {
            if (e != null && MagicLootConfig.getMaxLevel(e) > 0) all.add(e);
        }
        all.sort(Comparator.comparing(e -> e.getKey().getKey()));

        int groupsPerPage = MAX_ITEMS;
        int totalGroups = Math.max(1, (all.size() + 7) / 8);
        int pages = Math.max(1, (totalGroups - 1) / groupsPerPage + 1);
        final int cur = Math.min(page, pages);

        var menu = newMenu(player, m("effects"));
        var sw = new HashSet<UUID>();
        addBack(menu, 1, sw, () -> openMainPage(player, 1));

        int startGroup = groupsPerPage * (cur - 1);
        int slot = 9;
        for (int g = startGroup; g < totalGroups && slot < 45; g++, slot++) {
            int from = g * 8;
            int to = Math.min(all.size(), from + 8);
            if (from >= all.size()) break;
            menu.addItem(slot, buildEffectGroup(all.subList(from, to), from, to));
            menu.addMenuClickHandler(slot, ChestMenuUtils.getEmptyClickHandler());
        }

        addFooterBg(menu);
        addPrevNext(menu, player, cur, pages, sw,
                () -> openEffects(player, cur - 1),
                () -> openEffects(player, cur + 1));
        finishMenu(menu, player, sw);
    }

    private ItemStack buildEffectGroup(List<PotionEffectType> group, int from, int to) {
        ItemStack icon = new ItemStack(Material.DRAGON_BREATH);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', m("effect_group")));

        List<String> lore = new ArrayList<>();
        for (PotionEffectType e : group) {
            int max = MagicLootConfig.getMaxLevel(e);
            String raw = Messages.raw("effects." + e.getKey().getKey());
            String name;
            if (raw != null && !raw.isEmpty()) {
                name = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', raw));
            } else {
                name = e.getKey().getKey().replace('_', ' ').toLowerCase();
            }
            lore.add(ChatColor.translateAlternateColorCodes('&',
                    m("effect_row", name, roman(max))));
        }
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    static String m(String key, Object... args) {
        String v = Messages.get("gui.viewer." + key, args);
        return v != null ? v : "???" + key;
    }
}
