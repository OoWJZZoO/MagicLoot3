package com.github.oowjzzoo.magicloot3;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;

public class MagicLoot3 extends JavaPlugin implements SlimefunAddon, Listener {

    private static MagicLoot3 instance;
    private static boolean debug;

    @Override
    public void onEnable() {
        instance = this;

        getCommand("magicloot").setExecutor(new MagicLootCommand(this));
        saveDefaultConfig();
        MagicLootConfig.setupConfigs(this);

        // Extract default .nbt files to plugin folder (saveDefault semantics)
        StructurePlacer.extractDefaults(this);
        RuinBuilder.loadRuins(this);

        // Deploy structures to already-loaded worlds
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            StructurePlacer.deployToWorld(this, world);
        }

        // Deploy structures to worlds loaded later
        getServer().getPluginManager().registerEvents(this, this);

        getServer().getScheduler().runTaskLater(this, () -> {
            MagicLootConfig.loadSettings();

            if (Slimefun.instance() != null) {
                registerSlimefunItems();
                getLogger().info("Slimefun integration enabled!");
            } else {
                getLogger().warning("Slimefun not found - SF items will not be registered.");
            }

            new LootListener(this);
            getLogger().info("MagicLoot3 loaded successfully!");
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
        ItemGroup itemGroup = new ItemGroup(
                new NamespacedKey(this, "magicloot"),
                new ItemStack(Material.BOOKSHELF));

        SlimefunItemStack lostBookshelfStack = new SlimefunItemStack(
                "LOST_BOOKSHELF", Material.BOOKSHELF,
                "§dLost Bookshelf", "",
                "§rScrambled Parts of an", "§rancient Library...");

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
                "§dLost Librarian's Desk", "",
                "§rBasically like a Lost Librarian");

        ItemStack[] deskRecipe = {
                lostBookshelfStack, null, lostBookshelfStack,
                null, SlimefunItems.COMMON_TALISMAN, null,
                lostBookshelfStack, null, lostBookshelfStack};

        SlimefunItem lostDesk = new SlimefunItem(
                itemGroup, lostDeskStack,
                RecipeType.ENHANCED_CRAFTING_TABLE, deskRecipe);
        lostDesk.addItemHandler((ItemUseHandler) event -> {
            event.cancel();
            LostLibrarian.openMenu(event.getPlayer());
        });
        lostDesk.register(this);

        getLogger().info("Registered Slimefun items: LOST_BOOKSHELF, LOST_LIBRARIANS_DESK");
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
}
