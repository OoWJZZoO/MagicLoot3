package com.github.oowjzzoo.magicloot3.machines;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;

import com.github.oowjzzoo.magicloot3.MagicLoot3;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.PiglinBarterDrop;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.operations.CraftingOperation;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;

public class PiglinSimulator extends AContainer {

    private static final int[] YELLOW = {0,1,2, 9,11, 18,19,20};
    private static final int[] GRAY   = {27,28,29,36, 45,46,47};
    private static final int INPUT_SLOT    = 10;
    private static final int HEAD_SLOT     = 37;
    private static final int PROGRESS_SLOT = 38;
    private static final int[] OUTPUT;
    static {
        Set<Integer> reserved = new HashSet<>();
        for (int i : YELLOW) reserved.add(i);
        for (int i : GRAY) reserved.add(i);
        reserved.add(INPUT_SLOT);
        reserved.add(HEAD_SLOT);
        reserved.add(PROGRESS_SLOT);
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < 54; i++) {
            if (!reserved.contains(i)) out.add(i);
        }
        OUTPUT = out.stream().mapToInt(i -> i).toArray();
    }
    private static final int CYCLE_SECONDS = 6;
    private static final ItemStack DUMMY_INPUT = new ItemStack(Material.GOLD_INGOT, 1);

    public PiglinSimulator(ItemGroup itemGroup, SlimefunItemStack item,
                            RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        setCapacity(512);
        setEnergyConsumption(30);
        setProcessingSpeed(1);
    }

    @Override public String getMachineIdentifier() { return "PIGLIN_SIMULATOR"; }
    @Override public ItemStack getProgressBar() { return new ItemStack(Material.GOLDEN_HELMET); }
    @Override protected void registerDefaultRecipes() {}
    @Override public int[] getOutputSlots() { return OUTPUT; }
    @Override public int[] getInputSlots() { return new int[]{INPUT_SLOT}; }

    @Override
    protected void constructMenu(BlockMenuPreset preset) {
        var empty = ChestMenuUtils.getEmptyClickHandler();
        for (int i : YELLOW) preset.addItem(i, ChestMenuUtils.getBackground(), empty);
        for (int i : GRAY) preset.addItem(i, ChestMenuUtils.getInputSlotTexture(), empty);

        ItemStack pg = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
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
                    ItemStack pane = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
                    ItemMeta pm = pane.getItemMeta(); pm.setDisplayName(" "); pane.setItemMeta(pm);
                    inv.replaceExistingItem(PROGRESS_SLOT, pane);

                    ItemStack[] results = op.getResults();
                    if (fitsAll(inv, results)) {
                        for (ItemStack r : results) inv.pushItem(r.clone(), OUTPUT);
                    } else {
                        Bukkit.getScheduler().runTask(MagicLoot3.getInstance(), () -> {
                            for (ItemStack r : results)
                                b.getWorld().dropItemNaturally(
                                        b.getLocation().add(0.5, 1, 0.5), r.clone());
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
        ThreadLocalRandom r = ThreadLocalRandom.current();

        // Read piglin head count (efficiency multiplier)
        ItemStack headItem = menu.getItemInSlot(HEAD_SLOT);
        int n = (headItem != null && headItem.getType() == Material.PIGLIN_HEAD)
                ? headItem.getAmount() : 0;
        if (n <= 0) return null;

        // Check gold ingot count
        ItemStack gold = menu.getItemInSlot(INPUT_SLOT);
        if (gold == null || gold.getType() != Material.GOLD_INGOT || gold.getAmount() < n)
            return null;

        // Consume n gold ingots
        if (gold.getAmount() == n) menu.replaceExistingItem(INPUT_SLOT, null);
        else gold.setAmount(gold.getAmount() - n);

        // Simulated inventory for incremental fitting
        Inventory sim = Bukkit.createInventory(null, menu.toInventory().getSize());
        sim.setContents(menu.toInventory().getContents());

        LootTable table = LootTables.PIGLIN_BARTERING.getLootTable();
        LootContext ctx = new LootContext.Builder(
                Bukkit.getWorlds().get(0).getSpawnLocation()).build();

        List<ItemStack> outputs = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            Collection<ItemStack> drops = table.populateLoot(r, ctx);
            ItemStack result = drops.isEmpty() ? null : drops.iterator().next();
            if (result == null || result.getType().isAir()) continue;

            result = applySfBarterReplace(r, result);

            if (tryFitItem(sim, result)) {
                outputs.add(result);
            }
            // else: discard — swallowed, no drop to world
        }

        if (outputs.isEmpty()) return null;
        List<ItemStack> merged = mergeStacks(outputs);
        return new MachineRecipe(CYCLE_SECONDS,
                new ItemStack[]{DUMMY_INPUT},
                merged.toArray(new ItemStack[0]));
    }

    @Override
    protected BlockBreakHandler onBlockBreak() {
        return new SimpleBlockBreakHandler() {
            @Override
            public void onBlockBreak(Block b) {
                BlockMenu inv = BlockStorage.getInventory(b);
                if (inv != null) {
                    inv.dropItems(b.getLocation(), OUTPUT);
                    inv.dropItems(b.getLocation(), new int[]{INPUT_SLOT, HEAD_SLOT});
                }
                getMachineProcessor().endOperation(b);
            }
        };
    }

    // --- Private helpers ---

    /** Apply SF piglin barter replacement, replicating PiglinListener logic. */
    private static ItemStack applySfBarterReplace(ThreadLocalRandom r, ItemStack vanilla) {
        for (ItemStack candidate : Slimefun.getRegistry().getBarteringDrops()) {
            SlimefunItem sfi = SlimefunItem.getByItem(candidate);
            if (sfi instanceof PiglinBarterDrop bd) {
                int chance = bd.getBarteringLootChance();
                if (chance > 0 && chance < 100 && chance > r.nextInt(100)) {
                    return sfi.getRecipeOutput().clone();
                }
            }
        }
        return vanilla;
    }

    /** Try to fit a single item into the simulated inventory. Updates sim on success. */
    private static boolean tryFitItem(Inventory sim, ItemStack item) {
        for (int s : OUTPUT) {
            ItemStack existing = sim.getItem(s);
            if (existing == null || existing.getType().isAir()) {
                sim.setItem(s, item.clone());
                return true;
            } else if (existing.isSimilar(item)
                    && existing.getAmount() < existing.getMaxStackSize()) {
                int space = existing.getMaxStackSize() - existing.getAmount();
                if (space >= item.getAmount()) {
                    existing.setAmount(existing.getAmount() + item.getAmount());
                    return true;
                }
            }
        }
        return false;
    }

    /** Merge similar stacks into combined stacks. */
    private static List<ItemStack> mergeStacks(List<ItemStack> items) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack item : items) {
            boolean found = false;
            for (ItemStack m : merged) {
                if (m.isSimilar(item) && m.getAmount() < m.getMaxStackSize()) {
                    int space = m.getMaxStackSize() - m.getAmount();
                    int add = Math.min(space, item.getAmount());
                    m.setAmount(m.getAmount() + add);
                    if (add >= item.getAmount()) {
                        found = true;
                        break;
                    }
                    item.setAmount(item.getAmount() - add);
                }
            }
            if (!found) merged.add(item.clone());
        }
        return merged;
    }

    private boolean fitsAll(BlockMenu menu, ItemStack[] items) {
        Inventory copy = Bukkit.createInventory(null, menu.toInventory().getSize());
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
