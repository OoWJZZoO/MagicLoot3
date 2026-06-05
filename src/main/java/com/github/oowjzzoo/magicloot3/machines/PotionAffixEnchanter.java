package com.github.oowjzzoo.magicloot3.machines;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.github.oowjzzoo.magicloot3.ItemKeys;
import com.github.oowjzzoo.magicloot3.LootTier;
import com.github.oowjzzoo.magicloot3.MagicLoot3;
import com.github.oowjzzoo.magicloot3.Messages;
import com.github.oowjzzoo.magicloot3.machines.AffixTransferUtil.EffectEntry;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu.MenuClickHandler;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;

public class PotionAffixEnchanter extends AContainer {

    private static final int FUEL_SLOT = 13;
    private static final String FUEL_ID = "TIME_OF_EXPLORATION";

    public PotionAffixEnchanter(ItemGroup itemGroup, SlimefunItemStack item,
                                 RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        setCapacity(1024);
        setEnergyConsumption(64);
        setProcessingSpeed(5);
    }

    @Override
    public String getMachineIdentifier() { return "POTION_AFFIX_ENCHANTER"; }

    @Override
    public ItemStack getProgressBar() { return new ItemStack(Material.ENCHANTED_BOOK); }

    @Override
    protected void registerDefaultRecipes() {}

    // Border arrays copied from AContainer, with slot 13 removed from BORDER
    private static final int[] BORDER = { 0,1,2,3,4,5,6,7,8, 31, 36,37,38,39,40,41,42,43,44 };
    private static final int[] BORDER_IN = { 9,10,11,12, 18,21, 27,28,29,30 };
    private static final int[] BORDER_OUT = { 14,15,16,17, 23,26, 32,33,34,35 };

    @Override
    protected void constructMenu(BlockMenuPreset preset) {
        MenuClickHandler empty = ChestMenuUtils.getEmptyClickHandler();
        for (int i : BORDER) {
            preset.addItem(i, ChestMenuUtils.getBackground(), empty);
        }
        for (int i : BORDER_IN) {
            preset.addItem(i, ChestMenuUtils.getInputSlotTexture(), empty);
        }
        for (int i : BORDER_OUT) {
            preset.addItem(i, ChestMenuUtils.getOutputSlotTexture(), empty);
        }
        ItemStack bg = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bgm = bg.getItemMeta(); bgm.setDisplayName(" "); bg.setItemMeta(bgm);
        preset.addItem(22, bg, empty);

        for (int i : getOutputSlots()) {
            preset.addMenuClickHandler(i, ChestMenuUtils.getDefaultOutputHandler());
        }

        // Slot 4: fuel hint
        List<String> lines = Messages.getList("machine.fuel_slot_hint");
        String name = lines.isEmpty() ? "" : ChatColor.translateAlternateColorCodes('&', lines.get(0));
        ItemStack hint = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta hm = hint.getItemMeta();
        hm.setDisplayName(name);
        if (lines.size() > 1) {
            hm.setLore(java.util.Collections.singletonList(
                    ChatColor.translateAlternateColorCodes('&', lines.get(1))));
        }
        hint.setItemMeta(hm);
        preset.addItem(4, hint, empty);
    }

    @Override
    protected MachineRecipe findNextRecipe(BlockMenu menu) {
        if (!hasFuel(menu)) return null;


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

        if (!fitsAll(menu, recipe.getOutput())) return null;

        for (int inputSlot : getInputSlots()) {
            menu.consumeItem(inputSlot);
        }

        // 40% chance to consume one Adventuring Time
        if (ThreadLocalRandom.current().nextInt(100) < 40) {
            menu.consumeItem(FUEL_SLOT);
        }

        return recipe;
    }

    private boolean hasFuel(BlockMenu menu) {
        ItemStack fuel = menu.getItemInSlot(FUEL_SLOT);
        if (fuel == null) return false;
        SlimefunItem sfItem = SlimefunItem.getByItem(fuel);
        return sfItem != null && FUEL_ID.equals(sfItem.getId());
    }

    private ItemStack buildResultBook(ItemStack book) {
        book = AffixTransferUtil.stripAffixes(book);
        if (book.getItemMeta() instanceof EnchantmentStorageMeta esm
                && esm.getStoredEnchants().isEmpty()) {
            return new ItemStack(Material.BOOK);
        }
        return book;
    }

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

    private boolean isEnchantable(ItemStack item) {
        if (item != null && item.getType() != Material.ENCHANTED_BOOK
                && !item.getType().isAir()) {
            SlimefunItem sfItem = SlimefunItem.getByItem(item);
            if (sfItem != null) return sfItem.isEnchantable();
            for (Enchantment e : Enchantment.values()) {
                if (e.canEnchantItem(item)) return true;
            }
            return false;
        }
        return false;
    }
}
