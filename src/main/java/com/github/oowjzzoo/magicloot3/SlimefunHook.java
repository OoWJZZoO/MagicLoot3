package com.github.oowjzzoo.magicloot3;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;

/**
 * Slimefun integration. This class is never loaded unless Slimefun
 * is actually installed (guarded by isPluginEnabled check in onEnable).
 */
final class SlimefunHook implements SlimefunAddon {

    private final JavaPlugin plugin;

    private SlimefunHook(JavaPlugin plugin) { this.plugin = plugin; }

    static void registerItems(Plugin plugin) {
        new SlimefunHook((JavaPlugin) plugin).doRegister();
    }

    private void doRegister() {
        SlimefunItemStack iconStack = new SlimefunItemStack(
                "MAGICLOOT_ICON", Material.BOOKSHELF, "§5魔法战利品");
        ItemGroup itemGroup = new ItemGroup(
                new NamespacedKey(plugin, "magicloot"), iconStack);

        // Lost Bookshelf
        SlimefunItemStack bookshelfStack = new SlimefunItemStack(
                "LOST_BOOKSHELF", Material.BOOKSHELF,
                "§d旧日书架", "",
                "§7装满了过气老书，充斥着狗血剧情",
                "§7但是屎里淘金的话...",
                "§7说不定真能找到有用的旧书(存疑)");

        ItemStack[] bookshelfRecipe = {
                new ItemStack(Material.BOOKSHELF), null, new ItemStack(Material.BOOKSHELF),
                SlimefunItems.MAGIC_LUMP_3, SlimefunItems.MAGICAL_BOOK_COVER, SlimefunItems.MAGIC_LUMP_3,
                new ItemStack(Material.BOOKSHELF), null, new ItemStack(Material.BOOKSHELF)};

        SlimefunItem bookshelf = new SlimefunItem(
                itemGroup, bookshelfStack, RecipeType.ENHANCED_CRAFTING_TABLE,
                bookshelfRecipe, new SlimefunItemStack(bookshelfStack, 2));
        bookshelf.register(this);

        // Lost Librarian's Desk
        SlimefunItemStack deskStack = new SlimefunItemStack(
                "LOST_LIBRARIANS_DESK", Material.CRAFTING_TABLE,
                "§d遗物鉴定桌", "",
                "§7其实那几个旧书架没什么用",
                "§7只不过因为看起来旧所以显得雅致",
                "§7有助于进入状态研究装备");

        ItemStack[] deskRecipe = {
                bookshelfStack, null, bookshelfStack,
                null, SlimefunItems.COMMON_TALISMAN, null,
                bookshelfStack, null, bookshelfStack};

        SlimefunItem desk = new SlimefunItem(
                itemGroup, deskStack, RecipeType.ENHANCED_CRAFTING_TABLE, deskRecipe);
        desk.addItemHandler((BlockUseHandler) event -> {
            event.cancel();
            LostLibrarian.openMenu(event.getPlayer(), true);
        });
        desk.register(this);

        plugin.getLogger().info(Messages.get("log.items_registered"));
    }

    @Override
    public JavaPlugin getJavaPlugin() { return plugin; }

    @Override
    public String getBugTrackerURL() {
        return "https://github.com/OoWJZZoO/MagicLoot3/issues";
    }
}
