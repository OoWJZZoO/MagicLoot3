package com.github.oowjzzoo.magicloot3;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

public class RuinBuilder {

    public static List<Schematic> schematics = new ArrayList<>();
    public static Map<String, ConfigManager> configs = new HashMap<>();
    public static List<Schematic> buildings = new ArrayList<>();
    private static final Map<String, Schematic> schemMap = new HashMap<>();
    private static final Map<String, Schematic> buildingMap = new HashMap<>();
    private static Plugin plugin;

    public static void loadRuins(Plugin pl) throws IOException {
        plugin = pl;
        File dataFolder = plugin.getDataFolder();
        File schematicsDir = new File(dataFolder, "schematics");
        if (!schematicsDir.exists()) schematicsDir.mkdirs();

        for (File file : schematicsDir.listFiles()) {
            if (file.getName().endsWith(".schematic")) {
                Schematic s = Schematic.loadSchematic(file);
                schematics.add(s);
                schemMap.put(s.getName(), s);
                String name = s.getName();
                ConfigManager cfg = new ConfigManager(new File(dataFolder, "ruin_settings/" + name + ".yml"));
                cfg.setDefaultValue("y-offset", 0);
                cfg.setDefaultValue("underwater", false);
                cfg.save();
                configs.put(name, cfg);
            }
        }

        File buildingsDir = new File(dataFolder, "buildings");
        if (!buildingsDir.exists()) buildingsDir.mkdirs();

        for (File file : buildingsDir.listFiles()) {
            if (file.getName().endsWith(".schematic")) {
                Schematic s = Schematic.loadSchematic(file);
                buildings.add(s);
                buildingMap.put(s.getName(), s);
            }
        }
    }

    public static Schematic getSchematic(String name) {
        return schemMap.get(name);
    }

    public static Schematic getBuilding(String name) {
        return buildingMap.get(name);
    }

    public static void buildRuin(Location l) {
        if (schematics.isEmpty()) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (random.nextInt(100) < 4 && !buildings.isEmpty()) {
            Schematic s = buildings.get(random.nextInt(buildings.size()));
            if (l.getBlock().isLiquid()) return;
            StructurePlacer.pasteBuilding(plugin, l, s.getName());
        } else {
            Schematic s = schematics.get(random.nextInt(schematics.size()));
            ConfigManager cfg = configs.get(s.getName());
            if (cfg != null && l.getBlock().isLiquid() && !cfg.getBoolean("underwater")) return;
            if (cfg != null) l.setY(l.getY() + cfg.getInt("y-offset"));
            StructurePlacer.pasteRuin(plugin, l, s.getName());
        }
    }
}
