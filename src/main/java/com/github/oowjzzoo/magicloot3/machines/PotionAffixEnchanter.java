package com.github.oowjzzoo.magicloot3.machines;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
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

public class PotionAffixEnchanter extends AContainer {

    private static final int NORMAL_TICKS = 60;
    private static final int DEBUG_TICKS = 1;

    public PotionAffixEnchanter(ItemGroup itemGroup, SlimefunItemStack item,
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
    public String getMachineIdentifier() { return "POTION_AFFIX_ENCHANTER"; }

    @Override
    public ItemStack getProgressBar() { return new ItemStack(Material.ENCHANTED_BOOK); }

    @Override
    protected void registerDefaultRecipes() {
        // Dynamic recipe matching via findNextRecipe
    }

    @Override
    protected MachineRecipe findNextRecipe(BlockMenu menu) {
        ItemStack s19 = menu.getItemInSlot(getInputSlots()[0]);
        ItemStack s20 = menu.getItemInSlot(getInputSlots()[1]);
        if (s19 == null || s20 == null) return null;

        // Identify which slot holds equipment and which holds the affix enchanted book
        ItemStack equipment;
        ItemStack affixBook;
        if (s19.getType() == Material.ENCHANTED_BOOK && AffixTransferUtil.hasPotionAffixes(s19)
                && s20.getType() != Material.ENCHANTED_BOOK) {
            affixBook = s19; equipment = s20;
        } else if (s20.getType() == Material.ENCHANTED_BOOK && AffixTransferUtil.hasPotionAffixes(s20)
                && s19.getType() != Material.ENCHANTED_BOOK) {
            affixBook = s20; equipment = s19;
        } else {
            return null;
        }

        ItemMeta bookMeta = affixBook.getItemMeta();
        String pdcData = bookMeta.getPersistentDataContainer().get(
                ItemKeys.EFFECTS, PersistentDataType.STRING);
        List<EffectEntry> effects = AffixTransferUtil.parseEffects(pdcData);
        if (effects.isEmpty()) return null;

        ItemStack outputEquipment = AffixTransferUtil.appendAffixes(equipment.clone(), effects);
        ItemStack outputBook = buildResultBook(affixBook.clone());

        int ticks = (MagicLoot3.getInstance() != null && MagicLoot3.isDebug())
                ? DEBUG_TICKS : NORMAL_TICKS;

        ItemStack in0 = s19.clone(); in0.setAmount(1);
        ItemStack in1 = s20.clone(); in1.setAmount(1);

        return new MachineRecipe(ticks,
                new ItemStack[]{in0, in1},
                new ItemStack[]{outputEquipment, outputBook});
    }

    private ItemStack buildResultBook(ItemStack book) {
        book = AffixTransferUtil.stripAffixes(book);
        if (book.getItemMeta() instanceof EnchantmentStorageMeta esm
                && esm.getStoredEnchants().isEmpty()) {
            ItemStack normalBook = new ItemStack(Material.BOOK);
            ItemMeta meta = esm;
            if (meta.hasLore()) normalBook.setItemMeta(meta);
            return normalBook;
        }
        return book;
    }
}
