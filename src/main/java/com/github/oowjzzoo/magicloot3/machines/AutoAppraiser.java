package com.github.oowjzzoo.magicloot3.machines;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.github.oowjzzoo.magicloot3.ItemManager;
import com.github.oowjzzoo.magicloot3.LootTier;
import com.github.oowjzzoo.magicloot3.MagicLoot3;
import com.github.oowjzzoo.magicloot3.Messages;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.operations.CraftingOperation;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;

public class AutoAppraiser extends AContainer {

    private static final int[] BORDER = { 0,1,2,3,4,5,6,7,8, 31, 36,37,38,39,40,41,42,43,44 };
    private static final int[] BORDER_IN = { 9,10,11,12, 18,21, 27,28,29,30 };
    private static final int[] BORDER_OUT = { 14,15,16,17, 23,26, 32,33,34,35 };

    private static final String RANK_KEY = "appraiser_rank";
    private static final String DEFAULT_RANK = "RANDOM";

    private static final Map<Location, ItemStack[]> pendingInputs = new ConcurrentHashMap<>();

    public AutoAppraiser(ItemGroup itemGroup, SlimefunItemStack item,
                         RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        setCapacity(1024);
        setEnergyConsumption(96);
        setProcessingSpeed(3);
    }

    @Override
    public String getMachineIdentifier() { return "AUTO_APPRAISER"; }

    @Override
    public ItemStack getProgressBar() { return new ItemStack(Material.GRINDSTONE); }

    @Override
    protected void registerDefaultRecipes() {}

    @Override
    protected BlockBreakHandler onBlockBreak() {
        return new SimpleBlockBreakHandler() {
            @Override
            public void onBlockBreak(Block b) {
                BlockMenu inv = BlockStorage.getInventory(b);
                if (inv != null) {
                    inv.dropItems(b.getLocation(), getInputSlots());
                    inv.dropItems(b.getLocation(), getOutputSlots());
                }
                getMachineProcessor().endOperation(b);

                ItemStack[] pending = pendingInputs.remove(b.getLocation());
                if (pending != null) {
                    for (ItemStack p : pending) {
                        if (p != null) b.getWorld().dropItemNaturally(b.getLocation(), p);
                    }
                }
            }
        };
    }

    @Override
    protected void constructMenu(BlockMenuPreset preset) {
        var empty = ChestMenuUtils.getEmptyClickHandler();
        for (int i : BORDER) preset.addItem(i, ChestMenuUtils.getBackground(), empty);
        for (int i : BORDER_IN) preset.addItem(i, ChestMenuUtils.getInputSlotTexture(), empty);
        for (int i : BORDER_OUT) preset.addItem(i, ChestMenuUtils.getOutputSlotTexture(), empty);

        // Bell hint at slot 4
        String bellTitle = Messages.get("machine.auto_appraiser.bell_title");
        String bellLore = Messages.get("machine.auto_appraiser.bell_lore");
        ItemStack bell = new ItemStack(Material.BELL);
        ItemMeta bm = bell.getItemMeta();
        bm.setDisplayName(bellTitle);
        bm.setLore(List.of(bellLore));
        bell.setItemMeta(bm);
        preset.addItem(4, bell, empty);

        // Rank selector button at slot 22 — placeholder, real item set per-instance
        preset.addMenuClickHandler(22, (player, slot, item, action) -> {
            Location loc = player.getOpenInventory().getTopInventory().getLocation();
            if (loc == null) return false;
            BlockMenu menu = BlockStorage.getInventory(loc);
            if (menu == null) return false;
            // Don't allow switching while machine is running
            if (getMachineProcessor().getOperation(loc.getBlock()) != null) return false;
            cycleRank(menu);
            updateRankButton(menu);
            return false;
        });
    }

    // --- Rank management ---

