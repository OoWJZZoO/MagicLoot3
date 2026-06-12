package com.github.oowjzzoo.magicloot3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * Lightweight menu abstraction on top of raw Bukkit Inventory.
 * Supports any size (9/18/27/36/45/54), self-registers a single
 * global listener that routes clicks via {@link InventoryHolder}.
 *
 * <pre>{@code
 * new MenuGUI(18, "My Menu")
 *     .fillBg(0,1,2, 6,7,8)
 *     .setButton(4, myIcon, (p, s, a) -> doSomething(p))
 *     .open(player);
 * }</pre>
 */
public final class MenuGUI {

    @FunctionalInterface
    public interface ClickHandler {
        /** @param action the raw ClickAction from InventoryClickEvent */
        void onClick(Player player, int slot, org.bukkit.event.inventory.ClickType action);
    }

    // ── Internal holder identifies our menus in events ──

    static final class MenuHolder implements InventoryHolder {
        final MenuGUI menu;
        MenuHolder(MenuGUI menu) { this.menu = menu; }
        @Override public Inventory getInventory() { return menu.inventory; }
    }

    // ── Static listener (one per JVM, lazily registered) ──

    private static Plugin plugin;
    private static MenuListener listener;

    private static final class MenuListener implements Listener {
        @EventHandler
        public void onClick(InventoryClickEvent e) {
            if (!(e.getInventory().getHolder() instanceof MenuHolder holder)) return;
            if (e.getClickedInventory() != e.getView().getTopInventory()) {
                if (!holder.menu.allowBottom) e.setCancelled(true);
                else if (holder.menu.lockMainHand) {
                    Player p = (Player) e.getWhoClicked();
                    int hotbarSlot = holder.menu.inventory.getSize() + 27 + p.getInventory().getHeldItemSlot();
                    if (e.getRawSlot() == hotbarSlot)
                        e.setCancelled(true);
                }
                return;
            }
            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getType().isAir()) return;

            MenuGUI.ClickHandler h = holder.menu.handlers.get(e.getRawSlot());
            if (h != null) h.onClick((Player) e.getWhoClicked(), e.getRawSlot(), e.getClick());
        }

        @EventHandler
        public void onDrag(InventoryDragEvent e) {
            if (e.getInventory().getHolder() instanceof MenuHolder) e.setCancelled(true);
        }

        @EventHandler
        public void onClose(InventoryCloseEvent e) {
            if (!(e.getInventory().getHolder() instanceof MenuHolder holder)) return;
            Runnable cb = holder.menu.closeCallback;
            if (cb != null) cb.run();
        }
    }

    // ── Instance ──

    private final Inventory inventory;
    private final Map<Integer, ClickHandler> handlers = new HashMap<>();
    private Runnable closeCallback;
    private boolean allowBottom;
    private boolean lockMainHand;

    /** Create a menu with the given row count and title. */
    public MenuGUI(int size, String title) {
        if (!Lazy.listenerReady) {
            if (plugin == null) plugin = MagicLoot3.getInstance();
            listener = new MenuListener();
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            Lazy.listenerReady = true;
        }
        this.inventory = Bukkit.createInventory(new MenuHolder(this), size, title);
    }

    // ── Fluent builders ──

    /** Set a clickable button. */
    public MenuGUI setButton(int slot, ItemStack item, ClickHandler handler) {
        inventory.setItem(slot, item);
        handlers.put(slot, handler);
        return this;
    }

    /** Set a non-interactive display item. */
    public MenuGUI setDisplay(int slot, ItemStack item) {
        inventory.setItem(slot, item);
        return this;
    }

    /** Fill the given slots with gray glass pane background. */
    public MenuGUI fillBg(int... slots) {
        return fillBg(Material.GRAY_STAINED_GLASS_PANE, slots);
    }

    /** Fill the given slots with a custom-color glass pane background. */
    public MenuGUI fillBg(Material glassColor, int... slots) {
        ItemStack bg = bgPane(glassColor);
        for (int s : slots) inventory.setItem(s, bg);
        return this;
    }

    /** Fill all empty slots with gray glass pane background. Must be called LAST. */
    public MenuGUI fillEmpty() {
        return fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    /** Fill all empty slots with custom-color glass pane background. Must be called LAST. */
    public MenuGUI fillEmpty(Material glassColor) {
        ItemStack bg = bgPane(glassColor);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null || inventory.getItem(i).getType().isAir())
                inventory.setItem(i, bg);
        }
        return this;
    }

    /** Set a callback that fires when the menu is closed. */
    public MenuGUI onClose(Runnable callback) {
        this.closeCallback = callback;
        return this;
    }

    /** Allow the player to pick up items from their own inventory while this menu is open. */
    public MenuGUI allowBottom() {
        this.allowBottom = true;
        return this;
    }

    /** Prevent the player from picking up the item in their main hand hotbar slot. */
    public MenuGUI lockMainHand() {
        this.lockMainHand = true;
        return this;
    }

    // ── Lifecycle ──

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public void close(Player player) {
        player.closeInventory();
    }

    // Accessors for edge cases
    public Inventory getInventory() { return inventory; }

    /** Unregister the global listener. Only needed on plugin disable. */
    public static void shutdown() {
        if (listener != null) HandlerList.unregisterAll(listener);
        listener = null;
        Lazy.listenerReady = false;
    }

    // ── Helpers ──

    private static ItemStack bgPane(Material glassColor) {
        ItemStack bg = new ItemStack(glassColor);
        ItemMeta m = bg.getItemMeta();
        m.setDisplayName(" ");
        m.setCustomModelData(2200002);
        bg.setItemMeta(m);
        return bg;
    }

    /** Lazy-init gate to avoid double listener registration. */
    private static class Lazy { static boolean listenerReady; }
}
