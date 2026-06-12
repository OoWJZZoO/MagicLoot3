package com.github.oowjzzoo.magicloot3.items;

import java.util.Collection;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.github.oowjzzoo.magicloot3.ItemKeys;
import com.github.oowjzzoo.magicloot3.Messages;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemDropHandler;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;

public class MediumRune extends SimpleSlimefunItem<ItemDropHandler> {

    private static final double RANGE = 1.5;
    private final int mediumValue;

    @ParametersAreNonnullByDefault
    public MediumRune(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe,
                      ItemStack recipeOutput, int mediumValue) {
        super(itemGroup, item, recipeType, recipe, recipeOutput);
        this.mediumValue = mediumValue;
    }

    @Override
    public @Nonnull ItemDropHandler getItemHandler() {
        return (e, p, item) -> {
            if (isItem(item.getItemStack())) {
                if (canUse(p, true)) {
                    Slimefun.runSync(() -> activate(p, item), 20L);
                }
                return true;
            }
            return false;
        };
    }

    private void activate(@Nonnull Player p, @Nonnull Item runeItem) {
        if (!runeItem.isValid()) return;

        Location l = runeItem.getLocation();
        Collection<Entity> entities = l.getWorld().getNearbyEntities(
                l, RANGE, RANGE, RANGE, this::isSealedTotem);
        Optional<Entity> optional = entities.stream().findFirst();

        if (optional.isEmpty()) {
            p.sendMessage(Messages.get("totem.forge.no_totem"));
            return;
        }

        Item totemEntity = (Item) optional.get();
        ItemStack totemStack = totemEntity.getItemStack();
        ItemMeta totemMeta = totemStack.getItemMeta();
        if (totemMeta == null) return;

        // Read totem cost and forge count
        Integer costObj = totemMeta.getPersistentDataContainer().get(ItemKeys.TOTEM_COST, PersistentDataType.INTEGER);
        Integer forgeObj = totemMeta.getPersistentDataContainer().get(ItemKeys.TOTEM_FORGE_COUNT, PersistentDataType.INTEGER);
        int cost = costObj != null ? costObj : 0;
        int forgeCount = forgeObj != null ? forgeObj : 0;
        if (cost <= 0) return;

        // Calculate required medium value: floor(sqrt(cost)) * 2^forgeCount
        int required = (int) Math.floor(Math.sqrt(cost)) * (1 << forgeCount);

        // Calculate provided medium value
        ItemStack runeStack = runeItem.getItemStack();
        int provided = mediumValue * runeStack.getAmount();

        if (provided < required) {
            p.sendMessage(Messages.get("totem.forge.shortfall", provided, required));
            return;
        }

        // Consume runes (minimum needed)
        int consume = (int) Math.ceil((double) required / mediumValue);

        SoundEffect.ENCHANTMENT_RUNE_ADD_ENCHANT_SOUND.playAt(l, SoundCategory.PLAYERS);

        Slimefun.runSync(() -> {
            if (!runeItem.isValid() || !totemEntity.isValid()) return;

            l.getWorld().strikeLightningEffect(l);

            // Consume runes from stack
            ItemStack rs = runeItem.getItemStack();
            rs.setAmount(rs.getAmount() - consume);
            if (rs.getAmount() <= 0) {
                runeItem.remove();
            } else {
                runeItem.setItemStack(rs);
            }

            // Increment forge count on totem
            int newCount = TotemItem.incrementForgeCount(totemStack);
            if (newCount > 0) {
                totemEntity.setItemStack(totemStack);
                p.sendMessage(Messages.get("totem.forge.success", newCount, consume));
            }
        }, 10L);
    }

    private boolean isSealedTotem(@Nonnull Entity entity) {
        if (!(entity instanceof Item item)) return false;
        ItemStack stack = item.getItemStack();
        if (!stack.hasItemMeta()) return false;
        Byte sealed = stack.getItemMeta().getPersistentDataContainer().get(ItemKeys.TOTEM_SEALED, PersistentDataType.BYTE);
        return sealed != null && sealed == 1;
    }
}
