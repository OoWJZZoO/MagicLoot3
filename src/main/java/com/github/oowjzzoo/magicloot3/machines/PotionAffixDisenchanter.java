package com.github.oowjzzoo.magicloot3.machines;

import java.util.List;

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
        setEnergyConsumption(9);
        setCapacity(128);
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
    protected void registerDefaultRecipes() {
        // Dynamic recipe matching via findNextRecipe
    }

    @Override
    protected MachineRecipe findNextRecipe(BlockMenu menu) {
        ItemStack equipment = menu.getItemInSlot(getInputSlots()[0]); // slot 19
        ItemStack book = menu.getItemInSlot(getInputSlots()[1]);      // slot 20

        if (equipment == null || book == null) return null;
        if (book.getType() != Material.BOOK) return null;
        if (!AffixTransferUtil.hasPotionAffixes(equipment)) return null;

        // Parse effects from equipment
        ItemMeta eqMeta = equipment.getItemMeta();
        String pdcData = eqMeta.getPersistentDataContainer().get(
                ItemKeys.EFFECTS, PersistentDataType.STRING);
        List<EffectEntry> effects = AffixTransferUtil.parseEffects(pdcData);
        if (effects.isEmpty()) return null;

        ItemStack outputEquipment = AffixTransferUtil.stripAffixes(equipment.clone());
        ItemStack outputBook = AffixTransferUtil.createAffixBook(effects, LootTier.get(equipment));

        int ticks = (MagicLoot3.getInstance() != null && MagicLoot3.isDebug())
                ? DEBUG_TICKS : NORMAL_TICKS;

        return new MachineRecipe(ticks,
                new ItemStack[]{equipment.clone(), book.clone()},
                new ItemStack[]{outputEquipment, outputBook});
    }
}
