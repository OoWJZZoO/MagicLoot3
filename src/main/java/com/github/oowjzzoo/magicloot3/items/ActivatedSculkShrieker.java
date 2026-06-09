package com.github.oowjzzoo.magicloot3.items;

import javax.annotation.Nonnull;

import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;

public class ActivatedSculkShrieker extends SimpleSlimefunItem<BlockPlaceHandler> {

    public ActivatedSculkShrieker(ItemGroup itemGroup, SlimefunItemStack item,
                                   RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull BlockPlaceHandler getItemHandler() {
        return new BlockPlaceHandler(false) {
            @Override
            public void onPlayerPlace(@Nonnull BlockPlaceEvent e) {
                enableWardenSpawning(e.getBlock());
            }
        };
    }

    /** Sets can_summon=true on the placed SculkShrieker's block entity. */
    private static void enableWardenSpawning(org.bukkit.block.Block block) {
        var state = block.getState();
        try {
            // CraftBlockState.getHandle() returns the NMS BlockEntity
            var tile = state.getClass().getMethod("getHandle").invoke(state);
            // Set the canSummon field directly on SculkShriekerBlockEntity
            var field = tile.getClass().getDeclaredField("canSummon");
            field.setAccessible(true);
            field.setBoolean(tile, true);
            state.update();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
