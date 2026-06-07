package com.github.oowjzzoo.magicloot3.machines;

import java.io.File;
import java.io.IOException;
import java.util.List;
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

public class LivingDropper extends SlimefunItem {

    private static final Set<Location> locations = ConcurrentHashMap.newKeySet();
    private static final Map<Location, UUID> bindings = new ConcurrentHashMap<>();
    private static File dataFile;

    public LivingDropper(ItemGroup itemGroup, SlimefunItemStack item,
                         RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        addItemHandler(new BlockPlaceHandler(false) {
            @Override
            public void onPlayerPlace(@Nonnull BlockPlaceEvent e) {
                Location loc = e.getBlock().getLocation();
                locations.add(loc);
                saveData();
            }
        });

        addItemHandler(new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(BlockBreakEvent e, ItemStack item, List<ItemStack> drops) {
                Block b = e.getBlock();
                Location loc = b.getLocation();
                drops.clear();
                if (b.getState() instanceof Dropper dropper) {
                    for (ItemStack content : dropper.getInventory().getContents()) {
                        if (content != null && content.getType() != Material.AIR) {
                            drops.add(content);
                        }
                    }
                    dropper.getInventory().clear();
                }
                drops.add(getItem().clone());
                locations.remove(loc);
                bindings.remove(loc);
                saveData();
            }
        });
    }

    // --- Static API ---

    public static boolean isLivingDropper(Location loc) {
        return locations.contains(loc);
    }

    public static UUID getBoundUUID(Location loc) {
        return bindings.get(loc);
    }

    public static Player getBoundPlayer(Location loc) {
        UUID uuid = bindings.get(loc);
        return uuid != null ? Bukkit.getPlayer(uuid) : null;
    }

    public static void bind(Location loc, UUID uuid) {
        bindings.put(loc, uuid);
        saveData();
    }

    public static void unbind(Location loc) {
        bindings.remove(loc);
        saveData();
    }

    // --- Persistence ---

    public static void init(File dataFolder) {
        dataFile = new File(dataFolder, "data" + File.separator + "living_droppers.yml");
        loadData();
    }

    private static void loadData() {
        locations.clear();
        bindings.clear();
        if (!dataFile.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection droppers = yaml.getConfigurationSection("droppers");
        if (droppers == null) return;

        for (String worldName : droppers.getKeys(false)) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;
            ConfigurationSection coords = droppers.getConfigurationSection(worldName);
            if (coords == null) continue;
            for (String key : coords.getKeys(false)) {
                String[] parts = key.split(",");
                if (parts.length != 3) continue;
                try {
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    Location loc = new Location(world, x, y, z);
                    locations.add(loc);
                    String uuidStr = coords.getString(key);
                    if (uuidStr != null && !uuidStr.isEmpty()) {
                        bindings.put(loc, UUID.fromString(uuidStr));
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    private static void saveData() {
        if (dataFile == null) return;
        YamlConfiguration yaml = new YamlConfiguration();

        for (Location loc : locations) {
            String worldName = loc.getWorld().getName();
            String coordKey = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
            UUID uuid = bindings.get(loc);
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
}
