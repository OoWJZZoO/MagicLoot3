package com.github.oowjzzoo.magicloot3.machines;

import java.util.List;
import java.util.logging.Level;

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
    protected void registerDefaultRecipes() {}

    @Override
    protected MachineRecipe findNextRecipe(BlockMenu menu) {
        ItemStack s19 = menu.getItemInSlot(getInputSlots()[0]);
        ItemStack s20 = menu.getItemInSlot(getInputSlots()[1]);
        if (s19 == null || s20 == null) return null;

        // Identify which slot holds the affix book and which holds equipment.
        // An affix book is ENCHANTED_BOOK with EFFECTS PDC. Equipment is anything else.
        ItemStack equipment;
        ItemStack affixBook;
        boolean s19isBook = s19.getType() == Material.ENCHANTED_BOOK;
        boolean s20isBook = s20.getType() == Material.ENCHANTED_BOOK;
        boolean s19hasAffix = s19isBook && AffixTransferUtil.hasPotionAffixes(s19);
        boolean s20hasAffix = s20isBook && AffixTransferUtil.hasPotionAffixes(s20);

        debug("Enchanter: s19=" + s19.getType() + " isBook=" + s19isBook + " hasAffix=" + s19hasAffix
                + " | s20=" + s20.getType() + " isBook=" + s20isBook + " hasAffix=" + s20hasAffix);

        if (s19hasAffix && !s20isBook) {
            affixBook = s19; equipment = s20;
        } else if (s20hasAffix && !s19isBook) {
            affixBook = s20; equipment = s19;
        } else {
            debug("Enchanter: no valid equipment + affix-book pair found");
            return null;
        }

        ItemMeta bookMeta = affixBook.getItemMeta();
        if (bookMeta == null) {
            debug("Enchanter: book meta is null");
            return null;
        }

        String pdcData = bookMeta.getPersistentDataContainer().get(
                ItemKeys.EFFECTS, PersistentDataType.STRING);
        debug("Enchanter: book PDC EFFECTS = " + pdcData);

        List<EffectEntry> effects = AffixTransferUtil.parseEffects(pdcData);
        if (effects.isEmpty()) {
            debug("Enchanter: parsed effects is empty");
            return null;
        }
        debug("Enchanter: parsed " + effects.size() + " effect(s)");

        ItemStack outputEquipment = AffixTransferUtil.appendAffixes(equipment.clone(), effects);

        // Tier transfer: output tier = max(equipment tier, book tier)
        LootTier equipTier = LootTier.get(equipment);
        LootTier bookTier = LootTier.get(affixBook);
        debug("Enchanter: equipTier=" + equipTier + " bookTier=" + bookTier);
        outputEquipment = AffixTransferUtil.applyTierTransfer(outputEquipment, equipTier, bookTier);

        ItemStack outputBook = buildResultBook(affixBook.clone());

        int ticks = (MagicLoot3.getInstance() != null && MagicLoot3.isDebug())
                ? DEBUG_TICKS : NORMAL_TICKS;

        MachineRecipe recipe = new MachineRecipe(ticks,
                new ItemStack[]{s19.clone(), s20.clone()},
                new ItemStack[]{outputEquipment, outputBook});

        if (!menu.fits(outputEquipment, getOutputSlots())) {
            debug("Enchanter: output equipment doesn't fit");
            return null;
        }
        if (!menu.fits(outputBook, getOutputSlots())) {
            debug("Enchanter: output book doesn't fit");
            return null;
        }

        for (int slot : getInputSlots()) {
            menu.consumeItem(slot);
        }

        debug("Enchanter: recipe created, ticks=" + ticks);
        return recipe;
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

    private static void debug(String msg) {
        if (MagicLoot3.getInstance() != null && MagicLoot3.isDebug()) {
            MagicLoot3.getInstance().getLogger().log(Level.INFO, "[DEBUG] " + msg);
        }
    }
}
