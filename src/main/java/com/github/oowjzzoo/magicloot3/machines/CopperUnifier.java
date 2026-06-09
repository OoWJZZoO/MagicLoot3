package com.github.oowjzzoo.magicloot3.machines;

import javax.annotation.Nonnull;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;

public class CopperUnifier extends SlimefunItem implements RecipeDisplayItem {

    private static final int[] CYAN  = {0,1,2, 9,11, 18,19,20};
    private static final int[] GREEN = {6,7,8, 15,17, 24,25,26};
    private static final int[] BORDER = {3,4,5, 12,13,14, 21,22,23};
    private static final int INPUT_SLOT  = 10;
    private static final int OUTPUT_SLOT = 16;
    private static final String SF_COPPER_ID = "COPPER_INGOT";

    private static final ItemStack CYAN_HINT = cyanHint();
    private static final ItemStack GREEN_HINT = greenHint();

    private static ItemStack cyanHint() {
        ItemStack g = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta m = g.getItemMeta();
        m.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&7输入"));
        g.setItemMeta(m);
        return g;
    }

    private static ItemStack greenHint() {
        ItemStack g = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta m = g.getItemMeta();
        m.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&7输出"));
        g.setItemMeta(m);
        return g;
    }

    public CopperUnifier(ItemGroup itemGroup, SlimefunItemStack item,
                          RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public void preRegister() {
        super.preRegister();
        addItemHandler(new BlockTicker() {
            @Override public boolean isSynchronized() { return true; }
            @Override
            public void tick(Block b, SlimefunItem item, Config data) {
                onTick(b);
            }
        });

        new BlockMenuPreset(getId(), getItemName()) {
            @Override public void init() {
                var empty = ChestMenuUtils.getEmptyClickHandler();
                for (int i : CYAN)  addItem(i, CYAN_HINT, empty);
                for (int i : GREEN) addItem(i, GREEN_HINT, empty);
                for (int i : BORDER) addItem(i, ChestMenuUtils.getBackground(), empty);
            }
            @Override public void newInstance(BlockMenu menu, Block b) {}
            @Override public boolean canOpen(Block b, org.bukkit.entity.Player p) { return true; }
            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return new int[]{INPUT_SLOT, OUTPUT_SLOT};
            }
        };

        addItemHandler(new SimpleBlockBreakHandler() {
            @Override
            public void onBlockBreak(Block b) {
                BlockMenu inv = BlockStorage.getInventory(b);
                if (inv != null) {
                    inv.dropItems(b.getLocation(), INPUT_SLOT);
                    inv.dropItems(b.getLocation(), OUTPUT_SLOT);
                }
            }
        });
    }

    @Override
    public @Nonnull java.util.List<ItemStack> getDisplayRecipes() {
        return java.util.List.of(
                SlimefunItems.COPPER_INGOT, new ItemStack(Material.COPPER_INGOT),
                new ItemStack(Material.COPPER_INGOT), SlimefunItems.COPPER_INGOT);
    }

    private void onTick(Block b) {
        BlockMenu menu = BlockStorage.getInventory(b);
        if (menu == null) return;

        ItemStack input = menu.getItemInSlot(INPUT_SLOT);
        if (input == null || input.getType().isAir()) return;

        ItemStack output = menu.getItemInSlot(OUTPUT_SLOT);

        boolean isSf = isSfCopper(input);
        boolean isVanilla = !isSf && input.getType() == Material.COPPER_INGOT;
        if (!isSf && !isVanilla) return;

        int amount = input.getAmount();

        if (isSf) {
            // SF copper → vanilla copper
            ItemStack target = new ItemStack(Material.COPPER_INGOT, amount);
            tryPut(menu, target, amount);
        } else {
            // Vanilla copper → SF copper
            ItemStack target = SlimefunItems.COPPER_INGOT.clone();
            target.setAmount(amount);
            tryPut(menu, target, amount);
        }
    }

    private void tryPut(BlockMenu menu, ItemStack target, int amount) {
        ItemStack output = menu.getItemInSlot(OUTPUT_SLOT);

        if (output == null || output.getType().isAir()) {
            menu.replaceExistingItem(OUTPUT_SLOT, target);
            menu.replaceExistingItem(INPUT_SLOT, null);
        } else if (target.isSimilar(output) && output.getAmount() < output.getMaxStackSize()) {
            int space = output.getMaxStackSize() - output.getAmount();
            int move = Math.min(amount, space);
            if (move > 0) {
                ItemStack newOutput = output.clone();
                newOutput.setAmount(output.getAmount() + move);
                menu.replaceExistingItem(OUTPUT_SLOT, newOutput);
                if (amount == move) {
                    menu.replaceExistingItem(INPUT_SLOT, null);
                } else {
                    ItemStack input = menu.getItemInSlot(INPUT_SLOT);
                    if (input != null) {
                        ItemStack newInput = input.clone();
                        newInput.setAmount(amount - move);
                        menu.replaceExistingItem(INPUT_SLOT, newInput);
                    }
                }
            }
        }
        // else: output occupied by incompatible item, do nothing
    }

    private boolean isSfCopper(ItemStack item) {
        SlimefunItem sfi = SlimefunItem.getByItem(item);
        return sfi != null && SF_COPPER_ID.equals(sfi.getId());
    }
}
