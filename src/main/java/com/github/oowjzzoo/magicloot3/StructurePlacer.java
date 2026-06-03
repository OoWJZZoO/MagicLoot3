package com.github.oowjzzoo.magicloot3;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.type.Chest.Type;
import org.bukkit.plugin.Plugin;

/**
 * Places structures into the world. Uses WorldEdit API if available,
 * falling back to legacy .schematic paste when WorldEdit is absent.
 */
public final class StructurePlacer {

    private static boolean weChecked;
    private static Boolean weAvailable;

    private StructurePlacer() {}

    public static boolean isWorldEditAvailable() {
        if (!weChecked) {
            weAvailable = Bukkit.getPluginManager().isPluginEnabled("WorldEdit");
            weChecked = true;
        }
        return weAvailable;
    }

    public static void pasteRuin(Plugin plugin, Location location, String name) {
        Schematic s = RuinBuilder.getSchematic(name);
        if (s == null) return;

        if (isWorldEditAvailable()) {
            File schemFile = new File(plugin.getDataFolder(), "schematics/" + name + ".schematic");
            if (schemFile.exists()) {
                pasteWithWorldEdit(plugin, location, schemFile);
                postProcessRuin(location, s);
                return;
            }
        }
        Schematic.pasteSchematic(location, s, true);
    }

    public static void pasteBuilding(Plugin plugin, Location location, String name) {
        Schematic s = RuinBuilder.getBuilding(name);
        if (s == null) return;

        if (isWorldEditAvailable()) {
            File schemFile = new File(plugin.getDataFolder(), "buildings/" + name + ".schematic");
            if (schemFile.exists()) {
                pasteWithWorldEdit(plugin, location, schemFile);
                postProcessBuilding(location, s);
                return;
            }
        }
        Schematic.pasteSchematic(location, s, false);
    }

    // --- WorldEdit backend ---

