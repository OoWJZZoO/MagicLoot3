package com.github.oowjzzoo.magicloot3;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;

public class MagicLoot3 extends JavaPlugin implements SlimefunAddon, Listener {

    private static MagicLoot3 instance;
    private static boolean debug;

    @Override
    public void onEnable() {
        instance = this;

        ItemKeys.init(this);

        MagicLootCommand cmd = new MagicLootCommand(this);
        getCommand("magicloot").setExecutor(cmd);
        getCommand("magicloot").setTabCompleter(cmd);
        saveDefaultConfig();

        // Load language
        String lang = getConfig().getString("language", "zh");
        Messages.load(this, lang);

        MagicLootConfig.setupConfigs(this);
        StructurePlacer.extractDefaults(this);
        RuinBuilder.loadRuins(this);

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            StructurePlacer.deployToWorld(this, world);
        }
        getServer().getPluginManager().registerEvents(this, this);

        getServer().getScheduler().runTaskLater(this, () -> {
            MagicLootConfig.loadSettings();

            if (Slimefun.instance() != null) {
                registerSlimefunItems();
                getLogger().info(Messages.get("log.slimefun_enabled"));
            } else {
                getLogger().warning(Messages.get("log.slimefun_not_found"));
            }

            new LootListener(this);
            getLogger().info(Messages.get("log.loaded"));
        }, 10L);
    }

    @Override
    public void onDisable() {
        instance = null;
        ItemManager.ENCHANTMENTS = null;
        ItemManager.POTIONEFFECTS = null;
        ItemManager.PREFIX = null;
        ItemManager.SUFFIX = null;
        ItemManager.COLOR = null;
        ItemManager.EFFECTS = null;
        ItemManager.TOOLS = null;
        ItemManager.TREASURE = null;
        ItemManager.SLIMEFUN = null;
        ItemManager.types = null;
        ItemManager.potion = null;
        MagicLootConfig.prefixes.clear();
        MagicLootConfig.suffixes.clear();
        MagicLootConfig.colors.clear();
        MagicLootConfig.effects.clear();
        MagicLootConfig.mobs.clear();
        RuinBuilder.ruinNames.clear();
        RuinBuilder.buildingNames.clear();
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        StructurePlacer.deployToWorld(this, event.getWorld());
    }

    private void registerSlimefunItems() {
        SlimefunItemStack iconStack = new SlimefunItemStack(
                "MAGICLOOT_ICON", Material.BOOKSHELF,
                "§5魔法战利品");
        ItemGroup itemGroup = new ItemGroup(
                new NamespacedKey(this, "magicloot"), iconStack);

        SlimefunItemStack lostBookshelfStack = new SlimefunItemStack(
                "LOST_BOOKSHELF", Material.BOOKSHELF,
                "§d旧日书架", "",
                "§7装满了过气老书，充斥着狗血剧情",
                "§7但是屎里淘金的话...",
                "§7说不定真能找到有用的旧书(存疑)");

        ItemStack[] bookshelfRecipe = {
                new ItemStack(Material.BOOKSHELF), null, new ItemStack(Material.BOOKSHELF),
                SlimefunItems.MAGIC_LUMP_3, SlimefunItems.MAGICAL_BOOK_COVER, SlimefunItems.MAGIC_LUMP_3,
                new ItemStack(Material.BOOKSHELF), null, new ItemStack(Material.BOOKSHELF)};

        SlimefunItem lostBookshelf = new SlimefunItem(
                itemGroup, lostBookshelfStack,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                bookshelfRecipe,
                new SlimefunItemStack(lostBookshelfStack, 2));
        lostBookshelf.register(this);

        SlimefunItemStack lostDeskStack = new SlimefunItemStack(
                "LOST_LIBRARIANS_DESK", Material.CRAFTING_TABLE,
                "§d遗物鉴定桌", "",
                "§7其实那几个旧书架没什么用",
                "§7只不过因为看起来旧所以显得雅致",
                "§7有助于进入状态研究装备");

        ItemStack[] deskRecipe = {
                lostBookshelfStack, null, lostBookshelfStack,
                null, SlimefunItems.COMMON_TALISMAN, null,
                lostBookshelfStack, null, lostBookshelfStack};

        SlimefunItem lostDesk = new SlimefunItem(
                itemGroup, lostDeskStack,
                RecipeType.ENHANCED_CRAFTING_TABLE, deskRecipe);
        lostDesk.addItemHandler((BlockUseHandler) event -> {
            event.cancel();
            LostLibrarian.openMenu(event.getPlayer(), true);
        });
        lostDesk.register(this);

        getLogger().info(Messages.get("log.items_registered"));
    }

    @Override
    public JavaPlugin getJavaPlugin() { return this; }

    @Override
    public String getBugTrackerURL() {
        return "https://github.com/OoWJZZoO/MagicLoot3/issues";
    }

    public static MagicLoot3 getInstance() { return instance; }
    public static boolean isDebug() { return debug; }
    public static void setDebug(boolean value) { debug = value; }

    public static void reload(Plugin plugin) {
        instance.reloadConfig();
        String lang = instance.getConfig().getString("language", "zh");
        Messages.load(instance, lang);
        MagicLootConfig.setupConfigs(instance);
        MagicLootConfig.loadSettings();
        RuinBuilder.loadRuins(instance);
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            StructurePlacer.deployToWorld(instance, world);
        }
        instance.getLogger().info(Messages.get("log.config_reloaded",
                RuinBuilder.ruinNames.size(), RuinBuilder.buildingNames.size()));
    }
}