    static void updateRankButton(BlockMenu menu) {
        String rank = getRank(menu.getLocation());
        String title = Messages.get("machine.auto_appraiser.rank_button_title");
        ItemStack btn = new ItemStack(Material.PAPER);
        ItemMeta meta = btn.getItemMeta();
        meta.setDisplayName(title);

        List<String> lore = new java.util.ArrayList<>();
        String[] ranks = {"RANDOM", "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY"};
        for (String r : ranks) {
            boolean selected = r.equals(rank);
            String tierName;
            if ("RANDOM".equals(r)) {
                tierName = Messages.get("tiers.RANDOM_NAME");
            } else {
                tierName = LootTier.valueOf(r).getTag();
            }
            int cost = MagicLoot3.getInstance().getConfig().getInt("costs." + r);
            String line;
            if (selected) {
                line = "&a▶ " + tierName + " &8- &b" + cost
                        + Messages.get("machine.auto_appraiser.level_unit");
            } else {
                line = "&7" + ChatColor.stripColor(tierName) + " &8- &b" + cost
                        + Messages.get("machine.auto_appraiser.level_unit");
            }
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        lore.add("");
        lore.add(Messages.get("machine.auto_appraiser.rank_switch_hint"));
        meta.setLore(lore);
        btn.setItemMeta(meta);
        menu.replaceExistingItem(22, btn);
    }

    private static void cycleRank(BlockMenu menu) {
        String current = getRank(menu.getLocation());
        String[] ranks = {"RANDOM", "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY"};
        int idx = 0;
        for (int i = 0; i < ranks.length; i++) {
            if (ranks[i].equals(current)) { idx = i; break; }
        }
        String next = ranks[(idx + 1) % ranks.length];
        BlockStorage.addBlockInfo(menu.getLocation(), RANK_KEY, next);
    }

    static String getRank(Location loc) {
        String val = BlockStorage.getLocationInfo(loc, RANK_KEY);
        return val != null ? val : DEFAULT_RANK;
    }

    // --- Machine tick ---

    @Override
    protected void tick(Block b) {
        BlockMenu inv = BlockStorage.getInventory(b);
        CraftingOperation op = getMachineProcessor().getOperation(b);

        if (op != null) {
            if (takeCharge(b.getLocation())) {
                if (!op.isFinished()) {
                    getMachineProcessor().updateProgressBar(inv, 22, op);
                    op.addProgress(1);
                } else {
                    // Reset progress bar slot
                    inv.replaceExistingItem(22, new ItemStack(Material.AIR));

                    ItemStack[] results = op.getResults();
                    if (fitsAll(inv, results)) {
                        for (ItemStack output : results) {
                            inv.pushItem(output.clone(), getOutputSlots());
                        }
                    } else {
                        ItemStack[] drops = new ItemStack[results.length];
                        for (int i = 0; i < results.length; i++) drops[i] = results[i].clone();
                        org.bukkit.Bukkit.getScheduler().runTask(MagicLoot3.getInstance(),
                                () -> {
                                    for (ItemStack drop : drops) {
                                        b.getWorld().dropItemNaturally(b.getLocation(), drop);
                                    }
                                });
                    }
                    getMachineProcessor().endOperation(b);
                    pendingInputs.remove(b.getLocation());
                    updateRankButton(inv);
                }
            }
        } else {
            MachineRecipe next = findNextRecipe(inv);
            if (next != null) {
                op = new CraftingOperation(next);
                getMachineProcessor().startOperation(b, op);
                getMachineProcessor().updateProgressBar(inv, 22, op);
            } else {
                // Ensure rank button is visible when idle
                ItemStack cur = inv.getItemInSlot(22);
                if (cur == null || cur.getType() != Material.PAPER) {
                    updateRankButton(inv);
                }
            }
        }
    }

    @Override
    protected MachineRecipe findNextRecipe(BlockMenu menu) {
        for (int slot : getInputSlots()) {
            ItemStack equipment = menu.getItemInSlot(slot);
            if (equipment == null || equipment.getType().isAir()) continue;
            if (LootTier.get(equipment) != LootTier.UNKNOWN) continue;

            int otherSlot = slot == getInputSlots()[0] ? getInputSlots()[1] : getInputSlots()[0];
            ItemStack bottles = menu.getItemInSlot(otherSlot);
            if (bottles == null || bottles.getType() != Material.EXPERIENCE_BOTTLE) continue;

            String rankStr = getRank(menu.getLocation());
            int cost = MagicLoot3.getInstance().getConfig().getInt("costs." + rankStr);
            if (bottles.getAmount() < cost) continue;

            ItemStack outputEquipment = equipment.clone();
            if ("RANDOM".equals(rankStr)) {
                outputEquipment = ItemManager.applyTier(outputEquipment, LootTier.getRandomApplicable());
            } else {
                outputEquipment = ItemManager.applyTier(outputEquipment, LootTier.valueOf(rankStr));
            }

            int ticks = cost * 120 / getSpeed();
            if (ticks < 1) ticks = 1;
            if (MagicLoot3.isDebug()) ticks = 1;

            ItemStack consumedBottles = bottles.clone();
            consumedBottles.setAmount(cost);

            MachineRecipe recipe = new MachineRecipe(ticks,
                    new ItemStack[]{equipment.clone(), consumedBottles},
                    new ItemStack[]{outputEquipment});

            if (!fitsAll(menu, recipe.getOutput())) return null;

            pendingInputs.put(menu.getLocation(), new ItemStack[]{
                    equipment.clone(), consumedBottles.clone()});

            menu.consumeItem(slot);
            if (bottles.getAmount() > cost) {
                bottles.setAmount(bottles.getAmount() - cost);
            } else {
                menu.consumeItem(otherSlot);
            }

            return recipe;
        }
        return null;
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
}
