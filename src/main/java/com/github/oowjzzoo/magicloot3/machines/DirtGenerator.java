package com.github.oowjzzoo.magicloot3.machines;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.github.oowjzzoo.magicloot3.MagicLoot3;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.operations.CraftingOperation;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;

public class DirtGenerator extends AContainer implements RecipeDisplayItem {

    private static final int[] BORDER = {
        0,1,2,3,  5,6,7,8,
        9, 17, 18, 26, 27, 35, 36, 44,
        45,46,47,48,49,50,51,52,53
    };
    private static final int[] OUTPUT = {
        10,11,12,13,14,15,16,
        19,20,21,22,23,24,25,
        28,29,30,31,32,33,34,
        37,38,39,40,41,42,43
    };
    private static final int PROGRESS_SLOT = 4;
    private static final int DIRT_PER_CYCLE = 8;
    private static final int PROCESS_TICKS = 4;
    private static final ItemStack DUMMY_INPUT = new ItemStack(Material.DIRT, 1);

    public DirtGenerator(ItemGroup itemGroup, SlimefunItemStack item,
                          RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        setCapacity(256);
        setEnergyConsumption(16);
        setProcessingSpeed(PROCESS_TICKS);
    }

    @Override
    public String getMachineIdentifier() { return "DIRT_GENERATOR"; }

    @Override
    public ItemStack getProgressBar() { return new ItemStack(Material.DIRT); }

    @Override
    protected void registerDefaultRecipes() {
        registerRecipe(2, new ItemStack[]{new ItemStack(Material.DIRT, 8)},
                new ItemStack[]{new ItemStack(Material.AIR)});
    }

    @Override
    public java.util.@javax.annotation.Nonnull List<ItemStack> getDisplayRecipes() {
        java.util.List<ItemStack> display = new java.util.ArrayList<>(recipes.size() * 2);
        for (MachineRecipe r : recipes) {
            if (r.getInput().length != 1) continue;
            display.add(r.getInput()[0]);
            display.add(r.getOutput()[0]);
        }
        return display;
    }

    @Override
    public int[] getOutputSlots() { return OUTPUT; }

    @Override
    public int[] getInputSlots() { return new int[0]; }

    @Override
    protected void constructMenu(BlockMenuPreset preset) {
        var empty = ChestMenuUtils.getEmptyClickHandler();
        for (int i : BORDER) preset.addItem(i, ChestMenuUtils.getBackground(), empty);

        // Progress bar placeholder at slot 4
        ItemStack pg = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta pgm = pg.getItemMeta(); pgm.setDisplayName(" "); pg.setItemMeta(pgm);
        preset.addItem(PROGRESS_SLOT, pg, empty);
    }

    @Override
    protected void tick(Block b) {
        BlockMenu inv = BlockStorage.getInventory(b);
        CraftingOperation op = getMachineProcessor().getOperation(b);

        if (op != null) {
            if (takeCharge(b.getLocation())) {
                if (!op.isFinished()) {
                    getMachineProcessor().updateProgressBar(inv, PROGRESS_SLOT, op);
                    op.addProgress(1);
                } else {
                    ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
                    ItemMeta pm = pane.getItemMeta(); pm.setDisplayName(" "); pane.setItemMeta(pm);
                    inv.replaceExistingItem(PROGRESS_SLOT, pane);

                    ItemStack[] results = op.getResults();
                    if (fitsAll(inv, results)) {
                        for (ItemStack output : results) {
                            inv.pushItem(output.clone(), OUTPUT);
                        }
                    } else {
                        org.bukkit.Bukkit.getScheduler().runTask(MagicLoot3.getInstance(),
                                () -> {
                                    for (ItemStack result : results) {
                                        b.getWorld().dropItemNaturally(
                                                b.getLocation().add(0.5, 1, 0.5), result.clone());
                                    }
                                });
                    }
                    getMachineProcessor().endOperation(b);
                }
            }
        } else {
            MachineRecipe next = findNextRecipe(inv);
            if (next != null) {
                op = new CraftingOperation(next);
                getMachineProcessor().startOperation(b, op);
                getMachineProcessor().updateProgressBar(inv, PROGRESS_SLOT, op);
            }
        }
    }

    @Override
    protected MachineRecipe findNextRecipe(BlockMenu menu) {
        ItemStack dirt = new ItemStack(Material.DIRT, DIRT_PER_CYCLE);
        MachineRecipe recipe = new MachineRecipe(PROCESS_TICKS,
                new ItemStack[]{DUMMY_INPUT}, new ItemStack[]{dirt});
        if (!fitsAll(menu, recipe.getOutput())) return null;
        return recipe;
    }

    @Override
    protected BlockBreakHandler onBlockBreak() {
        return new SimpleBlockBreakHandler() {
            @Override
            public void onBlockBreak(Block b) {
                BlockMenu inv = BlockStorage.getInventory(b);
                if (inv != null) inv.dropItems(b.getLocation(), OUTPUT);
                getMachineProcessor().endOperation(b);
            }
        };
    }

    private boolean fitsAll(BlockMenu menu, ItemStack[] items) {
        org.bukkit.inventory.Inventory copy = org.bukkit.Bukkit.createInventory(
                null, menu.toInventory().getSize());
        copy.setContents(menu.toInventory().getContents());
        for (ItemStack item : items) {
            if (item == null) continue;
            int remaining = item.getAmount();
            for (int s : OUTPUT) {
                ItemStack existing = copy.getItem(s);
                if (existing == null || existing.getType().isAir()) {
                    copy.setItem(s, item.clone());
                    remaining = 0;
                    break;
                } else if (existing.isSimilar(item)
                        && existing.getAmount() < existing.getMaxStackSize()) {
                    int space = existing.getMaxStackSize() - existing.getAmount();
                    if (space >= remaining) {
                        existing.setAmount(existing.getAmount() + remaining);
                        remaining = 0;
                        break;
                    } else {
                        existing.setAmount(existing.getMaxStackSize());
                        remaining -= space;
                    }
                }
            }
            if (remaining > 0) return false;
        }
        return true;
    }
}
