package com.github.oowjzzoo.magicloot3;

import java.io.InputStream;
import java.util.Properties;

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
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineTier;
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineType;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.utils.LoreBuilder;

import com.github.oowjzzoo.magicloot3.machines.PotionAffixDisenchanter;
import com.github.oowjzzoo.magicloot3.machines.PotionAffixEnchanter;

/**
 * Slimefun integration. SF item names are bound to the build language
 * and never change at runtime. This class is only loaded when Slimefun
 * is installed (guarded by isPluginEnabled check).
 */
final class SlimefunHook implements SlimefunAddon {

    /** Build-time language. Read from filtered build-info.properties. */
    private static final String LANG = loadBuildLang();

    private final JavaPlugin plugin;

    private SlimefunHook(JavaPlugin plugin) { this.plugin = plugin; }

    static void registerItems(Plugin plugin) {
        new SlimefunHook((JavaPlugin) plugin).doRegister();
    }

    private void doRegister() {
        boolean zh = "zh".equals(LANG);

        SlimefunItemStack iconStack = new SlimefunItemStack(
                "MAGICLOOT_ICON", Material.BOOKSHELF,
                zh ? "§5魔法战利品" : "§5MagicLoot");
        ItemGroup itemGroup = new ItemGroup(
                new NamespacedKey(plugin, "magicloot"), iconStack);

        // Lost Bookshelf
        String shelfName = zh ? "§d旧日书架" : "§dLost Bookshelf";
        String[] shelfLore = zh
                ? new String[]{"", "§7装满了过气老书，充斥着狗血剧情",
                        "§7但是屎里淘金的话...",
                        "§7说不定真能找到有用的旧书(存疑)"}
                : new String[]{"", "§7Scrambled parts of an ancient library",
                        "§7Who knows what useful (or useless)",
                        "§7knowledge lies within..."};

        SlimefunItemStack bookshelfStack = new SlimefunItemStack(
                "LOST_BOOKSHELF", Material.BOOKSHELF,
                shelfName, shelfLore);

        ItemStack[] bookshelfRecipe = {
                new ItemStack(Material.BOOKSHELF), null, new ItemStack(Material.BOOKSHELF),
                SlimefunItems.MAGIC_LUMP_3, SlimefunItems.MAGICAL_BOOK_COVER, SlimefunItems.MAGIC_LUMP_3,
                new ItemStack(Material.BOOKSHELF), null, new ItemStack(Material.BOOKSHELF)};

        SlimefunItem bookshelf = new SlimefunItem(
                itemGroup, bookshelfStack, RecipeType.ENHANCED_CRAFTING_TABLE,
                bookshelfRecipe, new SlimefunItemStack(bookshelfStack, 2));
        bookshelf.register(this);

        // Lost Librarian's Desk
        String deskName = zh ? "§d遗物鉴定桌" : "§dLost Librarian's Desk";
        String[] deskLore = zh
                ? new String[]{"", "§7其实那几个旧书架没什么用",
                        "§7只不过因为看起来旧所以显得雅致",
                        "§7有助于进入状态研究装备"}
                : new String[]{"", "§7Basically like a Lost Librarian",
                        "§7Those old bookshelves just set the mood",
                        "§7Perfect for examining mysterious relics"};

        SlimefunItemStack deskStack = new SlimefunItemStack(
                "LOST_LIBRARIANS_DESK", Material.CRAFTING_TABLE,
                deskName, deskLore);

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

        String[] machineLore = {LoreBuilder.machine(MachineTier.END_GAME, MachineType.MACHINE),
                LoreBuilder.speed(3), LoreBuilder.powerPerSecond(128)};

        // Potion Affix Disenchanter
        String disName = zh ? "§d药水词缀祛魔机" : "§dPotion Affix Disenchanter";
        SlimefunItemStack disStack = new SlimefunItemStack(
                "POTION_AFFIX_DISENCHANTER", Material.NOTE_BLOCK, disName, machineLore);
        ItemStack[] disRecipe = {null, null, null,
                null, SlimefunItems.AUTO_DISENCHANTER, null,
                null, null, null};
        new PotionAffixDisenchanter(itemGroup, disStack,
                RecipeType.ENHANCED_CRAFTING_TABLE, disRecipe)
                .register(this);

        // Potion Affix Enchanter
        String enchName = zh ? "§d药水词缀附魔机" : "§dPotion Affix Enchanter";
        SlimefunItemStack enchStack = new SlimefunItemStack(
                "POTION_AFFIX_ENCHANTER", Material.JUKEBOX, enchName, machineLore);
        ItemStack[] enchRecipe = {null, null, null,
                null, SlimefunItems.AUTO_ENCHANTER, null,
                null, null, null};
        new PotionAffixEnchanter(itemGroup, enchStack,
                RecipeType.ENHANCED_CRAFTING_TABLE, enchRecipe)
                .register(this);

        plugin.getLogger().info(Messages.get("log.items_registered"));
    }

    @Override
    public JavaPlugin getJavaPlugin() { return plugin; }

    @Override
    public String getBugTrackerURL() {
        return "https://github.com/OoWJZZoO/MagicLoot3/issues";
    }

    private static String loadBuildLang() {
        try (InputStream in = SlimefunHook.class.getResourceAsStream("/build-info.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                return p.getProperty("lang", "zh");
            }
        } catch (Exception ignored) {}
        return "zh";
    }
}
