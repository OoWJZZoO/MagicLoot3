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

    /** All .nbt file names (without extension) found in the structures folder. */
    public static final List<String> ruinNames = new ArrayList<>();
    public static final List<String> buildingNames = new ArrayList<>();
    public static final Map<String, ConfigManager> configs = new HashMap<>();
    private static Plugin plugin;

    /**
     * Scans the structures/ folder for .nbt files.
     * Files listed in StructurePlacer.BUILDING_SET are treated as buildings,
     * everything else as ruins.
     */
    public static void loadRuins(Plugin pl) {
        plugin = pl;
        ruinNames.clear();
        buildingNames.clear();
        configs.clear();

        File structuresDir = new File(plugin.getDataFolder(), "structures");
        if (!structuresDir.exists()) structuresDir.mkdirs();

        File ruinSettingsDir = new File(plugin.getDataFolder(), "ruin_settings");
        if (!ruinSettingsDir.exists()) ruinSettingsDir.mkdirs();

        File[] files = structuresDir.listFiles((dir, name) -> name.endsWith(".nbt"));
        if (files == null) return;

        for (File file : files) {
            String name = file.getName().replace(".nbt", "");

            if (StructurePlacer.BUILDING_SET.contains(name)) {
                buildingNames.add(name);
            } else {
                ruinNames.add(name);
            }

            ConfigManager cfg = new ConfigManager(
                    new File(ruinSettingsDir, name + ".yml"));
            cfg.setDefaultValue("y-offset", 0);
            cfg.setDefaultValue("underwater", false);
            cfg.save();
            configs.put(name, cfg);
        }
    }

    public static void buildRuin(Location l) {
        if (ruinNames.isEmpty() && buildingNames.isEmpty()) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (random.nextInt(100) < 4 && !buildingNames.isEmpty()) {
            String name = buildingNames.get(random.nextInt(buildingNames.size()));
            if (l.getBlock().isLiquid()) return;
            StructurePlacer.place(plugin, l, name, true);
        } else if (!ruinNames.isEmpty()) {
            String name = ruinNames.get(random.nextInt(ruinNames.size()));
            ConfigManager cfg = configs.get(name);
            if (cfg != null && l.getBlock().isLiquid() && !cfg.getBoolean("underwater")) return;
            if (cfg != null) l.setY(l.getY() + cfg.getInt("y-offset"));
            StructurePlacer.place(plugin, l, name, false);
        }
    }
}
