package com.github.oowjzzoo.magicloot3;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;

public class RuinBuilder {

    public static List<Schematic> schematics = new ArrayList<>();
    public static Map<String, ConfigManager> configs = new HashMap<>();
    public static List<Schematic> buildings = new ArrayList<>();

    public static void loadRuins() throws IOException {
        File schematicsDir = new File("plugins/MagicLoot/schematics");
        if (!schematicsDir.exists()) {
            schematicsDir.mkdirs();
        }
        for (File file : schematicsDir.listFiles()) {
            if (file.getName().endsWith(".schematic")) {
                schematics.add(Schematic.loadSchematic(file));
                String name = file.getName().replace(".schematic", "");
                ConfigManager cfg = new ConfigManager(
                        new File("plugins/MagicLoot/ruin_settings/" + name + ".yml"));
                cfg.setDefaultValue("y-offset", 0);
                cfg.setDefaultValue("underwater", false);
                cfg.save();
                configs.put(name, cfg);
            }
        }

        File buildingsDir = new File("plugins/MagicLoot/buildings");
        if (!buildingsDir.exists()) {
            buildingsDir.mkdirs();
        }
        for (File file : buildingsDir.listFiles()) {
            if (file.getName().endsWith(".schematic")) {
                buildings.add(Schematic.loadSchematic(file));
            }
        }
    }

    public static void buildRuin(Location l) {
        if (schematics.isEmpty()) return;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Schematic s = schematics.get(random.nextInt(schematics.size()));

        if (random.nextInt(100) < 4 && !buildings.isEmpty()) {
            // 4% chance: spawn a building (Lost Library)
            s = buildings.get(random.nextInt(buildings.size()));
            if (l.getBlock().isLiquid()) return;
            Schematic.pasteSchematic(l, s, false);
        } else {
            // 96% chance: spawn a regular ruin
            ConfigManager cfg = configs.get(s.getName());
            if (cfg != null && l.getBlock().isLiquid() && !cfg.getBoolean("underwater")) {
                return;
            }
            if (cfg != null) {
                l.setY(l.getY() + cfg.getInt("y-offset"));
            }
            Schematic.pasteSchematic(l, s, true);
        }
    }
}
