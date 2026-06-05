package com.github.oowjzzoo.magicloot3.machines;

import java.util.List;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.github.oowjzzoo.magicloot3.ItemKeys;
import com.github.oowjzzoo.magicloot3.LootTier;
import com.github.oowjzzoo.magicloot3.MagicLoot3;
import com.github.oowjzzoo.magicloot3.machines.AffixTransferUtil.EffectEntry;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;

public class PotionAffixDisenchanter extends AContainer {

    private static final int NORMAL_TICKS = 60;
    private static final int DEBUG_TICKS = 1;

    public PotionAffixDisenchanter(ItemGroup itemGroup, SlimefunItemStack item,
                                    RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        setCapacity(128);
        setEnergyConsumption(9);
        setProcessingSpeed(1);
    }

    @Override
    public int[] getInputSlots() { return new int[]{19, 20}; }

    @Override
    public int[] getOutputSlots() { return new int[]{24, 25}; }

    @Override
    public String getMachineIdentifier() { return "POTION_AFFIX_DISENCHANTER"; }

    @Override
    public ItemStack getProgressBar() { return new ItemStack(Material.ENCHANTED_BOOK); }

    @Override
    protected void registerDefaultRecipes() {}

    @Override
    protected MachineRecipe findNextRecipe(BlockMenu menu) {
        ItemStack s19 = menu.getItemInSlot(getInputSlots()[0]);
        ItemStack s20 = menu.getItemInSlot(getInputSlots()[1]);
        if (s19 == null || s20 == null) return null;

        // Identify which slot holds equipment (has EFFECTS PDC) and which holds a plain BOOK
        ItemStack equipment;
        ItemStack plainBook;
        boolean s19isEquip = AffixTransferUtil.hasPotionAffixes(s19) && s19.getType() != Material.BOOK;
        boolean s20isEquip = AffixTransferUtil.hasPotionAffixes(s20) && s20.getType() != Material.BOOK;
        boolean s19isBook = s19.getType() == Material.BOOK;
        boolean s20isBook = s20.getType() == Material.BOOK;

        debug("Disenchanter: s19=" + s19.getType() + " hasAffix=" + AffixTransferUtil.hasPotionAffixes(s19)
                + " | s20=" + s20.getType() + " hasAffix=" + AffixTransferUtil.hasPotionAffixes(s20));

        if (s19isEquip && s20isBook) {
            equipment = s19; plainBook = s20;
        } else if (s20isEquip && s19isBook) {
            equipment = s20; plainBook = s19;
        } else {
            debug("Disenchanter: no valid equipment + book pair found");
            return null;
        }

        ItemMeta eqMeta = equipment.getItemMeta();
        String pdcData = eqMeta.getPersistentDataContainer().get(
                ItemKeys.EFFECTS, PersistentDataType.STRING);
        debug("Disenchanter: equipment PDC EFFECTS = " + pdcData);

        List<EffectEntry> effects = AffixTransferUtil.parseEffects(pdcData);
        if (effects.isEmpty()) {
            debug("Disenchanter: parsed effects is empty");
            return null;
        }
        debug("Disenchanter: parsed " + effects.size() + " effect(s)");

        ItemStack outputEquipment = AffixTransferUtil.stripAffixes(equipment.clone());
        ItemStack outputBook = AffixTransferUtil.createAffixBook(effects, LootTier.get(equipment));

        int ticks = (MagicLoot3.getInstance() != null && MagicLoot3.isDebug())
                ? DEBUG_TICKS : NORMAL_TICKS;

        MachineRecipe recipe = new MachineRecipe(ticks,
                new ItemStack[]{s19.clone(), s20.clone()},
                new ItemStack[]{outputEquipment, outputBook});

        if (!menu.fits(outputEquipment, getOutputSlots())) {
            debug("Disenchanter: output equipment doesn't fit");
            return null;
        }
        if (!menu.fits(outputBook, getOutputSlots())) {
            debug("Disenchanter: output book doesn't fit");
            return null;
        }

        for (int slot : getInputSlots()) {
            menu.consumeItem(slot);
        }

        debug("Disenchanter: recipe created, ticks=" + ticks);
        return recipe;
    }

    private static void debug(String msg) {
        if (MagicLoot3.getInstance() != null && MagicLoot3.isDebug()) {
            MagicLoot3.getInstance().getLogger().log(Level.INFO, "[DEBUG] " + msg);
        }
    }
}
