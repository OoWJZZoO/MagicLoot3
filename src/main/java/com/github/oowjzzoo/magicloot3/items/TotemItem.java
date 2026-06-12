package com.github.oowjzzoo.magicloot3.items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.github.oowjzzoo.magicloot3.ItemKeys;
import com.github.oowjzzoo.magicloot3.Messages;

import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

public class TotemItem extends SlimefunItem {

    static final java.util.Map<String, Attribute> ATTR_MAP = new java.util.HashMap<>();
    static {
        ATTR_MAP.put("attack_damage", Attribute.ATTACK_DAMAGE);
        ATTR_MAP.put("entity_interaction_range", Attribute.ENTITY_INTERACTION_RANGE);
        ATTR_MAP.put("max_health", Attribute.MAX_HEALTH);
        ATTR_MAP.put("block_break_speed", Attribute.BLOCK_BREAK_SPEED);
        ATTR_MAP.put("safe_fall_distance", Attribute.SAFE_FALL_DISTANCE);
        ATTR_MAP.put("movement_speed", Attribute.MOVEMENT_SPEED);
        ATTR_MAP.put("jump_strength", Attribute.JUMP_STRENGTH);
        ATTR_MAP.put("step_height", Attribute.STEP_HEIGHT);
    }

    public TotemItem(ItemGroup group, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(group, item, recipeType, recipe);
    }

    @Override
    public void preRegister() {
        addItemHandler((ItemUseHandler) this::onUse);
        super.preRegister();
    }

    private void onUse(PlayerRightClickEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        event.cancel();
        ItemStack totem = player.getInventory().getItemInMainHand();

        // Block GUI for sealed totems
        ItemMeta meta = totem.getItemMeta();
        if (meta != null) {
            Byte sealed = meta.getPersistentDataContainer().get(ItemKeys.TOTEM_SEALED, PersistentDataType.BYTE);
            if (sealed != null && sealed == 1) {
                player.sendMessage(Messages.get("totem.sealed_cannot_open"));
                return;
            }
        }

        Slimefun.runSync(() -> TotemGUI.open(player, totem), 0L);
    }

    /** Read gems from totem PDC (sparse "slot:sfId,..." format) and rebuild all offhand attribute modifiers. */
    public static void rebuildAttributes(ItemStack totem) {
        ItemMeta meta = totem.getItemMeta();
        if (meta == null) return;

        for (Attribute attr : ATTR_MAP.values()) {
            meta.removeAttributeModifier(attr);
        }

        String gemsData = meta.getPersistentDataContainer().get(ItemKeys.TOTEM_GEMS, PersistentDataType.STRING);
        if (gemsData == null || gemsData.isEmpty()) {
            totem.setItemMeta(meta);
            return;
        }

        // Aggregate by "attrName:opCode" composite key to sum same-attribute+same-operation
        Map<String, Double> sums = new LinkedHashMap<>();
        Map<String, GemStone.Attr> metaRef = new LinkedHashMap<>(); // keep one Attr per composite key for op lookup
        for (String entry : gemsData.split(",")) {
            String[] kv = entry.split(":");
            if (kv.length != 2) continue;
            SlimefunItem sfItem = SlimefunItem.getById(kv[1]);
            if (sfItem == null) continue;
            for (GemStone.Attr a : GemStone.getAttrs(sfItem.getItem()).values()) {
                String composite = a.name() + ":" + a.op().name();
                sums.merge(composite, a.value(), Double::sum);
                metaRef.putIfAbsent(composite, a);
            }
        }

        int idx = 0;
        for (Map.Entry<String, Double> e : sums.entrySet()) {
            GemStone.Attr a = metaRef.get(e.getKey());
            Attribute attr = ATTR_MAP.get(a.name());
            if (attr == null) continue;
            meta.addAttributeModifier(attr,
                    new AttributeModifier(
                            new NamespacedKey("magicloot", "totem_" + a.name() + "_" + idx),
                            e.getValue(),
                            a.op(),
                            EquipmentSlotGroup.OFFHAND));
            idx++;
        }

        totem.setItemMeta(meta);
    }

    /** Copy all totem-relevant AttributeModifiers from one ItemMeta to another.
     *  Used by TotemGUI info display to mirror the real totem's stats in the tooltip. */
    public static void copyAttributeModifiers(ItemMeta from, ItemMeta to) {
        if (from == null || to == null) return;
        if (!from.hasAttributeModifiers()) return;
        var map = from.getAttributeModifiers();
        if (map == null) return;
        for (Attribute attr : ATTR_MAP.values()) {
            if (map.containsKey(attr)) {
                for (AttributeModifier mod : map.get(attr)) {
                    to.addAttributeModifier(attr, mod);
                }
            }
        }
    }

    /** Update the lore of a sealed totem to reflect current cost/forge count. */
    public static void updateSealedLore(ItemStack totem) {
        ItemMeta meta = totem.getItemMeta();
        if (meta == null) return;
        Byte sealed = meta.getPersistentDataContainer().get(ItemKeys.TOTEM_SEALED, PersistentDataType.BYTE);
        if (sealed == null || sealed != 1) return;
        int cost = meta.getPersistentDataContainer().getOrDefault(ItemKeys.TOTEM_COST, PersistentDataType.INTEGER, 0);
        int forge = meta.getPersistentDataContainer().getOrDefault(ItemKeys.TOTEM_FORGE_COUNT, PersistentDataType.INTEGER, 0);
        List<String> lore = new ArrayList<>();
        for (String line : Messages.getList("totem.sealed_lore")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line
                    .replace("{cost}", String.valueOf(cost))
                    .replace("{forge}", String.valueOf(forge))));
        }
        meta.setLore(lore);
        totem.setItemMeta(meta);
    }

    /** Increment forge count on a sealed totem. Returns new count, or -1 if not sealed. */
    public static int incrementForgeCount(ItemStack totem) {
        ItemMeta meta = totem.getItemMeta();
        if (meta == null) return -1;
        Byte sealed = meta.getPersistentDataContainer().get(ItemKeys.TOTEM_SEALED, PersistentDataType.BYTE);
        if (sealed == null || sealed != 1) return -1;
        Integer count = meta.getPersistentDataContainer().get(ItemKeys.TOTEM_FORGE_COUNT, PersistentDataType.INTEGER);
        int cur = count != null ? count : 0;
        if (cur >= 30) return -1; // prevent overflow: 1 << 31 is negative
        int newCount = cur + 1;
        meta.getPersistentDataContainer().set(ItemKeys.TOTEM_FORGE_COUNT, PersistentDataType.INTEGER, newCount);
        totem.setItemMeta(meta);
        updateSealedLore(totem);
        return newCount;
    }

    /** Decrement forge count on a sealed totem. Returns new count, or -1 on failure. */
    public static int decrementForgeCount(ItemStack totem) {
        ItemMeta meta = totem.getItemMeta();
        if (meta == null) return -1;
        Byte sealed = meta.getPersistentDataContainer().get(ItemKeys.TOTEM_SEALED, PersistentDataType.BYTE);
        if (sealed == null || sealed != 1) return -1;
        Integer count = meta.getPersistentDataContainer().get(ItemKeys.TOTEM_FORGE_COUNT, PersistentDataType.INTEGER);
        int cur = count != null ? count : 0;
        if (cur <= 0) return 0;
        int newCount = cur - 1;
        meta.getPersistentDataContainer().set(ItemKeys.TOTEM_FORGE_COUNT, PersistentDataType.INTEGER, newCount);
        totem.setItemMeta(meta);
        updateSealedLore(totem);
        return newCount;
    }
}
