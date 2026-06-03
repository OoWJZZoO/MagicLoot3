package com.github.oowjzzoo.magicloot3;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jnbt.ByteArrayTag;
import org.jnbt.CompoundTag;
import org.jnbt.NBTInputStream;
import org.jnbt.ShortTag;
import org.jnbt.Tag;

public class Schematic {

    private final short[] blocks;
    private final byte[] data;
    private final short width;
    private final short lenght;
    private final short height;
    private final String name;

    public Schematic(String name, short[] blocks, byte[] data, short width, short lenght, short height) {
        this.blocks = blocks;
        this.data = data;
        this.width = width;
        this.lenght = lenght;
        this.height = height;
        this.name = name;
    }

    public short[] getBlocks() {
        return blocks;
    }

    public String getName() {
        return name;
    }

    public byte[] getData() {
        return data;
    }

    public short getWidth() {
        return width;
    }

    public short getLenght() {
        return lenght;
    }

    public short getHeight() {
        return height;
    }

    @SuppressWarnings("deprecation")
    public static void pasteSchematic(Location loc, Schematic schematic, boolean ordinary) {
        BlockFace[] bf = {
                BlockFace.NORTH, BlockFace.NORTH_EAST, BlockFace.EAST,
                BlockFace.SOUTH_EAST, BlockFace.SOUTH, BlockFace.SOUTH_WEST,
                BlockFace.WEST, BlockFace.NORTH_WEST
        };

        short[] blocks = schematic.getBlocks();
        byte[] blockData = schematic.getData();

        short length = schematic.getLenght();
        short width = schematic.getWidth();
        short height = schematic.getHeight();

        for (int x = 0; x < width; ++x) {
            for (int y = 0; y < height; ++y) {
                for (int z = 0; z < length; ++z) {
                    int index = y * width * length + z * width + x;
                    Block block = new Location(loc.getWorld(), x + loc.getX(), y + loc.getY(), z + loc.getZ()).getBlock();
                    Material material = getMaterialById(blocks[index]);
                    if (material == null) continue;

                    // Skip placing air over water/lava (preserve terrain liquids)
                    if (blocks[index] == 0 && (block.getType() == Material.WATER || block.getType() == Material.LAVA)) {
                        continue;
                    }

                    block.setType(material);

                    // Apply legacy data byte for blocks that need it
                    if (blockData[index] != 0) {
                        try {
                            block.setBlockData(Bukkit.getUnsafe().fromLegacy(material, blockData[index]), false);
                        } catch (Exception ignored) {
                            // Some blocks don't support legacy data conversion
                        }
                    }

                    if (material == Material.CHEST || material == Material.TRAPPED_CHEST) {
                        ItemManager.fillChest(block);
                    } else if (material == Material.PLAYER_HEAD || material == Material.SKELETON_SKULL
                            || material == Material.ZOMBIE_HEAD || material == Material.CREEPER_HEAD) {
                        if (block.getBlockData() instanceof Rotatable rotatable) {
                            rotatable.setRotation(bf[new Random().nextInt(bf.length)]);
                            block.setBlockData(rotatable);
                        }
                    } else if (material == Material.SPAWNER) {
                        if (block.getState() instanceof CreatureSpawner spawner) {
                            spawner.setSpawnedType(MagicLootConfig.mobs.get(
                                    ThreadLocalRandom.current().nextInt(MagicLootConfig.mobs.size())));
                            spawner.update();
                        }
                    } else if (!ordinary && material == Material.EMERALD_BLOCK) {
                        block.setType(Material.AIR);
                        Villager v = (Villager) block.getWorld().spawnEntity(block.getLocation(), EntityType.VILLAGER);
                        v.setProfession(Profession.LIBRARIAN);
                        v.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 255));
                        v.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, -255));
                        v.setCustomName("§5§lLost Librarian");
                        v.setCustomNameVisible(true);
                        v.setAdult();
                    }
                }
            }
        }
    }

    @SuppressWarnings("resource")
    public static Schematic loadSchematic(File file) throws IOException {
        FileInputStream stream = new FileInputStream(file);
        NBTInputStream nbtStream = new NBTInputStream(stream);

        CompoundTag schematicTag = (CompoundTag) nbtStream.readTag();
        if (!schematicTag.getName().equals("Schematic")) {
            nbtStream.close();
            throw new IllegalArgumentException("Tag \"Schematic\" does not exist or is not first");
        }

        Map<String, Tag> schematic = schematicTag.getValue();
        if (!schematic.containsKey("Blocks")) {
            nbtStream.close();
            throw new IllegalArgumentException("Schematic file is missing a \"Blocks\" tag");
        }

        short width = getChildTag(schematic, "Width", ShortTag.class).getValue();
        short length = getChildTag(schematic, "Length", ShortTag.class).getValue();
        short height = getChildTag(schematic, "Height", ShortTag.class).getValue();

        byte[] blockId = getChildTag(schematic, "Blocks", ByteArrayTag.class).getValue();
        byte[] blockData = getChildTag(schematic, "Data", ByteArrayTag.class).getValue();
        byte[] addId = new byte[0];
        short[] blocks = new short[blockId.length];

        if (schematic.containsKey("AddBlocks")) {
            addId = getChildTag(schematic, "AddBlocks", ByteArrayTag.class).getValue();
        }

        for (int index = 0; index < blockId.length; index++) {
            if ((index >> 1) >= addId.length) {
                blocks[index] = (short) (blockId[index] & 0xFF);
            } else {
                if ((index & 1) == 0) {
                    blocks[index] = (short) (((addId[index >> 1] & 0x0F) << 8) + (blockId[index] & 0xFF));
                } else {
                    blocks[index] = (short) (((addId[index >> 1] & 0xF0) << 4) + (blockId[index] & 0xFF));
                }
            }
        }

        nbtStream.close();
        return new Schematic(file.getName().replace(".schematic", ""), blocks, blockData, width, length, height);
    }

    private static <T extends Tag> T getChildTag(Map<String, Tag> items, String key, Class<T> expected) {
        if (!items.containsKey(key)) {
            throw new IllegalArgumentException("Schematic file is missing a \"" + key + "\" tag");
        }
        Tag tag = items.get(key);
        if (!expected.isInstance(tag)) {
            throw new IllegalArgumentException(key + " tag is not of tag type " + expected.getName());
        }
        return expected.cast(tag);
    }

    private static Material getMaterialById(short id) {
        Material[] values = Material.values();
        int index = id & 0xFFFF;
        if (index >= 0 && index < values.length) {
            return values[index];
        }
        return null;
    }
}
