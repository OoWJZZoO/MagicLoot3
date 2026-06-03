package com.github.oowjzzoo.magicloot3;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;
import org.bukkit.util.BlockVector;

public final class StructurePlacer {

    private static final String[] RUIN_NAMES = {
        "Farm", "GasStation", "House", "Outpost", "Railstation", "Shop"
    };
    private static final String[] BUILDING_NAMES = { "Lost_Library" };

    private StructurePlacer() {}

    /**
     * Copies .nbt structure files from plugin jar to the plugin's data folder.
     */
    public static void extractStructures(Plugin plugin) {
        File structuresDir = new File(plugin.getDataFolder(), "structures");
        structuresDir.mkdirs();

        String[] allNames = new String[RUIN_NAMES.length + BUILDING_NAMES.length];
        System.arraycopy(RUIN_NAMES, 0, allNames, 0, RUIN_NAMES.length);
        System.arraycopy(BUILDING_NAMES, 0, allNames, RUIN_NAMES.length, BUILDING_NAMES.length);

        for (String name : allNames) {
            File dest = new File(structuresDir, name + ".nbt");
            if (dest.exists()) continue;
            try (InputStream in = StructurePlacer.class.getResourceAsStream("/" + name + ".nbt")) {
                if (in == null) {
                    plugin.getLogger().warning("Structure resource not found: " + name + ".nbt");
                    continue;
                }
                Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to extract structure " + name + ": " + e.getMessage());
            }
        }
    }

    /**
     * Places a structure at the given location using StructureManager.
     */
    public static boolean place(Plugin plugin, Location location, String name, boolean isBuilding) {
        File file = new File(plugin.getDataFolder(), "structures/" + name + ".nbt");
        if (!file.exists()) {
            debug(plugin, "Structure file missing: " + file);
            return false;
        }

        StructureManager manager = Bukkit.getStructureManager();
        try {
            Structure structure = manager.loadStructure(file);
            if (structure == null) {
                debug(plugin, "Failed to load structure: " + name);
                return false;
            }

            structure.place(location, true, StructureRotation.NONE, Mirror.NONE, 1, 0, new Random());
            debug(plugin, "Placed structure: " + name);

            BlockVector size = structure.getSize();
            postProcess(plugin, location, size.getBlockX(), size.getBlockY(), size.getBlockZ(),
                    name, isBuilding);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to place structure " + name + ": " + e.getMessage());
            return false;
        }
    }

    private static void postProcess(Plugin plugin, Location loc,
                                     int dx, int dy, int dz,
                                     String name, boolean isBuilding) {
        if (dx <= 0 || dy <= 0 || dz <= 0 || dx > 256 || dy > 256 || dz > 256) return;

        var world = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int chestCount = 0;

        for (int y = 0; y < dy; y++) {
            for (int x = 0; x < dx; x++) {
                for (int z = 0; z < dz; z++) {
                    Block block = world.getBlockAt(bx + x, by + y, bz + z);
                    Material type = block.getType();

                    if (type == Material.CHEST || type == Material.TRAPPED_CHEST) {
                        ItemManager.fillChest(block);
                        chestCount++;
                    } else if (type == Material.SPAWNER) {
                        if (block.getState() instanceof CreatureSpawner spawner) {
                            if (!MagicLootConfig.mobs.isEmpty()) {
                                spawner.setSpawnedType(MagicLootConfig.mobs.get(
                                        random.nextInt(MagicLootConfig.mobs.size())));
                                spawner.update();
                            }
                        }
                    } else if (isBuilding && type == Material.EMERALD_BLOCK) {
                        block.setType(Material.AIR);
                        Villager v = (Villager) world.spawnEntity(
                                block.getLocation(), EntityType.VILLAGER);
                        v.setProfession(Villager.Profession.LIBRARIAN);
                        v.addPotionEffect(new PotionEffect(
                                PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 255));
                        v.addPotionEffect(new PotionEffect(
                                PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, -255));
                        v.setCustomName("§5§lLost Librarian");
                        v.setCustomNameVisible(true);
                        v.setAdult();
                    }
                }
            }
        }
        debug(plugin, "  " + name + ": " + chestCount + " chests filled");
    }

    static void debug(Plugin plugin, String msg) {
        if (MagicLoot3.isDebug()) {
            plugin.getLogger().info("[DEBUG] " + msg);
        }
    }
}
