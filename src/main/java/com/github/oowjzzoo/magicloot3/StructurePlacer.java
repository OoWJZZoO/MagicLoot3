package com.github.oowjzzoo.magicloot3;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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
import org.jnbt.CompoundTag;
import org.jnbt.IntTag;
import org.jnbt.ListTag;
import org.jnbt.NBTInputStream;
import org.jnbt.NBTOutputStream;
import org.jnbt.Tag;

public final class StructurePlacer {

    private static final String[] RUIN_NAMES = {
        "Farm", "GasStation", "House", "Outpost", "Railstation", "Shop"
    };
    private static final String[] BUILDING_NAMES = { "Lost_Library" };

    private StructurePlacer() {}

    /**
     * Copies .nbt structure files from plugin jar to the plugin's data folder,
     * fixing palette tag if needed.
     */
    public static void extractStructures(Plugin plugin) {
        File structuresDir = new File(plugin.getDataFolder(), "structures");
        structuresDir.mkdirs();

        String[] allNames = new String[RUIN_NAMES.length + BUILDING_NAMES.length];
        System.arraycopy(RUIN_NAMES, 0, allNames, 0, RUIN_NAMES.length);
        System.arraycopy(BUILDING_NAMES, 0, allNames, RUIN_NAMES.length, BUILDING_NAMES.length);

        for (String name : allNames) {
            File dest = new File(structuresDir, name + ".nbt");
            if (!dest.exists()) {
                try (InputStream in = StructurePlacer.class.getResourceAsStream("/" + name + ".nbt")) {
                    if (in == null) {
                        plugin.getLogger().warning("Structure resource not found: " + name + ".nbt");
                        continue;
                    }
                    Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to extract structure " + name + ": " + e.getMessage());
                    continue;
                }
            }

            // Always fix NBT structure in case it has broken palette format
            fixNbtStructure(plugin, dest);
        }
    }

    /**
     * Fixes .nbt structure files that have 'palette' (singular, from .schematic)
     * instead of 'palettes' (plural list, required by Minecraft Structure format).
     */
    @SuppressWarnings("unchecked")
    private static boolean fixNbtStructure(Plugin plugin, File file) {
        try (NBTInputStream nbtIn = new NBTInputStream(
                new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))))) {
            Tag rootTag = nbtIn.readTag();
            if (!(rootTag instanceof CompoundTag root)) return false;

            Map<String, Tag> map = new LinkedHashMap<>(root.getValue());
            boolean modified = false;

            // Fix: rename 'palette' (singular) → 'palettes' (list of palettes)
            if (map.containsKey("palette") && !map.containsKey("palettes")) {
                Tag palette = map.remove("palette");
                if (palette instanceof ListTag list) {
                    List<Tag> outerList = new ArrayList<>();
                    outerList.add(list);
                    map.put("palettes", new ListTag("palettes", CompoundTag.class, outerList));
                    modified = true;
                    debug(plugin, "Fixed palette->palettes for " + file.getName());
                }
            }

            // Remove legacy .schematic wrapper if present
            modified |= map.remove("Schematic") != null;
            modified |= map.remove("Blocks") != null;
            modified |= map.remove("Data") != null;
            modified |= map.remove("AddBlocks") != null;
            modified |= map.remove("BlockIDs") != null;
            modified |= map.remove("Materials") != null;

            // Ensure size tag from Width/Height/Length
            if (!map.containsKey("size") && map.containsKey("Width")) {
                int w = ((IntTag) map.remove("Width")).getValue();
                int h = ((IntTag) map.remove("Height")).getValue();
                int l = ((IntTag) map.remove("Length")).getValue();
                List<Tag> sizeList = new ArrayList<>();
                sizeList.add(new IntTag("", w));
                sizeList.add(new IntTag("", h));
                sizeList.add(new IntTag("", l));
                map.put("size", new ListTag("size", IntTag.class, sizeList));
                modified = true;
            }

            if (modified) {
                CompoundTag newRoot = new CompoundTag(root.getName(), map);
                NBTOutputStream nbtOut = new NBTOutputStream(
                        new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(file))));
                nbtOut.writeTag(newRoot);
                nbtOut.close();
                return true;
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("NBT fix error for " + file.getName() + ": " + e.getMessage());
            return false;
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
