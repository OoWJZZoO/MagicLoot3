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

    // --- Post-processing after WorldEdit paste ---

    /**
     * Scan the world area that WorldEdit just pasted into. Uses overscan
     * (max of width/length + buffer) in both horizontal axes to tolerate
     * axis-ordering differences between JNBT and WorldEdit.
     */
    private static void postProcessRuin(Plugin plugin, Location loc, Schematic s) {
        int w = s.getWidth(), h = s.getHeight(), l = s.getLenght();
        // Overscan: JNBT and WE may disagree on which axis is Width vs Length
        int scan = Math.max(w, l) + 2;
        var world = loc.getWorld();
        boolean[][] chestDone = new boolean[scan][scan];
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        int chestCount = 0, doubleCount = 0;

        debug(plugin, "postProcessRuin: scanning " + scan + "×" + h + "×" + scan
                + " for " + s.getName() + " (w=" + w + " l=" + l + ")");

        for (int x = 0; x < scan; ++x) {
            for (int y = 0; y < h; ++y) {
                for (int z = 0; z < scan; ++z) {
                    Block block = world.getBlockAt(bx + x, by + y, bz + z);
                    Material type = block.getType();

                    if (type == Material.CHEST || type == Material.TRAPPED_CHEST) {
                        if (chestDone[x][z]) continue;
                        chestDone[x][z] = true;
                        chestCount++;

                        block.getState(true);
                        if (block.getState() instanceof Chest) {
                            ItemManager.fillChest(block);
                            debug(plugin, "postProcessRuin: filled chest at " + x + "," + y + "," + z);
                        }

                        // Check +x neighbor — fill AND connect
                        Block nx = world.getBlockAt(bx + x + 1, by + y, bz + z);
                        if (nx.getType() == type && !chestDone[x + 1][z]) {
                            chestDone[x + 1][z] = true;
                            nx.getState(true);
                            if (nx.getState() instanceof Chest) ItemManager.fillChest(nx);
                            if (connectDoubleChest(block, nx)) doubleCount++;
                        }
                        // Check +z neighbor — fill AND connect
                        Block nz = world.getBlockAt(bx + x, by + y, bz + z + 1);
                        if (nz.getType() == type && !chestDone[x][z + 1]) {
                            chestDone[x][z + 1] = true;
                            nz.getState(true);
                            if (nz.getState() instanceof Chest) ItemManager.fillChest(nz);
                            if (connectDoubleChest(block, nz)) doubleCount++;
                        }
                    }
                }
            }
        }
        debug(plugin, "postProcessRuin: " + chestCount + " chests ("
                + doubleCount + " double) in " + s.getName());
    }

    private static boolean connectDoubleChest(Block a, Block b) {
        // Determine left/right: the block with smaller +x or +z is LEFT
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

    // --- Building post-processing ---

    private static void postProcessBuilding(Plugin plugin, Location loc, Schematic s) {
        int w = s.getWidth(), h = s.getHeight(), l = s.getLenght();
        int scan = Math.max(w, l) + 2;
        var world = loc.getWorld();
        boolean[][] chestDone = new boolean[scan][scan];
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();

        for (int x = 0; x < scan; ++x) {
            for (int y = 0; y < h; ++y) {
                for (int z = 0; z < scan; ++z) {
                    Block block = world.getBlockAt(bx + x, by + y, bz + z);
                    Material type = block.getType();

                    if (type == Material.CHEST || type == Material.TRAPPED_CHEST) {
                        if (!chestDone[x][z]) {
                            chestDone[x][z] = true;
                            block.getState(true);
                            if (block.getState() instanceof Chest) ItemManager.fillChest(block);
                            Block nx = world.getBlockAt(bx + x + 1, by + y, bz + z);
                            if (nx.getType() == type && !chestDone[x + 1][z]) {
                                chestDone[x + 1][z] = true;
                                nx.getState(true);
                                if (nx.getState() instanceof Chest) ItemManager.fillChest(nx);
                                connectDoubleChest(block, nx);
                            }
                            Block nz = world.getBlockAt(bx + x, by + y, bz + z + 1);
                            if (nz.getType() == type && !chestDone[x][z + 1]) {
                                chestDone[x][z + 1] = true;
                                nz.getState(true);
                                if (nz.getState() instanceof Chest) ItemManager.fillChest(nz);
                                connectDoubleChest(block, nz);
                            }
                        }
                    } else if (type == Material.EMERALD_BLOCK) {
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
