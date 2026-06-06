package com.github.oowjzzoo.magicloot3;

import java.io.InputStream;
import java.util.Properties;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkEffectMeta;
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

import com.github.oowjzzoo.magicloot3.items.PastRune;
import com.github.oowjzzoo.magicloot3.machines.EquipmentSplitter;
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

        // --- Shared crafting materials (must be created before machines that use them) ---

        // Ancient Rune of Past (往日)
        String runeName = zh ? "§7古代符文 §8§l[§e§l往日§8§l]"
                : "§7Ancient Rune §8§l[§e§lPast§8§l]";
        String[] runeLore = zh
                ? new String[]{"§e把符文丢向你已经丢出的装备物品",
                        "§e该物品会变为 §4未鉴定 §e的乱码装备",
                        "", "§7往日种种，你当真不记得了?",
                        "§7往日种种? 往日...", "§7你说的可是往日..."}
                : new String[]{"§eDrop this rune onto an equipment item",
                        "§eThe item will become §4Unidentified §ewith garbled name",
                        "", "§7Past memories, you truly don't remember?",
                        "§7Past memories? Past...", "§7Are you talking about the past..."};

        // SF's ColoredFireworkStar pattern: effect color + HIDE_ADDITIONAL_TOOLTIP
        ItemStack runeBase = new ItemStack(Material.FIREWORK_STAR);
        FireworkEffectMeta fwMeta = (FireworkEffectMeta) runeBase.getItemMeta();
        fwMeta.setEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BURST).withColor(Color.YELLOW).build());
        fwMeta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        runeBase.setItemMeta(fwMeta);
        SlimefunItemStack runeStack = new SlimefunItemStack(
                "ANCIENT_RUNE_PAST", runeBase, runeName, runeLore);

        ItemStack[] runeRecipe = {
                new ItemStack(Material.ANCIENT_DEBRIS),
                new ItemStack(Material.WAXED_WEATHERED_COPPER_BULB),
                new ItemStack(Material.SCULK_CATALYST),
                SlimefunItems.ESSENCE_OF_AFTERLIFE,
                SlimefunItems.VILLAGER_RUNE,
                SlimefunItems.ESSENCE_OF_AFTERLIFE,
                new ItemStack(Material.SCULK_CATALYST),
                new ItemStack(Material.WAXED_WEATHERED_COPPER_BULB),
                new ItemStack(Material.ANCIENT_DEBRIS)};

        new PastRune(itemGroup, runeStack, RecipeType.ANCIENT_ALTAR, runeRecipe,
                new SlimefunItemStack(runeStack, 3))
                .register(this);

        // Time of Exploration (探索的时光)
        String timeName = zh ? "&b&l探索的时光" : "&b&lAdventuring Time";
        String[] timeLore = zh
                ? new String[]{"", "&7一缕光阴"}
                : new String[]{"", "&7A sliver of time"};
        SlimefunItemStack timeStack = new SlimefunItemStack(
                "TIME_OF_EXPLORATION", Material.NETHER_STAR, timeName, timeLore);
        ItemStack[] timeRecipe = {
                runeStack, new ItemStack(Material.DRAGON_HEAD), runeStack,
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE),
                new ItemStack(Material.BEACON),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE),
                runeStack, new ItemStack(Material.DRAGON_HEAD), runeStack};
        new SlimefunItem(itemGroup, timeStack, RecipeType.ANCIENT_ALTAR, timeRecipe,
                new SlimefunItemStack(timeStack, 4))
                .register(this);

        // --- Machines ---

        String[] machineLore = {"",
                zh ? "&7花费了大量的 &b&l探索的时光" : "&7After spending a great deal of &b&lAdventuring Time",
                zh ? "&7你终于打造出了一套神装" : "&7you have finally crafted a set of godly gear",
                "",
                LoreBuilder.machine(MachineTier.END_GAME, MachineType.MACHINE),
                LoreBuilder.speed(5), LoreBuilder.powerPerSecond(128)};

        // Potion Affix Disenchanter
        String disName = zh ? "§d药水词缀祛魔机" : "§dPotion Affix Disenchanter";
        SlimefunItemStack disStack = new SlimefunItemStack(
                "POTION_AFFIX_DISENCHANTER", Material.NOTE_BLOCK, disName, machineLore);
        ItemStack[] disRecipe = {
                timeStack, SlimefunItems.CARBONADO, timeStack,
                SlimefunItems.PROGRAMMABLE_ANDROID_3_FISHERMAN,
                SlimefunItems.AUTO_DISENCHANTER_2,
                SlimefunItems.PROGRAMMABLE_ANDROID_3_BUTCHER,
                timeStack, SlimefunItems.NETHER_STAR_REACTOR, timeStack};
        new PotionAffixDisenchanter(itemGroup, disStack,
                RecipeType.ENHANCED_CRAFTING_TABLE, disRecipe)
                .register(this);

        // Potion Affix Enchanter
        String enchName = zh ? "§d药水词缀附魔机" : "§dPotion Affix Enchanter";
        SlimefunItemStack enchStack = new SlimefunItemStack(
                "POTION_AFFIX_ENCHANTER", Material.JUKEBOX, enchName, machineLore);
        ItemStack[] enchRecipe = {
                timeStack, SlimefunItems.CARBONADO, timeStack,
                SlimefunItems.PROGRAMMABLE_ANDROID_3_FISHERMAN,
                SlimefunItems.AUTO_ENCHANTER_2,
                SlimefunItems.PROGRAMMABLE_ANDROID_3_BUTCHER,
                timeStack, SlimefunItems.NETHER_STAR_REACTOR, timeStack};
        new PotionAffixEnchanter(itemGroup, enchStack,
                RecipeType.ENHANCED_CRAFTING_TABLE, enchRecipe)
                .register(this);

        // Equipment Splitter
        String splitterName = zh ? "§a§l装备分流器" : "§a§lEquipment Splitter";
        SlimefunItemStack splitterStack = new SlimefunItemStack(
                "EQUIPMENT_SPLITTER", Material.STONECUTTER, splitterName, "");
        ItemStack[] splitterRecipe = {
                null, SlimefunItems.REINFORCED_ALLOY_INGOT, null,
                bookshelfStack, new ItemStack(Material.DISPENSER), bookshelfStack,
                bookshelfStack, SlimefunItems.PORTABLE_DUSTBIN, bookshelfStack};
        new EquipmentSplitter(itemGroup, splitterStack,
                RecipeType.ENHANCED_CRAFTING_TABLE, splitterRecipe)
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
