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
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

public final class StructurePlacer {

    /** Default structure names (lowercase). See extractStructures(). */
    private static final String[] DEFAULT_STRUCTURES = {
        "farm", "gas_station", "house", "outpost", "railstation", "shop", "lost_library"
    };

    /** Structures treated as buildings (emerald block → librarian villager). */
    static final java.util.Set<String> BUILDING_NAMES = java.util.Set.of("lost_library");

    private StructurePlacer() {}

    /**
     * Extracts default .nbt files from jar to the plugin's structures/ folder.
     * Uses saveDefaultConfig semantics — only copies if the file doesn't exist.
     */
    public static void extractDefaults(Plugin plugin) {
        File dir = new File(plugin.getDataFolder(), "structures");
        dir.mkdirs();
        for (String name : DEFAULT_STRUCTURES) {
            File dest = new File(dir, name + ".nbt");
            if (dest.exists()) continue;
            try (InputStream in = StructurePlacer.class.getResourceAsStream("/" + name + ".nbt")) {
                if (in == null) continue;
                Files.copy(in, dest.toPath());
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to extract default structure " + name + ": " + e.getMessage());
            }
        }
    }

    /**
     * Copies all .nbt files from the plugin's structures/ folder into the world's
     * generated/magicloot3/structures/ directory (overwriting), so they are
     * loadable via StructureManager.loadStructure(NamespacedKey).
     */
    public static void deployToWorld(Plugin plugin, World world) {
        File srcDir = new File(plugin.getDataFolder(), "structures");
        if (!srcDir.exists()) return;

        File worldDir = world.getWorldFolder();
        File destDir = new File(worldDir, "generated/magicloot3/structures");
        destDir.mkdirs();

        File[] files = srcDir.listFiles((d, n) -> n.endsWith(".nbt"));
        if (files == null) return;

        for (File src : files) {
            File dest = new File(destDir, src.getName());
            try {
                Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to deploy structure " + src.getName()
                        + " to world " + world.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Places a structure using StructureManager with a NamespacedKey under
     * the magicloot3 namespace. The corresponding .nbt must have been deployed
     * to the world's generated/magicloot3/structures/ folder first.
     */
    public static boolean place(Plugin plugin, Location location, String name, boolean isBuilding) {
        NamespacedKey key = new NamespacedKey("magicloot3", name);

        try {
            Structure structure = Bukkit.getStructureManager().loadStructure(key);
            if (structure == null) {
                debug(plugin, "Structure not found: " + key);
                return false;
            }

            structure.place(location, true, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, new Random());
            debug(plugin, "Placed structure: " + name);

            BlockVector size = structure.getSize();
            postProcess(plugin, location, size.getBlockX(), size.getBlockY(), size.getBlockZ(), isBuilding);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to place structure " + name + ": " + e.getMessage());
            return false;
        }
    }

    private static void postProcess(Plugin plugin, Location loc,
                                     int dx, int dy, int dz, boolean isBuilding) {
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
                        // Re-place spawner from scratch to clear structure NBT
                        block.setType(Material.AIR);
                        block.setType(Material.SPAWNER);
                        if (block.getState() instanceof CreatureSpawner spawner) {
                            if (!MagicLootConfig.mobs.isEmpty()) {
                                spawner.setSpawnedType(MagicLootConfig.mobs.get(
                                        random.nextInt(MagicLootConfig.mobs.size())));
                                spawner.setMinSpawnDelay(200);
                                spawner.setMaxSpawnDelay(800);
                                spawner.setSpawnCount(4);
                                spawner.setMaxNearbyEntities(6);
                                spawner.setRequiredPlayerRange(16);
                                spawner.setSpawnRange(4);
                                spawner.setDelay(200);
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
                        v.getPersistentDataContainer().set(ItemKeys.LIBRARIAN,
                                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
                        v.setCustomName(Messages.get("npc.name"));
                        v.setCustomNameVisible(true);
                        v.setAdult();
                        // Spawn item frame 2 blocks below, on the top surface
                        Location frameLoc = block.getLocation().clone().add(0.5, -1.0, 0.5);
                        try {
                            ItemFrame frame = world.spawn(frameLoc, ItemFrame.class);
                            frame.setFacingDirection(BlockFace.UP);
                            SlimefunItem time = SlimefunItem.getById("TIME_OF_EXPLORATION");
                            if (time != null) frame.setItem(time.getItem().clone());
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        debug(plugin, "  chests filled: " + chestCount);
    }

    static void debug(Plugin plugin, String msg) {
        if (MagicLoot3.isDebug()) {
            plugin.getLogger().info("[DEBUG] " + msg);
        }
    }
}
