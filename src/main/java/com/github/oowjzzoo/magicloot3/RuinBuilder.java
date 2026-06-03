package com.github.oowjzzoo.magicloot3;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

public class RuinBuilder {

    public static final List<String> ruinNames = new ArrayList<>();
    public static final List<String> buildingNames = new ArrayList<>();
    public static final Map<String, ConfigManager> configs = new HashMap<>();
    private static Plugin plugin;

    public static void loadRuins(Plugin pl) {
        plugin = pl;
        File dataFolder = plugin.getDataFolder();

        File ruinSettingsDir = new File(dataFolder, "ruin_settings");
        if (!ruinSettingsDir.exists()) ruinSettingsDir.mkdirs();

        String[] ruins = {"Farm", "GasStation", "House", "Outpost", "Railstation", "Shop"};
        for (String name : ruins) {
            ruinNames.add(name);
            ConfigManager cfg = new ConfigManager(
                    new File(ruinSettingsDir, name + ".yml"));
            cfg.setDefaultValue("y-offset", 0);
            cfg.setDefaultValue("underwater", false);
            cfg.save();
            configs.put(name, cfg);
        }

        buildingNames.add("Lost_Library");
        ConfigManager libCfg = new ConfigManager(
                new File(ruinSettingsDir, "Lost_Library.yml"));
        libCfg.setDefaultValue("y-offset", 0);
        libCfg.setDefaultValue("underwater", false);
        libCfg.save();
        configs.put("Lost_Library", libCfg);
    }

    public static void buildRuin(Location l) {
        if (ruinNames.isEmpty()) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (random.nextInt(100) < 4 && !buildingNames.isEmpty()) {
            // 4% chance: spawn a building (Lost Library)
            String name = buildingNames.get(random.nextInt(buildingNames.size()));
            if (l.getBlock().isLiquid()) return;
            StructurePlacer.place(plugin, l, name, true);
        } else {
            // 96% chance: spawn a regular ruin
            String name = ruinNames.get(random.nextInt(ruinNames.size()));
            ConfigManager cfg = configs.get(name);
            if (cfg != null && l.getBlock().isLiquid() && !cfg.getBoolean("underwater")) return;
            if (cfg != null) l.setY(l.getY() + cfg.getInt("y-offset"));
            StructurePlacer.place(plugin, l, name, false);
        }
    }
}
