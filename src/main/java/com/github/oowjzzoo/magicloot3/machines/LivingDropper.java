package com.github.oowjzzoo.magicloot3.machines;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Dropper;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.github.oowjzzoo.magicloot3.MagicLoot3;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import me.mrCookieSlime.Slimefun.api.BlockStorage;

public class LivingDropper extends SlimefunItem {

    // Key format: "worldName!x!y!z" — avoids World strong references that prevent GC
    private static final Set<String> locationKeys = ConcurrentHashMap.newKeySet();
    private static final Map<String, UUID> bindings = new ConcurrentHashMap<>();
    private static File dataFile;

    public LivingDropper(ItemGroup itemGroup, SlimefunItemStack item,
                         RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        addItemHandler(new BlockPlaceHandler(false) {
            @Override
            public void onPlayerPlace(@Nonnull BlockPlaceEvent e) {
                String key = locToKey(e.getBlock().getLocation());
                locationKeys.add(key);
                saveData();
            }
        });

        // includeExplosions=true so TNT/creepers don't leave ghost entries
        addItemHandler(new BlockBreakHandler(true, false) {
            @Override
            public void onPlayerBreak(BlockBreakEvent e, ItemStack item, java.util.List<ItemStack> drops) {
                Block b = e.getBlock();
                Location loc = b.getLocation();
                // SF's BlockListener adds sfItem.getDrops() after this handler.
                if (b.getState() instanceof Dropper dropper) {
                    for (ItemStack content : dropper.getInventory().getContents()) {
                        if (content != null && content.getType() != Material.AIR) {
                            drops.add(content);
                        }
                    }
                    dropper.getInventory().clear();
                }
                unregister(loc);
            }
        });
    }

    // --- Key helpers ---

    static String locToKey(Location loc) {
        return loc.getWorld().getName() + "!" + loc.getBlockX() + "!" + loc.getBlockY() + "!" + loc.getBlockZ();
    }

    private static Location keyToLoc(String key) {
        String[] parts = key.split("!");
        if (parts.length != 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            return new Location(world,
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // --- Static API ---

    public static boolean isLivingDropper(Location loc) {
        return locationKeys.contains(locToKey(loc));
    }

    public static UUID getBoundUUID(Location loc) {
        return bindings.get(locToKey(loc));
    }

    public static Player getBoundPlayer(Location loc) {
        UUID uuid = bindings.get(locToKey(loc));
        return uuid != null ? Bukkit.getPlayer(uuid) : null;
    }

    public static void bind(Location loc, UUID uuid) {
        bindings.put(locToKey(loc), uuid);
        saveData();
    }

    public static void unbind(Location loc) {
        bindings.remove(locToKey(loc));
        saveData();
    }

    private static void unregister(Location loc) {
        String key = locToKey(loc);
        locationKeys.remove(key);
        bindings.remove(key);
        saveData();
    }

    // --- Persistence ---

    public static void init(File dataFolder) {
        dataFile = new File(dataFolder, "data" + File.separator + "living_droppers.yml");
        loadData();
        scheduleValidate();
    }

    public static void cleanup() {
        locationKeys.clear();
        bindings.clear();
    }

    private static void loadData() {
        locationKeys.clear();
        bindings.clear();
        if (!dataFile.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection droppers = yaml.getConfigurationSection("droppers");
        if (droppers == null) return;

        for (String worldName : droppers.getKeys(false)) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue; // world deleted — entry silently dropped
            ConfigurationSection coords = droppers.getConfigurationSection(worldName);
            if (coords == null) continue;
            for (String coordKey : coords.getKeys(false)) {
                String key = worldName + "!" + coordKey.replace(",", "!");
                if (world.getBlockAt(
                        Integer.parseInt(coordKey.split(",")[0]),
                        Integer.parseInt(coordKey.split(",")[1]),
                        Integer.parseInt(coordKey.split(",")[2])).getType() != Material.DROPPER) {
                    continue; // block was replaced — skip
                }
                locationKeys.add(key);
                String uuidStr = coords.getString(coordKey);
                if (uuidStr != null && !uuidStr.isEmpty()) {
                    try {
                        bindings.put(key, UUID.fromString(uuidStr));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
    }

    private static void saveData() {
        if (dataFile == null) return;
        YamlConfiguration yaml = new YamlConfiguration();

        for (String key : locationKeys) {
            String[] parts = key.split("!");
            if (parts.length != 4) continue;
            String worldName = parts[0];
            String coordKey = parts[1] + "," + parts[2] + "," + parts[3];
            UUID uuid = bindings.get(key);
            yaml.set("droppers." + worldName + "." + coordKey, uuid != null ? uuid.toString() : "");
        }

        try {
            dataFile.getParentFile().mkdirs();
            yaml.save(dataFile);
        } catch (IOException e) {
            MagicLoot3.getInstance().getLogger().log(Level.WARNING,
                    "Failed to save living dropper data", e);
        }
    }

    private static void scheduleValidate() {
        // Delay 3s: let all worlds/chunks load after startup, then purge ghosts
        Bukkit.getScheduler().runTaskLater(MagicLoot3.getInstance(), () -> {
            int removed = 0;
            for (String key : locationKeys.toArray(new String[0])) {
                Location loc = keyToLoc(key);
                if (loc == null || loc.getWorld() == null) {
                    locationKeys.remove(key);
                    bindings.remove(key);
                    removed++;
                    continue;
                }
                Block block = loc.getBlock();
                if (block.getType() != Material.DROPPER
                        || !BlockStorage.check(block, "LIVING_DROPPER")) {
                    locationKeys.remove(key);
                    bindings.remove(key);
                    removed++;
                }
            }
            if (removed > 0) {
                saveData();
                MagicLoot3.getInstance().getLogger().info(
                        "LivingDropper: purged " + removed + " stale entries");
            }
        }, 60L); // 3 seconds
    }
}
