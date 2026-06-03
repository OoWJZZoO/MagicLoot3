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
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.type.Chest.Type;
import org.bukkit.plugin.Plugin;

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
        if (s == null) {
            debug(plugin, "pasteRuin: no schematic for " + name);
            return;
        }

        if (isWorldEditAvailable()) {
            File schemFile = new File(plugin.getDataFolder(), "schematics/" + name + ".schematic");
            if (schemFile.exists()) {
                debug(plugin, "pasteRuin: using WorldEdit for " + name);
                pasteWithWorldEdit(plugin, location, schemFile);
                postProcessRuin(plugin, location, s);
                return;
            }
        }
        debug(plugin, "pasteRuin: using legacy paste for " + name);
        Schematic.pasteSchematic(location, s, true);
    }

    public static void pasteBuilding(Plugin plugin, Location location, String name) {
        Schematic s = RuinBuilder.getBuilding(name);
        if (s == null) {
            debug(plugin, "pasteBuilding: no schematic for " + name);
            return;
        }

        if (isWorldEditAvailable()) {
            File schemFile = new File(plugin.getDataFolder(), "buildings/" + name + ".schematic");
            if (schemFile.exists()) {
                debug(plugin, "pasteBuilding: using WorldEdit for " + name);
                pasteWithWorldEdit(plugin, location, schemFile);
                postProcessBuilding(plugin, location, s);
                return;
            }
        }
        debug(plugin, "pasteBuilding: using legacy paste for " + name);
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

    // --- Post-processing after WorldEdit paste (single-pass) ---

    /**
     * After WE paste: fill chests, fix double chest connections.
     * Detects chests by Material name rather than hardcoded legacy IDs.
     */
    private static void postProcessRuin(Plugin plugin, Location loc, Schematic s) {
        short[] blocks = s.getBlocks();
        short w = s.getWidth(), h = s.getHeight(), l = s.getLenght();
        var world = loc.getWorld();
        boolean[][] chestDone = new boolean[w][l];
        int chestCount = 0, doubleCount = 0;

        for (int x = 0; x < w; ++x) {
            for (int y = 0; y < h; ++y) {
                for (int z = 0; z < l; ++z) {
                    int idx = y * w * l + z * w + x;
                    Material mat = Schematic.getMaterialById(blocks[idx]);
                    if (mat == null || mat == Material.AIR) continue;

                    Block block = world.getBlockAt(loc.getBlockX() + x, loc.getBlockY() + y, loc.getBlockZ() + z);

                    if (mat == Material.CHEST || mat == Material.TRAPPED_CHEST) {
                        if (chestDone[x][z]) continue;
                        chestDone[x][z] = true;
                        chestCount++;

                        // Force tile entity refresh from NBT (WE paste may not have loaded it)
                        BlockState state = block.getState(true);
                        if (state instanceof Chest chest) {
                            ItemManager.fillChest(block);
                            debug(plugin, "postProcessRuin: filled chest at " + x + "," + y + "," + z);
                        } else {
                            debug(plugin, "postProcessRuin: block at " + x + "," + y + "," + z
                                    + " is " + block.getType() + " but state is " + state.getClass().getSimpleName());
                        }

                        // Fix double chest
                        if (fixDoubleChest(block, blocks, w, l, h, x, y, z, chestDone)) {
                            doubleCount++;
                        }
                    }
                }
            }
        }
        if (chestCount > 0) {
            debug(plugin, "postProcessRuin: processed " + chestCount + " chests ("
                    + doubleCount + " double) in " + s.getName());
        }
    }

    /**
     * Detects and connects adjacent chests. Both schematic neighbors and
     * actual placed blocks are checked for compatibility.
     */
    private static boolean fixDoubleChest(Block block, short[] blocks,
                                           short w, short l, short h,
                                           int x, int y, int z, boolean[][] chestDone) {
        Material mat = block.getType();
        // Check +x direction
        if (x + 1 < w) {
            Material neighborMat = Schematic.getMaterialById(blocks[y * w * l + z * w + (x + 1)]);
            if (isChestMaterial(neighborMat)) {
                chestDone[x + 1][z] = true;
                Block other = block.getWorld().getBlockAt(block.getX() + 1, block.getY(), block.getZ());
                other.getState(true); // force tile entity load
                return applyDoubleChest(block, other);
            }
        }
        // Check +z direction
        if (z + 1 < l) {
            Material neighborMat = Schematic.getMaterialById(blocks[y * w * l + (z + 1) * w + x]);
            if (isChestMaterial(neighborMat)) {
                chestDone[x][z + 1] = true;
                Block other = block.getWorld().getBlockAt(block.getX(), block.getY(), block.getZ() + 1);
                other.getState(true);
                return applyDoubleChest(block, other);
            }
        }
        return false;
    }

    private static boolean isChestMaterial(Material m) {
        return m == Material.CHEST || m == Material.TRAPPED_CHEST;
    }

    private static boolean applyDoubleChest(Block chestA, Block chestB) {
        Material matA = chestA.getType();
        Material matB = chestB.getType();
        if (matA != matB) return false;
        if (matA != Material.CHEST && matA != Material.TRAPPED_CHEST) return false;

        // Figure out left/right based on block position
        // If chestA is west (-x) or north (-z) of chestB, it's LEFT
        boolean aIsLeft = chestA.getX() < chestB.getX() || chestA.getZ() < chestB.getZ();
        Block left = aIsLeft ? chestA : chestB;
        Block right = aIsLeft ? chestB : chestA;

        if (left.getBlockData() instanceof org.bukkit.block.data.type.Chest leftData
                && right.getBlockData() instanceof org.bukkit.block.data.type.Chest rightData) {
            leftData.setType(Type.LEFT);
            rightData.setType(Type.RIGHT);
            left.setBlockData(leftData, false);
            right.setBlockData(rightData, false);
            return true;
        }
        return false;
    }

    // --- Building post-processing (chests + NPC spawning) ---

    private static void postProcessBuilding(Plugin plugin, Location loc, Schematic s) {
        short[] blocks = s.getBlocks();
        short w = s.getWidth(), h = s.getHeight(), l = s.getLenght();
        var world = loc.getWorld();
        boolean[][] chestDone = new boolean[w][l];

        for (int x = 0; x < w; ++x) {
            for (int y = 0; y < h; ++y) {
                for (int z = 0; z < l; ++z) {
                    int idx = y * w * l + z * w + x;
                    Material mat = Schematic.getMaterialById(blocks[idx]);
                    if (mat == null || mat == Material.AIR) continue;

                    Block block = world.getBlockAt(loc.getBlockX() + x, loc.getBlockY() + y, loc.getBlockZ() + z);

                    if (isChestMaterial(mat)) {
                        if (!chestDone[x][z]) {
                            chestDone[x][z] = true;
                            BlockState state = block.getState(true);
                            if (state instanceof Chest) {
                                ItemManager.fillChest(block);
                            }
                            fixDoubleChest(block, blocks, w, l, h, x, y, z, chestDone);
                        }
                    } else if (mat == Material.EMERALD_BLOCK && block.getType() == Material.EMERALD_BLOCK) {
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
                        debug(plugin, "postProcessBuilding: spawned Lost Librarian at " + x + "," + y + "," + z);
                    }
                }
            }
        }
    }

    // --- NBT resource loading ---

    static void copyNbtResources(Plugin plugin) {
        String[] names = {"Farm", "GasStation", "House", "Outpost", "Railstation", "Shop", "Lost_Library"};
        File destDir = new File(plugin.getDataFolder(), "structures");
        destDir.mkdirs();
        for (String name : names) {
            String resourcePath = "/" + name + ".nbt";
            File dest = new File(destDir, name + ".nbt");
            if (dest.exists()) continue;
            try (InputStream in = StructurePlacer.class.getResourceAsStream(resourcePath)) {
                if (in == null) continue;
                Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {}
        }
    }

    // --- Debug logging ---

    static void debug(Plugin plugin, String msg) {
        if (MagicLoot3.isDebug()) {
            plugin.getLogger().info("[DEBUG] " + msg);
        }
    }
}
