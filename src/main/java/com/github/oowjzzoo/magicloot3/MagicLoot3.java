package com.github.oowjzzoo.magicloot3;

import com.github.oowjzzoo.magicloot3.machines.EquipmentSplitter;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class MagicLoot3 extends JavaPlugin implements Listener {

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

            if (Bukkit.getPluginManager().isPluginEnabled("Slimefun")) {
                EquipmentSplitter.setPlugin(this);
                SlimefunHook.registerItems(this);
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
        ItemManager.enchantments = null;
        ItemManager.potionEffectTypes = null;
        ItemManager.prefixes = null;
        ItemManager.suffixes = null;
        ItemManager.colorCodes = null;
        ItemManager.effectDisplayNames = null;
        ItemManager.enabledLootTypes = null;
        ItemManager.potionEffectMap = null;
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

    @EventHandler
    public void onServerLoaded(ServerLoadEvent event) {
        // All plugins (including SF addons) are now loaded.
        // Write default weights for any SF items that were registered after ours.
        if (Bukkit.getPluginManager().isPluginEnabled("Slimefun")) {
            MagicLootConfig.ensureDefaults(this);
            MagicLootConfig.loadSettings();
            getLogger().info("Server loaded — refreshed loot config defaults.");
        }
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
