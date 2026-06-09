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
            var tile = state.getClass().getMethod("getHandle").invoke(state);
            var field = getField(tile.getClass(), "can_summon", "canSummon");
            field.setAccessible(true);
            field.setBoolean(tile, true);
            state.update();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static java.lang.reflect.Field getField(Class<?> clazz, String... names)
            throws NoSuchFieldException {
        for (String name : names) {
            try { return clazz.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(String.join(", ", names));
    }
}
