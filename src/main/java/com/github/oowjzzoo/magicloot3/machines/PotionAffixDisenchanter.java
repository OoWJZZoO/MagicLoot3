package com.github.oowjzzoo.magicloot3.machines;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.block.BlockBreakEvent;
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
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;

public class PotionAffixDisenchanter extends AContainer {

    private static final int NORMAL_TICKS = 60;
    private static final int DEBUG_TICKS = 1;
    private static final Map<Location, ItemStack[]> pendingInputs = new HashMap<>();
    private static final Set<Location> brokenLocations = new HashSet<>();

    public PotionAffixDisenchanter(ItemGroup itemGroup, SlimefunItemStack item,
                                    RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        setCapacity(128);
        setEnergyConsumption(9);
        setProcessingSpeed(1);

        addItemHandler(new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(BlockBreakEvent e, ItemStack tool, List<ItemStack> drops) {
                Location loc = e.getBlock().getLocation().clone();
                ItemStack[] pending = pendingInputs.remove(loc);
                if (pending != null) {
                    for (ItemStack p : pending) {
                        if (p != null) drops.add(p.clone());
                    }
                    log("BreakHandler: dropping " + pending[0].getType()
                            + " + " + (pending[1] != null ? pending[1].getType() : "null"));
                    brokenLocations.add(loc);
                }
            }
        });
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
        Location loc = menu.getLocation();

        if (brokenLocations.remove(loc)) {
            BlockStorage.clearBlockInfo(loc);
            log("findNextRecipe: cleared stale operation at re-placed machine");
        }

        pendingInputs.remove(loc);

        ItemStack s19 = menu.getItemInSlot(getInputSlots()[0]);
        ItemStack s20 = menu.getItemInSlot(getInputSlots()[1]);
        if (s19 == null || s20 == null) return null;

        ItemStack equipment;
        ItemStack plainBook;
        boolean s19isEquip = AffixTransferUtil.hasPotionAffixes(s19) && s19.getType() != Material.BOOK;
        boolean s20isEquip = AffixTransferUtil.hasPotionAffixes(s20) && s20.getType() != Material.BOOK;
        boolean s19isBook = s19.getType() == Material.BOOK;
        boolean s20isBook = s20.getType() == Material.BOOK;

        if (s19isEquip && s20isBook) {
            equipment = s19; plainBook = s20;
        } else if (s20isEquip && s19isBook) {
            equipment = s20; plainBook = s19;
        } else {
            return null;
        }

        ItemMeta eqMeta = equipment.getItemMeta();
        String pdcData = eqMeta.getPersistentDataContainer().get(
                ItemKeys.EFFECTS, PersistentDataType.STRING);
        List<EffectEntry> effects = AffixTransferUtil.parseEffects(pdcData);
        if (effects.isEmpty()) return null;

        ItemStack outputEquipment = AffixTransferUtil.stripAffixes(equipment.clone());
        ItemStack outputBook = AffixTransferUtil.createAffixBook(effects, LootTier.get(equipment));

        int ticks = (MagicLoot3.getInstance() != null && MagicLoot3.isDebug())
                ? DEBUG_TICKS : NORMAL_TICKS;

        MachineRecipe recipe = new MachineRecipe(ticks,
                new ItemStack[]{s19.clone(), s20.clone()},
                new ItemStack[]{outputEquipment, outputBook});

        if (!menu.fits(outputEquipment, getOutputSlots())) return null;
        if (!menu.fits(outputBook, getOutputSlots())) return null;

        pendingInputs.put(loc, new ItemStack[]{equipment.clone(), plainBook.clone()});

        for (int slot : getInputSlots()) {
            menu.consumeItem(slot);
        }

        return recipe;
    }

    private static void log(String msg) {
        MagicLoot3.getInstance().getLogger().info("[Disenchanter] " + msg);
    }
}
