package com.github.oowjzzoo.magicloot3.machines;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Piglin;
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
import io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem;
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

public class PiglinSimulator extends AContainer implements RecipeDisplayItem {

    private static final int[] GOLD_SLOTS = {0,1,2, 9,11, 18,19,20};
    private static final int[] HEAD_SLOTS = {27,28,29,36, 45,46,47};
    private static final int INPUT_SLOT    = 10;
    private static final int HEAD_SLOT     = 37;
    private static final int PROGRESS_SLOT = 38;
    private static final int[] OUTPUT;
    static {
        Set<Integer> reserved = new HashSet<>();
        for (int i : GOLD_SLOTS) reserved.add(i);
        for (int i : HEAD_SLOTS) reserved.add(i);
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

    // Decorative items
    private static final ItemStack GOLD_HINT = goldHint();
    private static final ItemStack HEAD_HINT = headHint();

    private static ItemStack goldHint() {
        ItemStack g = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        ItemMeta m = g.getItemMeta();
        m.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&l此处放入金锭"));
        g.setItemMeta(m);
        return g;
    }

    private static ItemStack headHint() {
        ItemStack g = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta m = g.getItemMeta();
        m.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b&l此处放入 &e猪灵的头"));
        m.setLore(List.of(ChatColor.translateAlternateColorCodes('&',
                "&7机器的倍速取决于头颅的数目")));
        g.setItemMeta(m);
        return g;
    }

    public PiglinSimulator(ItemGroup itemGroup, SlimefunItemStack item,
                            RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        setCapacity(1024);
        setEnergyConsumption(72);
        setProcessingSpeed(1);
    }

    @Override public String getMachineIdentifier() { return "PIGLIN_SIMULATOR"; }
    @Override public ItemStack getProgressBar() { return new ItemStack(Material.GOLDEN_HELMET); }
    @Override protected void registerDefaultRecipes() {
        ItemStack head = new ItemStack(Material.PIGLIN_HEAD);
        ItemMeta hm = head.getItemMeta();
        hm.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e猪灵交易的所有产物"));
        head.setItemMeta(hm);
        registerRecipe(3, new ItemStack[]{new ItemStack(Material.GOLD_INGOT)},
                new ItemStack[]{head});
    }

    @Override
    public java.util.List<ItemStack> getDisplayRecipes() {
        java.util.List<ItemStack> display = new java.util.ArrayList<>(recipes.size() * 2);
        for (MachineRecipe r : recipes) {
            if (r.getInput().length != 1) continue;
            display.add(r.getInput()[0]);
            display.add(r.getOutput()[0]);
        }
        return display;
    }

    @Override public int[] getOutputSlots() { return OUTPUT; }
    @Override public int[] getInputSlots() { return new int[]{INPUT_SLOT}; }

    @Override
    protected void constructMenu(BlockMenuPreset preset) {
        var empty = ChestMenuUtils.getEmptyClickHandler();
        for (int i : GOLD_SLOTS) preset.addItem(i, GOLD_HINT, empty);
        for (int i : HEAD_SLOTS) preset.addItem(i, HEAD_HINT.clone(), empty);
        preset.addItem(PROGRESS_SLOT, HEAD_HINT.clone(), empty);
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
                    inv.replaceExistingItem(PROGRESS_SLOT, HEAD_HINT.clone());
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

        ItemStack headItem = menu.getItemInSlot(HEAD_SLOT);
        int n = (headItem != null && headItem.getType() == Material.PIGLIN_HEAD)
                ? headItem.getAmount() : 0;
        if (n <= 0) return null;

        ItemStack gold = menu.getItemInSlot(INPUT_SLOT);
        if (gold == null || gold.getType() != Material.GOLD_INGOT) return null;
        n = Math.min(n, gold.getAmount());

        if (gold.getAmount() == n) menu.replaceExistingItem(INPUT_SLOT, null);
        else gold.setAmount(gold.getAmount() - n);

        Inventory sim = Bukkit.createInventory(null, menu.toInventory().getSize());
        sim.setContents(menu.toInventory().getContents());

        World world = Bukkit.getWorlds().get(0);
        Piglin piglin = world.createEntity(world.getSpawnLocation(), Piglin.class);
        LootTable table = LootTables.PIGLIN_BARTERING.getLootTable();
        LootContext ctx = new LootContext.Builder(world.getSpawnLocation())
                .lootedEntity(piglin).build();

        List<ItemStack> outputs = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            Collection<ItemStack> drops = table.populateLoot(r, ctx);
            ItemStack result = drops.isEmpty() ? null : drops.iterator().next();
            if (result == null || result.getType().isAir()) continue;

            result = applySfBarterReplace(r, result);

            if (tryFitItem(sim, result)) {
                outputs.add(result);
            }
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
