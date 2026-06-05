package com.github.oowjzzoo.magicloot3.machines;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
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

public class PotionAffixEnchanter extends AContainer {

    public PotionAffixEnchanter(ItemGroup itemGroup, SlimefunItemStack item,
                                 RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        setCapacity(128);
        setEnergyConsumption(9);
        setProcessingSpeed(1);
    }

    @Override
    public String getMachineIdentifier() { return "POTION_AFFIX_ENCHANTER"; }

    @Override
    public ItemStack getProgressBar() { return new ItemStack(Material.ENCHANTED_BOOK); }

    @Override
    protected void registerDefaultRecipes() {}

    @Override
    protected MachineRecipe findNextRecipe(BlockMenu menu) {
        for (int slot : getInputSlots()) {
            ItemStack book = menu.getItemInSlot(slot);
            if (book == null || book.getType() != Material.ENCHANTED_BOOK
                    || !AffixTransferUtil.hasPotionAffixes(book)) continue;

            ItemStack equipment = menu.getItemInSlot(
                    slot == getInputSlots()[0] ? getInputSlots()[1] : getInputSlots()[0]);
            if (!isEnchantable(equipment)) continue;

            return createRecipe(menu, equipment, book);
        }
        return null;
    }

    private MachineRecipe createRecipe(BlockMenu menu, ItemStack equipment, ItemStack affixBook) {
        String pdcData = affixBook.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.EFFECTS, PersistentDataType.STRING);
        List<EffectEntry> effects = AffixTransferUtil.parseEffects(pdcData);
        if (effects.isEmpty()) return null;

        ItemStack outputEquipment = AffixTransferUtil.appendAffixes(equipment.clone(), effects);

        LootTier equipTier = LootTier.get(equipment);
        LootTier bookTier = LootTier.get(affixBook);
        outputEquipment = AffixTransferUtil.applyTierTransfer(outputEquipment, equipTier, bookTier);

        ItemStack outputBook = buildResultBook(affixBook.clone());

        int ticks = 75 * effects.size() / getSpeed();
        if (ticks < 1) ticks = 1;
        if (MagicLoot3.getInstance() != null && MagicLoot3.isDebug()) ticks = 1;

        MachineRecipe recipe = new MachineRecipe(ticks,
                new ItemStack[]{equipment.clone(), affixBook.clone()},
                new ItemStack[]{outputEquipment, outputBook});

        if (!fitsAll(menu, recipe.getOutput())) {
            return null;
        }

        for (int inputSlot : getInputSlots()) {
            menu.consumeItem(inputSlot);
        }

        return recipe;
    }

    private ItemStack buildResultBook(ItemStack book) {
        book = AffixTransferUtil.stripAffixes(book);
        if (book.getItemMeta() instanceof EnchantmentStorageMeta esm
                && esm.getStoredEnchants().isEmpty()) {
            return new ItemStack(Material.BOOK);
        }
        return book;
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

    /** Mirrors {@code AutoEnchanter.isEnchantable()}. */
    private boolean isEnchantable(ItemStack item) {
        if (item != null && item.getType() != Material.ENCHANTED_BOOK
                && !item.getType().isAir()) {
            SlimefunItem sfItem = SlimefunItem.getByItem(item);
            return sfItem == null || sfItem.isEnchantable();
        }
        return false;
    }
}
