package com.github.oowjzzoo.magicloot3.items;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.github.oowjzzoo.magicloot3.ItemKeys;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;

public class GemStone extends SlimefunItem {

    public record Attr(String name, double value, Operation op) {}

    static final Map<String, Operation> OP_MAP = Map.of(
        "ADD", Operation.ADD_NUMBER,
        "MUL", Operation.ADD_SCALAR,
        "FIN", Operation.MULTIPLY_SCALAR_1
    );

    public GemStone(ItemGroup group, SlimefunItemStack item, String attrs, String slots, int capacity, int cost) {
        super(group, item, RecipeType.NULL, new ItemStack[9]);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ItemKeys.GEM_ATTRS, PersistentDataType.STRING, attrs);
        meta.getPersistentDataContainer().set(ItemKeys.GEM_SLOTS, PersistentDataType.STRING, slots);
        meta.getPersistentDataContainer().set(ItemKeys.GEM_CAPACITY, PersistentDataType.INTEGER, capacity);
        meta.getPersistentDataContainer().set(ItemKeys.GEM_COST, PersistentDataType.INTEGER, cost);
        item.setItemMeta(meta);
    }

    public static boolean isGem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(ItemKeys.GEM_ATTRS);
    }

    /** Parse "attack_damage:ADD:5.0,speed:MUL:0.02" → ordered map of Attr. */
    public static Map<String, Attr> getAttrs(ItemStack item) {
        Map<String, Attr> map = new LinkedHashMap<>();
        if (item == null || !item.hasItemMeta()) return map;
        String data = item.getItemMeta().getPersistentDataContainer().get(ItemKeys.GEM_ATTRS, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) return map;
        for (String entry : data.split(",")) {
            String[] parts = entry.split(":");
            if (parts.length != 3) continue;
            Operation op = OP_MAP.get(parts[1]);
            if (op == null) continue;
            try { map.put(parts[0], new Attr(parts[0], Double.parseDouble(parts[2]), op)); }
            catch (NumberFormatException ignored) {}
        }
        return map;
    }

    public static String getSlots(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(ItemKeys.GEM_SLOTS, PersistentDataType.STRING);
    }

    public static int getCapacity(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer v = item.getItemMeta().getPersistentDataContainer().get(ItemKeys.GEM_CAPACITY, PersistentDataType.INTEGER);
        return v != null ? v : 0;
    }

    public static int getCost(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer v = item.getItemMeta().getPersistentDataContainer().get(ItemKeys.GEM_COST, PersistentDataType.INTEGER);
        return v != null ? v : 0;
    }
}
