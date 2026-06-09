package com.github.oowjzzoo.magicloot3.items;

import javax.annotation.Nonnull;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.SculkShrieker;
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
                Block block = e.getBlock();
                if (block.getBlockData() instanceof SculkShrieker data) {
                    data.setCanSummon(true);
                    block.setBlockData(data);
                }
            }
        };
    }
}
