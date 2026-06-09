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

    /** Sets can_summon=true on a placed SculkShrieker using the appropriate API. */
    private static void enableWardenSpawning(org.bukkit.block.Block block) {
        // Paper >= 1.20.5: can_summon is block entity NBT, not a block state property.
        // Access via CraftBlockState → getHandle() → load NBT with can_summon:1b.
        var state = block.getState();
        try {
            var getHandle = state.getClass().getMethod("getHandle");
            var tile = getHandle.invoke(state);
            var tileClass = tile.getClass();
            // Load block entity NBT with can_summon = true
            var nbtClass = Class.forName("net.minecraft.nbt.CompoundTag");
            var tag = nbtClass.getDeclaredConstructor().newInstance();
            nbtClass.getMethod("putBoolean", String.class, boolean.class)
                    .invoke(tag, "can_summon", true);
            tileClass.getMethod("loadWithComponents",
                    nbtClass, Class.forName(
                            "net.minecraft.core.HolderLookup$Provider"))
                    .invoke(tile, tag,
                            tileClass.getMethod("levelRegistryAccess").invoke(tile));
            state.update();
        } catch (Exception ex) {
            // Fallback: try pre-1.20.5 API (setCanSummon on SculkShrieker)
            try {
                var m = org.bukkit.block.SculkShrieker.class
                        .getMethod("setCanSummon", boolean.class);
                if (state instanceof org.bukkit.block.SculkShrieker shrieker) {
                    m.invoke(shrieker, true);
                    shrieker.update();
                }
            } catch (Exception ignored) {}
        }
    }
}
