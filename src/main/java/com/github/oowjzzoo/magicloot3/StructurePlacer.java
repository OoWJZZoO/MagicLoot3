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

import com.sk89q.worldedit.math.BlockVector3;

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
        if (isWorldEditAvailable()) {
            File file = new File(plugin.getDataFolder(), "schematics/" + name + ".schematic");
            if (file.exists()) {
                debug(plugin, "pasteRuin: WE for " + name);
                BlockVector3[] bbox = pasteWithWorldEdit(plugin, location, file);
                if (bbox != null) {
                    postProcess(plugin, bbox[0], bbox[1], location, false, name);
                    return;
                }
            }
        }
        debug(plugin, "pasteRuin: legacy for " + name);
        Schematic s = RuinBuilder.getSchematic(name);
        if (s != null) Schematic.pasteSchematic(location, s, true);
    }

    public static void pasteBuilding(Plugin plugin, Location location, String name) {
        if (isWorldEditAvailable()) {
            File file = new File(plugin.getDataFolder(), "buildings/" + name + ".schematic");
            if (file.exists()) {
                debug(plugin, "pasteBuilding: WE for " + name);
                BlockVector3[] bbox = pasteWithWorldEdit(plugin, location, file);
                if (bbox != null) {
                    postProcess(plugin, bbox[0], bbox[1], location, true, name);
                    return;
                }
            }
        }
        debug(plugin, "pasteBuilding: legacy for " + name);
        Schematic s = RuinBuilder.getBuilding(name);
        if (s != null) Schematic.pasteSchematic(location, s, false);
    }

    // --- WorldEdit backend ---

    /**
     * Pastes via WorldEdit. Returns [minCorner, maxCorner] from the EditSession's
     * own block-change tracking — the authoritative bounding box of the paste.
     */
    private static BlockVector3[] pasteWithWorldEdit(Plugin plugin, Location loc, File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            var format = com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats.findByFile(file);
            if (format == null) {
                plugin.getLogger().warning("Unknown format: " + file.getName());
                return null;
            }
            var reader = format.getReader(fis);
            var clipboard = reader.read();

            var weWorld = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(loc.getWorld());
            try (var session = com.sk89q.worldedit.WorldEdit.getInstance().newEditSession(weWorld)) {
                var holder = new com.sk89q.worldedit.session.ClipboardHolder(clipboard);
                var pasteOp = holder.createPaste(session)
                        .to(BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()))
                        .ignoreAirBlocks(false)
                        .build();
                com.sk89q.worldedit.function.operation.Operations.complete(pasteOp);

                // EditSession tracks the bounding box of all changes
                return new BlockVector3[] {
                        session.getMinimumPoint(),
                        session.getMaximumPoint()
                };
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "WE paste failed: " + e.getMessage());
            return null;
        }
    }

    // --- Post-processing ---

    /**
     * Scan the exact region that WorldEdit modified, as reported by the
     * EditSession's own block-change tracking. No guessing, no calculating.
     */
    private static void postProcess(Plugin plugin,
                                      BlockVector3 min, BlockVector3 max,
                                      Location loc, boolean isBuilding, String name) {
        int minX = min.x(), minY = min.y(), minZ = min.z();
        int maxX = max.x(), maxY = max.y(), maxZ = max.z();
        int dx = maxX - minX + 1;
        int dz = maxZ - minZ + 1;

        var world = loc.getWorld();
        boolean[][] chestDone = new boolean[dx + 1][dz + 1];
        int chestCount = 0, doubleCount = 0;

        debug(plugin, "postProcess " + name
                + ": min=(" + minX + "," + minY + "," + minZ + ")"
                + " max=(" + maxX + "," + maxY + "," + maxZ + ")"
                + " size=" + dx + "×" + (maxY - minY + 1) + "×" + dz);

        for (int x = 0; x < dx; ++x) {
            for (int y = 0; y <= maxY - minY; ++y) {
                for (int z = 0; z < dz; ++z) {
                    Block block = world.getBlockAt(minX + x, minY + y, minZ + z);
                    Material type = block.getType();

                    if (type == Material.CHEST || type == Material.TRAPPED_CHEST) {
                        if (chestDone[x][z]) continue;
                        chestDone[x][z] = true;
                        chestCount++;

                        block.getState(true);
                        if (block.getState() instanceof Chest) {
                            ItemManager.fillChest(block);
                        }

                        // +X neighbor
                        if (x + 1 < dx) {
                            Block nx = world.getBlockAt(minX + x + 1, minY + y, minZ + z);
                            if (nx.getType() == type && !chestDone[x + 1][z]) {
                                chestDone[x + 1][z] = true;
                                nx.getState(true);
                                if (nx.getState() instanceof Chest) ItemManager.fillChest(nx);
                                if (connectDoubleChest(block, nx)) doubleCount++;
                            }
                        }
                        // +Z neighbor
                        if (z + 1 < dz) {
                            Block nz = world.getBlockAt(minX + x, minY + y, minZ + z + 1);
                            if (nz.getType() == type && !chestDone[x][z + 1]) {
                                chestDone[x][z + 1] = true;
                                nz.getState(true);
                                if (nz.getState() instanceof Chest) ItemManager.fillChest(nz);
                                if (connectDoubleChest(block, nz)) doubleCount++;
                            }
                        }
                    } else if (isBuilding && type == Material.EMERALD_BLOCK) {
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
        debug(plugin, "  " + chestCount + " chests (" + doubleCount + " double)");
    }

    private static boolean connectDoubleChest(Block a, Block b) {
        boolean aIsLeft = a.getX() < b.getX() || a.getZ() < b.getZ();
        Block left = aIsLeft ? a : b;
        Block right = aIsLeft ? b : a;

        if (left.getBlockData() instanceof org.bukkit.block.data.type.Chest ld
                && right.getBlockData() instanceof org.bukkit.block.data.type.Chest rd) {
            ld.setType(Type.LEFT);
            rd.setType(Type.RIGHT);
            left.setBlockData(ld, false);
            right.setBlockData(rd, false);
            return true;
        }
        return false;
    }

    // --- NBT resources (future vanilla fallback) ---

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

    static void debug(Plugin plugin, String msg) {
        if (MagicLoot3.isDebug()) {
            plugin.getLogger().info("[DEBUG] " + msg);
        }
    }
}
