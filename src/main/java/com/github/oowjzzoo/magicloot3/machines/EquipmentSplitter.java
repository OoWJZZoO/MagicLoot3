package com.github.oowjzzoo.magicloot3.machines;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.github.oowjzzoo.magicloot3.ItemKeys;
import com.github.oowjzzoo.magicloot3.ItemManager;
import com.github.oowjzzoo.magicloot3.MagicLoot3;
import com.github.oowjzzoo.magicloot3.Messages;
import com.github.oowjzzoo.magicloot3.machines.AffixTransferUtil.EffectEntry;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.interfaces.InventoryBlock;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;

public class EquipmentSplitter extends SlimefunItem implements InventoryBlock {

    enum Route { A, B, C }
    enum Priority { DESTROY_FIRST, OUTPUT_FIRST }

    /** Per-machine persisted + transient state, replacing 8 separate maps. */
    static class SplitterState {
        final Map<String, Route> routes = new HashMap<>();
        Priority priority = Priority.DESTROY_FIRST;
        boolean enchProtectOn;
        int enchProtectThresh = 5;
        Map<String, Route> dirtyRoutes;   // non-null = sub-menu is open
        Priority dirtyPriority;
        long lastShiftTime;
    }

    private static final Map<Location, SplitterState> states = new ConcurrentHashMap<>();

    // 1-based slots from user spec, converted to 0-based
    // Input: 38 → 37, Output: 44 → 43
    private static final int INPUT_SLOT = 37;
    private static final int OUTPUT_SLOT = 43;
    // Menu: 11 → 10, Priority: 20 → 19, Sign(B): 17 → 16, Trash(C): 26 → 25
    // EnchProtect: 23 → 22, Bell: 5 → 4
    private static final int MENU_SLOT = 10;
    private static final int PRIO_SLOT = 19;
    private static final int SIGN_SLOT = 16;
    private static final int TRASH_SLOT = 25;
    private static final int ENCH_PROTECT_SLOT = 22;
    private static final int BELL_SLOT = 4;

    private static final int SAVE_SLOT = 4; // 1-based slot 5
    private static final int MAX_ITEMS = 36;

    // Cyan frame: 28,29,30,37,39,46,47,48 → 0-based
    private static final int[] CYAN_SLOTS  = {27, 28, 29, 36, 38, 45, 46, 47};
    // Orange frame: 34,35,36,43,45,52,53,54 → 0-based
    private static final int[] ORANGE_SLOTS = {33, 34, 35, 42, 44, 51, 52, 53};

    private static SplitterState state(Location loc) {
        return states.computeIfAbsent(loc, k -> new SplitterState());
    }