    private static void pasteWithWorldEdit(Plugin plugin, Location location, File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            var format = com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats.findByFile(file);
            if (format == null) {
                plugin.getLogger().warning("Unknown schematic format: " + file.getName());
                return;
            }
            var reader = format.getReader(fis);
            var clipboard = reader.read();

            var weWorld = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(location.getWorld());
            try (var session = com.sk89q.worldedit.WorldEdit.getInstance().newEditSession(weWorld)) {
                var holder = new com.sk89q.worldedit.session.ClipboardHolder(clipboard);
                var pasteOp = holder.createPaste(session)
                        .to(com.sk89q.worldedit.math.BlockVector3.at(
                                location.getBlockX(), location.getBlockY(), location.getBlockZ()))
                        .ignoreAirBlocks(false)
                        .build();
                com.sk89q.worldedit.function.operation.Operations.complete(pasteOp);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "WorldEdit paste failed for " + file.getName() + ": " + e.getMessage());
        }
    }

    // --- Post-processing after WorldEdit paste ---

    /**
     * Single-pass post-processing for ruins: fills chests with loot,
     * connects double chests, sets spawner types.
     */
    @SuppressWarnings("deprecation")
    private static void postProcessRuin(Location loc, Schematic s) {
        short[] blocks = s.getBlocks();
        short w = s.getWidth(), h = s.getHeight(), l = s.getLenght();
        var world = loc.getWorld();
        // Track processed chest positions to avoid double-processing
        boolean[][] chestDone = new boolean[w][l];

        for (int x = 0; x < w; ++x) {
            for (int y = 0; y < h; ++y) {
                for (int z = 0; z < l; ++z) {
                    int idx = y * w * l + z * w + x;
                    short id = blocks[idx];
                    if (id == 0) continue;

                    Block block = world.getBlockAt(loc.getBlockX() + x, loc.getBlockY() + y, loc.getBlockZ() + z);

                    if (id == 54 || id == 146) { // CHEST or TRAPPED_CHEST
                        if (chestDone[x][z]) continue;
                        chestDone[x][z] = true;

                        // Fill with loot
                        ItemManager.fillChest(block);

                        // Check for double chest: adjacent chest in +x or +z direction
                        Material chestMat = block.getType();
                        if (chestMat == Material.CHEST || chestMat == Material.TRAPPED_CHEST) {
                            fixDoubleChest(block, blocks, w, l, h, x, y, z, chestDone);
                        }
                    }
                }
            }
        }
    }

    private static void fixDoubleChest(Block block, short[] blocks, short w, short l, short h,
                                        int x, int y, int z, boolean[][] chestDone) {
        // Check +x direction
        if (x + 1 < w && isChestId(blocks[y * w * l + z * w + (x + 1)])) {
            chestDone[x + 1][z] = true;
            Block other = block.getWorld().getBlockAt(block.getX() + 1, block.getY(), block.getZ());
            setDoubleChest(block, other);
            return;
        }
        // Check +z direction
        if (z + 1 < l && isChestId(blocks[y * w * l + (z + 1) * w + x])) {
            chestDone[x][z + 1] = true;
            Block other = block.getWorld().getBlockAt(block.getX(), block.getY(), block.getZ() + 1);
            setDoubleChest(block, other);
        }
    }

    private static boolean isChestId(short id) {
        return id == 54 || id == 146;
    }

    private static void setDoubleChest(Block left, Block right) {
        if (left.getBlockData() instanceof org.bukkit.block.data.type.Chest leftChest
                && right.getBlockData() instanceof org.bukkit.block.data.type.Chest rightChest) {
            leftChest.setType(Type.LEFT);
            left.setBlockData(leftChest);
            rightChest.setType(Type.RIGHT);
            right.setBlockData(rightChest);
        }
    }

    /**
     * Post-processing for buildings: handles chests + Lost Librarian NPC spawning.
     */
    @SuppressWarnings("deprecation")
    private static void postProcessBuilding(Location loc, Schematic s) {
        short[] blocks = s.getBlocks();
        short w = s.getWidth(), h = s.getHeight(), l = s.getLenght();
        var world = loc.getWorld();
        boolean[][] chestDone = new boolean[w][l];

        for (int x = 0; x < w; ++x) {
            for (int y = 0; y < h; ++y) {
                for (int z = 0; z < l; ++z) {
                    int idx = y * w * l + z * w + x;
                    short id = blocks[idx];
                    if (id == 0) continue;

                    Block block = world.getBlockAt(loc.getBlockX() + x, loc.getBlockY() + y, loc.getBlockZ() + z);

                    if (isChestId(id)) {
                        if (!chestDone[x][z]) {
                            chestDone[x][z] = true;
                            ItemManager.fillChest(block);
                            fixDoubleChest(block, blocks, w, l, h, x, y, z, chestDone);
                        }
                    } else if (id == 133 && block.getType() == Material.EMERALD_BLOCK) {
                        // Replace emerald block with Lost Librarian NPC
                        block.setType(Material.AIR);
                        var v = (org.bukkit.entity.Villager) world.spawnEntity(block.getLocation(),
                                org.bukkit.entity.EntityType.VILLAGER);
                        v.setProfession(org.bukkit.entity.Villager.Profession.LIBRARIAN);
                        v.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 255));
                        v.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, -255));
                        v.setCustomName("§5§lLost Librarian");
                        v.setCustomNameVisible(true);
                        v.setAdult();
                    }
                }
            }
        }
    }

    // --- NBT resource loading (for future fallback) ---

    static void copyNbtResources(Plugin plugin) {
        String[] names = {"Farm", "GasStation", "House", "Outpost", "Railstation", "Shop", "Lost_Library"};
        File destDir = new File(plugin.getDataFolder(), "structures");
        destDir.mkdirs();
        for (String name : names) {
            String resourcePath = "/" + name + ".nbt";
            File dest = new File(destDir, name + ".nbt");
            if (dest.exists()) continue;
            try (InputStream in = StructurePlacer.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    continue;
                }
                Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {}
        }
    }
}
