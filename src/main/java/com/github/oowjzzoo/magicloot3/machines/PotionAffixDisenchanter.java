package com.github.oowjzzoo.magicloot3.machines;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import com.github.oowjzzoo.magicloot3.ItemKeys;
import com.github.oowjzzoo.magicloot3.LootTier;
import com.github.oowjzzoo.magicloot3.MagicLoot3;
import com.github.oowjzzoo.magicloot3.machines.AffixTransferUtil.EffectEntry;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;

public class PotionAffixDisenchanter extends AContainer {

    public PotionAffixDisenchanter(ItemGroup itemGroup, SlimefunItemStack item,
                                    RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        setCapacity(1024);
        setEnergyConsumption(64);
        setProcessingSpeed(5);
    }

    @Override
    public String getMachineIdentifier() { return "POTION_AFFIX_DISENCHANTER"; }

    @Override
    public ItemStack getProgressBar() { return new ItemStack(Material.ENCHANTED_BOOK); }

    @Override
    protected void registerDefaultRecipes() {}

    @Override
    protected MachineRecipe findNextRecipe(BlockMenu menu) {
        for (int slot : getInputSlots()) {
            ItemStack equipment = menu.getItemInSlot(slot);
            if (!isDisenchantable(equipment)) continue;
            if (!AffixTransferUtil.hasPotionAffixes(equipment)) continue;

            ItemStack book = menu.getItemInSlot(
                    slot == getInputSlots()[0] ? getInputSlots()[1] : getInputSlots()[0]);
            if (book == null || book.getType() != Material.BOOK) continue;

            return createRecipe(menu, equipment, book);
        }
        return null;
    }

    private MachineRecipe createRecipe(BlockMenu menu, ItemStack equipment, ItemStack book) {
        String pdcData = equipment.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.EFFECTS, PersistentDataType.STRING);
        List<EffectEntry> effects = AffixTransferUtil.parseEffects(pdcData);
        if (effects.isEmpty()) return null;

        ItemStack outputEquipment = AffixTransferUtil.stripAffixes(equipment.clone());
        ItemStack outputBook = AffixTransferUtil.createAffixBook(effects, LootTier.get(equipment));

        int ticks = 90 * effects.size() / getSpeed();
        if (ticks < 1) ticks = 1;
        if (MagicLoot3.getInstance() != null && MagicLoot3.isDebug()) ticks = 1;

        MachineRecipe recipe = new MachineRecipe(ticks,
                new ItemStack[]{equipment.clone(), book.clone()},
                new ItemStack[]{outputEquipment, outputBook});

        if (!fitsAll(menu, recipe.getOutput())) {
            return null;
        }

        for (int inputSlot : getInputSlots()) {
            menu.consumeItem(inputSlot);
        }

        return recipe;
    }

    /** Equivalent to {@code InvUtils.fitAll(inv, items, slots)} — simulates pushes. */
    private boolean fitsAll(BlockMenu menu, ItemStack[] items) {
        org.bukkit.inventory.Inventory copy = org.bukkit.Bukkit.createInventory(
                null, menu.toInventory().getSize());
        copy.setContents(menu.toInventory().getContents());
        for (ItemStack item : items) {
            if (item == null) continue;
            int remaining = item.getAmount();
            for (int s : getOutputSlots()) {
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

    /** Mirrors {@code AutoDisenchanter.isDisenchantable()}. */
    private boolean isDisenchantable(ItemStack item) {
        if (item != null && !item.getType().isAir()
                && item.getType() != Material.BOOK) {
            SlimefunItem sfItem = SlimefunItem.getByItem(item);
            return sfItem == null || sfItem.isDisenchantable();
        }
        return false;
    }
}