    /** Drop dirty editing state for offline players. Called by Housekeeper. */
    public static int cleanupStaleStates() {
        int removed = 0;
        var it = states.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getValue().dirtyRoutes != null) {
                // Only keep dirty state if the world chunk is still loaded
                if (!e.getKey().isWorldLoaded() || !e.getKey().isChunkLoaded()) {
                    e.getValue().dirtyRoutes = null;
                    e.getValue().dirtyPriority = null;
                    removed++;
                }
            }
        }
        return removed;
    }

    private static Plugin plugin;

    public EquipmentSplitter(ItemGroup itemGroup, SlimefunItemStack item,
                              RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemHandler(onBlockBreak());
        addItemHandler(onBlockPlace());
        addItemHandler(new BlockTicker() {
            @Override public boolean isSynchronized() { return true; }
            @Override
            public void tick(Block b, SlimefunItem item, Config data) {
                process(b);
            }
        });
    }

    @Override
    public void postRegister() {
        new BlockMenuPreset(getId(), getItemName()) {
            @Override public void init() {
                var empty = ChestMenuUtils.getEmptyClickHandler();
                // Gray background: all slots not otherwise used
                Set<Integer> used = new HashSet<>();
                used.add(INPUT_SLOT); used.add(OUTPUT_SLOT);
                used.add(MENU_SLOT); used.add(PRIO_SLOT);
                used.add(SIGN_SLOT); used.add(TRASH_SLOT);
                used.add(ENCH_PROTECT_SLOT); used.add(BELL_SLOT);
                for (int i : CYAN_SLOTS) used.add(i);
                for (int i : ORANGE_SLOTS) used.add(i);
                for (int i = 0; i < 54; i++)
                    if (!used.contains(i)) addItem(i, ChestMenuUtils.getBackground(), empty);

                // Cyan frame
                ItemStack cyanPane = stainedPane(Material.CYAN_STAINED_GLASS_PANE);
                for (int i : CYAN_SLOTS) addItem(i, cyanPane, empty);
                // Orange frame
                ItemStack orangePane = stainedPane(Material.ORANGE_STAINED_GLASS_PANE);
                for (int i : ORANGE_SLOTS) addItem(i, orangePane, empty);

                // I/O slots are framed by cyan/orange; no extra texture needed
            }

            @Override
            public void newInstance(@Nonnull BlockMenu menu, @Nonnull Block b) {
                refreshMainPage(menu, b);
            }

            @Override
            public boolean canOpen(@Nonnull Block b, @Nonnull Player p) {
                return true;
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return flow == ItemTransportFlow.INSERT
                        ? new int[]{INPUT_SLOT}
                        : new int[]{OUTPUT_SLOT};
            }
        };
    }

    @Nonnull
    private BlockBreakHandler onBlockBreak() {
        return new SimpleBlockBreakHandler() {
            @Override
            public void onBlockBreak(@Nonnull Block b) {
                BlockMenu inv = BlockStorage.getInventory(b);
                if (inv != null) {
                    dropIfPresent(b, inv, INPUT_SLOT);
                    dropIfPresent(b, inv, OUTPUT_SLOT);
                }
                Location loc = b.getLocation();
                states.remove(loc);
                // Clean up stale entry in YAML file
                deleteLocation(loc);
            }
        };
    }

    @Nonnull
    private BlockPlaceHandler onBlockPlace() {
        return new BlockPlaceHandler(false) {
            @Override
            public void onPlayerPlace(@Nonnull BlockPlaceEvent e) {
                Location loc = e.getBlock().getLocation();
                loadLocation(loc);
            }
        };
    }

    // ── Ticker ──

    private void process(Block b) {
        BlockMenu inv = BlockStorage.getInventory(b);
        if (inv == null) return;
        ItemStack item = inv.getItemInSlot(INPUT_SLOT);
        if (item == null || item.getType().isAir()) return;

        Location loc = b.getLocation();
        Map<String, Route> routes = state(loc).routes;
        Priority prio = state(loc).priority;

        // Determine route
        Route finalRoute = Route.A;
        String pdcData = item.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.EFFECTS, PersistentDataType.STRING);
        if (pdcData != null && !pdcData.isEmpty()) {
            List<EffectEntry> effects = AffixTransferUtil.parseEffects(pdcData);
            for (EffectEntry e : effects) {
                String key = e.effectKey() + ":" + (e.positive() ? "+" : "-");
                Route r = routes.get(key);
                if (r == null) continue;
                if (prio == Priority.DESTROY_FIRST) {
                    if (r == Route.C) { finalRoute = Route.C; break; }
                    if (r == Route.B) finalRoute = Route.B;
                } else {
                    if (r == Route.B) { finalRoute = Route.B; break; }
                    if (r == Route.C) finalRoute = Route.C;
                }
            }
        }

        // Enchant protection overrides affix routing
        if (state(loc).enchProtectOn) {
            int threshold = state(loc).enchProtectThresh;
            for (Enchantment ench : item.getEnchantments().keySet()) {
                if (item.getEnchantmentLevel(ench) >= threshold) {
                    finalRoute = Route.B;
                    break;
                }
            }
        }

        switch (finalRoute) {
            case B -> {
                ItemStack existing = inv.getItemInSlot(OUTPUT_SLOT);
                if (existing != null && !existing.getType().isAir()) return; // blocked
                inv.replaceExistingItem(INPUT_SLOT, null);
                inv.pushItem(item, OUTPUT_SLOT);
            }
            case C -> inv.replaceExistingItem(INPUT_SLOT, null);
            default -> {
                inv.replaceExistingItem(INPUT_SLOT, null);
                b.getWorld().dropItem(b.getLocation().add(0.5, 0.8, 0.5), item);
            }
        }
    }

    // ── Main page refresh ──

    private void refreshMainPage(BlockMenu menu, Block b) {
        Location loc = b.getLocation();
        Map<String, Route> routes = state(loc).routes;
        Priority prio = state(loc).priority;

        // Menu button
        menu.replaceExistingItem(MENU_SLOT, buildMenuItem());
        menu.addMenuClickHandler(MENU_SLOT, (pl, s, it, a) -> {
            openSubMenu(pl, b);
            return false;
        });

        // Comparator (priority toggle)
        menu.replaceExistingItem(PRIO_SLOT, buildPrioItem(prio));
        menu.addMenuClickHandler(PRIO_SLOT, (pl, s, it, a) -> {
            Priority newPrio = prio == Priority.DESTROY_FIRST
                    ? Priority.OUTPUT_FIRST : Priority.DESTROY_FIRST;
            state(loc).priority = newPrio;
            saveLocation(loc);
            refreshMainPage(menu, b);
            pl.playSound(pl.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            return false;
        });

        // Sign (route B list)
        menu.replaceExistingItem(SIGN_SLOT, buildRouteDisplay(true, routes));
        menu.addMenuClickHandler(SIGN_SLOT, ChestMenuUtils.getEmptyClickHandler());

        // Trash (route C list)
        menu.replaceExistingItem(TRASH_SLOT, buildRouteDisplay(false, routes));
        menu.addMenuClickHandler(TRASH_SLOT, ChestMenuUtils.getEmptyClickHandler());

        // Enchant protect
        boolean epOn = state(loc).enchProtectOn;
        int epThreshold = state(loc).enchProtectThresh;
        menu.replaceExistingItem(ENCH_PROTECT_SLOT, buildEnchantProtectItem(epOn, epThreshold));
        menu.addMenuClickHandler(ENCH_PROTECT_SLOT, (pl, s, it, a) -> {
            if (a.isShiftClicked()) {
                long now = System.currentTimeMillis();
                if (now - state(loc).lastShiftTime < 300) return false;
                state(loc).lastShiftTime = now;
                state(loc).enchProtectOn = !epOn;
            } else if (epOn) {
                int newThreshold = a.isRightClicked()
                        ? Math.max(1, epThreshold - 1)
                        : Math.min(255, epThreshold + 1);
                state(loc).enchProtectThresh = newThreshold;
            } else {
                state(loc).enchProtectOn = true;
            }
            saveLocation(loc);
            refreshMainPage(menu, b);
            pl.playSound(pl.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            return false;
        });

        // Bell (info)
        menu.replaceExistingItem(BELL_SLOT, buildBellItem());
        menu.addMenuClickHandler(BELL_SLOT, ChestMenuUtils.getEmptyClickHandler());
    }

    // ── Sub-menu ──

    private void openSubMenu(Player player, Block b) {
        Location loc = b.getLocation();
        SplitterState st = state(loc);
        Map<String, Route> saved = new HashMap<>(st.routes);
        st.dirtyRoutes = new HashMap<>(saved);
        st.dirtyPriority = st.priority;

        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> showSubPage(player, b, 1));
    }

    private void showSubPage(Player player, Block b, int page) {
        Location loc = b.getLocation();
        Map<String, Route> working = state(loc).dirtyRoutes;
        if (working == null) {
            BlockMenu inv = BlockStorage.getInventory(b);
            if (inv != null) inv.open(player);
            return;
        }

        List<String> allKeys = new ArrayList<>(ItemManager.potionEffectMap.keySet());
        allKeys.sort(Comparator.naturalOrder());
        int effectsPerPage = 18;
        int pages = Math.max(1, (allKeys.size() - 1) / effectsPerPage + 1);
        final int cur = Math.min(page, pages);

        ChestMenu menu = new ChestMenu(Messages.get("machine.equipment_splitter.menu_button"));
        menu.setEmptySlotsClickable(false);

        var sw = new HashSet<java.util.UUID>(); // switching pages

        // Top background (except save slot)
        for (int i = 0; i < 9; i++) {
            if (i == SAVE_SLOT) continue;
            menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }

        // Save button
        menu.addItem(SAVE_SLOT, buildSaveButton());
        menu.addMenuClickHandler(SAVE_SLOT, (pl, s, it, a) -> {
            sw.add(pl.getUniqueId());
            saveLocation(loc);
            pl.playSound(pl.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            pl.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> {
                BlockMenu inv = BlockStorage.getInventory(b);
                if (inv != null) {
                    refreshMainPage(inv, b);
                    inv.open(pl);
                }
            });
            return false;
        });

        // Potion grid: + in rows 2&4, - in rows 3&5 (directly below each +)
        int pageStart = effectsPerPage * (cur - 1);
        for (int i = 0; i < effectsPerPage && (pageStart + i) < allKeys.size(); i++) {
            String effectKey = allKeys.get(pageStart + i);
            int row = i / 9;
            int col = i % 9;
            int plusSlot = (row == 0 ? 9 : 27) + col;
            int minusSlot = plusSlot + 9;
            final int pageSnap = cur;

            // + version
            String plusKey = effectKey + ":+";
            Route plusRoute = working.getOrDefault(plusKey, Route.A);
            menu.addItem(plusSlot, buildAffixItem(effectKey, "+", plusRoute));
            menu.addMenuClickHandler(plusSlot, makeAffixHandler(sw, working, plusKey, b, pageSnap));

            // - version
            String minusKey = effectKey + ":-";
            Route minusRoute = working.getOrDefault(minusKey, Route.A);
            menu.addItem(minusSlot, buildAffixItem(effectKey, "-", minusRoute));
            menu.addMenuClickHandler(minusSlot, makeAffixHandler(sw, working, minusKey, b, pageSnap));
        }

        // Footer
        for (int i = 45; i < 54; i++)
            menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());

        if (cur > 1) {
            menu.addItem(46, ChestMenuUtils.getPreviousButton(player, cur, pages));
            menu.addMenuClickHandler(46, (pl, s, it, a) -> {
                sw.add(pl.getUniqueId());
                showSubPage(pl, b, cur - 1);
                return false;
            });
        }
        if (cur < pages) {
            menu.addItem(52, ChestMenuUtils.getNextButton(player, cur, pages));
            menu.addMenuClickHandler(52, (pl, s, it, a) -> {
                sw.add(pl.getUniqueId());
                showSubPage(pl, b, cur + 1);
                return false;
            });
        }

        menu.addMenuCloseHandler(pl -> {
            if (!sw.remove(pl.getUniqueId())) {
                state(loc).dirtyRoutes = null;
                state(loc).dirtyPriority = null;
            }
        });

        menu.open(player);
    }

    // ── Item builders ──

    private ItemStack buildMenuItem() {
        ItemStack item = new ItemStack(Material.LOOM);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Messages.get("machine.equipment_splitter.menu_button"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildSaveButton() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Messages.get("machine.equipment_splitter.save_exit"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildPrioItem(Priority prio) {
        ItemStack item = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = item.getItemMeta();
        boolean isDestroy = prio == Priority.DESTROY_FIRST;
        meta.setDisplayName("§f" + Messages.get("machine.equipment_splitter.priority") + ": "
                + Messages.get(isDestroy
                        ? "machine.equipment_splitter.destroy_first"
                        : "machine.equipment_splitter.output_first"));
        meta.setLore(List.of(
                Messages.get("machine.equipment_splitter.priority_lore_base"),
                Messages.get(isDestroy
                        ? "machine.equipment_splitter.priority_lore_destroy"
                        : "machine.equipment_splitter.priority_lore_output")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildRouteDisplay(boolean isB, Map<String, Route> routes) {
        ItemStack item = isB ? new ItemStack(Material.OAK_SIGN) : getTrashIcon();
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Messages.get(isB
                ? "machine.equipment_splitter.sign_b_title"
                : "machine.equipment_splitter.trash_c_title"));
        List<String> lore = new ArrayList<>();
        lore.add(Messages.get(isB
                ? "machine.equipment_splitter.sign_b_lore"
                : "machine.equipment_splitter.trash_c_lore"));
        Route target = isB ? Route.B : Route.C;
        List<String> pos = new ArrayList<>();
        List<String> neg = new ArrayList<>();
        for (var e : routes.entrySet()) {
            if (e.getValue() == target) {
                if (e.getKey().endsWith(":+")) pos.add(formatRouteKey(e.getKey()));
                else neg.add(formatRouteKey(e.getKey()));
            }
        }
        if (!pos.isEmpty() || !neg.isEmpty()) {
            lore.addAll(pos);
            lore.addAll(neg);
        } else {
            lore.add(Messages.get("machine.equipment_splitter.no_affixes"));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildAffixItem(String effectKey, String polarity, Route route) {
        PotionEffectType type = ItemManager.potionEffectMap.get(effectKey);
        boolean positive = "+".equals(polarity);
        Material mat = positive ? Material.POTION : Material.LINGERING_POTION;
        ItemStack potion = new ItemStack(mat);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        if (type != null)
            meta.addCustomEffect(new PotionEffect(type, 600, 0), true);

        meta.setDisplayName(routeName(route));

        List<String> lore = new ArrayList<>();
        lore.add(Messages.get(positive
                ? "machine.equipment_splitter.affix_pos_desc"
                : "machine.equipment_splitter.affix_neg_desc"));
        lore.add(Messages.get("machine.equipment_splitter.route_hint1"));
        lore.add(Messages.get("machine.equipment_splitter.route_hint2"));
        meta.setLore(lore);
        potion.setItemMeta(meta);
        return potion;
    }

    private ChestMenu.MenuClickHandler makeAffixHandler(Set<UUID> sw,
            Map<String, Route> working, String routeKey, Block b, int pageSnap) {
        return (pl, s, it, a) -> {
            sw.add(pl.getUniqueId());
            if (a.isShiftClicked()) {
                working.put(routeKey, Route.C);
            } else if (a.isRightClicked()) {
                working.put(routeKey, Route.A);
            } else {
                working.put(routeKey, Route.B);
            }
            showSubPage(pl, b, pageSnap);
            return false;
        };
    }

    private static ItemStack stainedPane(Material mat) {
        ItemStack pane = new ItemStack(mat);
        ItemMeta m = pane.getItemMeta();
        m.setDisplayName(" ");
        pane.setItemMeta(m);
        return pane;
    }

    private ItemStack buildEnchantProtectItem(boolean on, int threshold) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (on) {
            meta.setDisplayName(Messages.get("machine.equipment_splitter.ench_protect_on"));
            meta.setLore(List.of(
                    Messages.get("machine.equipment_splitter.ench_protect_lore1",
                            String.valueOf(threshold)),
                    Messages.get("machine.equipment_splitter.ench_protect_lore2"),
                    "",
                    Messages.get("machine.equipment_splitter.ench_protect_hint_on1"),
                    Messages.get("machine.equipment_splitter.ench_protect_hint_on2")));
        } else {
            meta.setDisplayName(Messages.get("machine.equipment_splitter.ench_protect_off"));
            meta.setLore(List.of(Messages.get("machine.equipment_splitter.ench_protect_hint_off")));
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildBellItem() {
        ItemStack item = new ItemStack(Material.BELL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Messages.get("machine.equipment_splitter.bell_title"));
        meta.setLore(List.of(
                Messages.get("machine.equipment_splitter.bell_lore1"),
                Messages.get("machine.equipment_splitter.bell_lore2")));
        item.setItemMeta(meta);
        return item;
    }

    private static void dropIfPresent(Block b, BlockMenu inv, int slot) {
        ItemStack item = inv.getItemInSlot(slot);
        if (item != null && !item.getType().isAir())
            b.getWorld().dropItem(b.getLocation().add(0.5, 0.8, 0.5), item);
    }

    private ItemStack getTrashIcon() {
        SlimefunItem sfTrash = SlimefunItem.getById("TRASH_CAN");
        if (sfTrash != null) return sfTrash.getItem().clone();
        return new ItemStack(Material.CAULDRON);
    }

    // ── Helpers ──

    private String routeName(Route r) {
        return switch (r) {
            case B -> Messages.get("machine.equipment_splitter.route_b_title");
            case C -> Messages.get("machine.equipment_splitter.route_c_title");
            default -> Messages.get("machine.equipment_splitter.route_a_title");
        };
    }

    private String formatRouteKey(String key) {
        int idx = key.lastIndexOf(':');
        if (idx < 0) return key;
        String ek = key.substring(0, idx);
        String pol = key.substring(idx + 1);
        String name = ItemManager.effectNames.getOrDefault(ek, ek);
        return "+".equals(pol) ? "§a+" + name : "§c-" + name;
    }

    // ── Persistence helpers ──

    private static String locKey(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX()
                + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    static void deleteLocation(Location loc) {
        File file = getDataFile();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String key = locKey(loc);
        if (cfg.contains(key)) {
            cfg.set(key, null);
            try { cfg.save(file); } catch (Exception ignored) {}
        }
    }

    static void loadLocation(Location loc) {
        File file = getDataFile();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String key = locKey(loc);
        String prioStr = cfg.getString(key + ".priority", "DESTROY_FIRST");
        SplitterState st = state(loc);
        st.priority = Priority.valueOf(prioStr);
        st.enchProtectOn = cfg.getBoolean(key + ".ench_protect_enabled", false);
        st.enchProtectThresh = cfg.getInt(key + ".ench_protect_threshold", 5);

        Map<String, Route> routes = new HashMap<>();
        var sec = cfg.getConfigurationSection(key + ".routes");
        if (sec != null) {
            for (String rk : sec.getKeys(false))
                routes.put(rk, Route.valueOf(sec.getString(rk, "A")));
        }
        st.routes.clear();
        st.routes.putAll(routes);
    }

    static void saveLocation(Location loc) {
        SplitterState st = state(loc);
        Map<String, Route> working = st.dirtyRoutes;
        Priority prio = st.dirtyPriority;
        st.dirtyRoutes = null;
        st.dirtyPriority = null;

        if (working != null) {
            st.routes.clear();
            st.routes.putAll(working);
        } else {
            working = new HashMap<>(st.routes);
        }
        if (prio != null) st.priority = prio;
        else prio = st.priority;

        File file = getDataFile();
        YamlConfiguration cfg;
        if (file.exists()) {
            cfg = YamlConfiguration.loadConfiguration(file);
        } else {
            cfg = new YamlConfiguration();
        }

        String key = locKey(loc);
        cfg.set(key + ".priority", prio.name());
        cfg.set(key + ".ench_protect_enabled", st.enchProtectOn);
        cfg.set(key + ".ench_protect_threshold", st.enchProtectThresh);
        for (var e : working.entrySet())
            cfg.set(key + ".routes." + e.getKey(), e.getValue().name());

        try { cfg.save(file); } catch (Exception ignored) {}
    }

    public static void setPlugin(Plugin p) { plugin = p; }

    private static File getDataFile() {
        return new File(plugin.getDataFolder(), "equipment_splitter.yml");
    }

    // ── InventoryBlock ──

    @Override public int[] getInputSlots() { return new int[]{INPUT_SLOT}; }
    @Override public int[] getOutputSlots() { return new int[]{OUTPUT_SLOT}; }
}
