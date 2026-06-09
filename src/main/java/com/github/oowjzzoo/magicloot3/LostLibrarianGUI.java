package com.github.oowjzzoo.magicloot3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;

import com.github.oowjzzoo.magicloot3.util.SkullCreator;

public final class LostLibrarianGUI {

    private static final Map<UUID, Boolean> deskState = new HashMap<>();

    private LostLibrarianGUI() {}

    static int cleanupStaleDeskState() {
        int removed = 0;
        var it = deskState.entrySet().iterator();
        while (it.hasNext()) {
            if (Bukkit.getPlayer(it.next().getKey()) == null) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    static void open(Player player, boolean isDesk) {
        deskState.put(player.getUniqueId(), isDesk);
        String title = isDesk ? Messages.get("desk.title") : Messages.get("gui.title");
        ChestMenu menu = new ChestMenu(title);
        menu.setEmptySlotsClickable(false);

        // Fill rows 3-6 (slots 18-53) with background
        for (int i = 18; i < 54; i++)
            menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());

        // Slots 1-3, 5-7 (empty decorative) — background
        for (int i : new int[]{1,2,3, 5,6,7, 10, 16})
            menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());

        // Border panes
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.setDisplayName(Messages.get("gui.border"));
        border.setItemMeta(borderMeta);
        menu.addItem(0, border, ChestMenuUtils.getEmptyClickHandler());
        menu.addItem(8, border, ChestMenuUtils.getEmptyClickHandler());
        menu.addItem(9, border, ChestMenuUtils.getEmptyClickHandler());

        // Random option (slot 4)
        ItemStack randomIcon = SkullCreator.createSkull(
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzk3OTU1NDYyZTRlNTc2NjY0NDk5YWM0YTFjNTcyZjYxNDNmMTlhZDJkNjE5NDc3NjE5OGY4ZDEzNmZkYjIifX19",
                Messages.get("gui.random"));
        ItemMeta randomMeta = randomIcon.getItemMeta();
        List<String> randomLore = new ArrayList<>();
        randomLore.add("");
        randomLore.add(Messages.get("gui.cost", getCost("RANDOM")));
        randomMeta.setLore(randomLore);
        randomIcon.setItemMeta(randomMeta);
        menu.addItem(4, randomIcon);
        menu.addMenuClickHandler(4, (pl, s, it, a) -> {
            LostLibrarian.examineTier(pl, null, isDesk);
            return false;
        });

        // Tier buttons (slots 11-15)
        LootTier[] tiers = {LootTier.COMMON, LootTier.UNCOMMON, LootTier.RARE, LootTier.EPIC, LootTier.LEGENDARY};
        for (int i = 0; i < tiers.length; i++) {
            LootTier tier = tiers[i];
            Material pane = getPaneMaterial(tier.getPrimaryColor());
            ItemStack paneItem = new ItemStack(pane);
            ItemMeta paneMeta = paneItem.getItemMeta();
            paneMeta.setDisplayName(tier.getTag());
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(Messages.get("gui.cost", getCost(tier)));
            paneMeta.setLore(lore);
            paneItem.setItemMeta(paneMeta);
            final LootTier snap = tier;
            menu.addItem(11 + i, paneItem);
            menu.addMenuClickHandler(11 + i, (pl, s, it, a) -> {
                LostLibrarian.examineTier(pl, snap, isDesk);
                return false;
            });
        }

        // Exchange button (slot 17)
        ItemStack exchangeBtn = new ItemStack(Material.CAULDRON);
        ItemMeta exMeta = exchangeBtn.getItemMeta();
        exMeta.setDisplayName(Messages.get("gui.exchange_button_title"));
        List<String> exLore = new ArrayList<>();
        exLore.add(Messages.get("gui.exchange_button_warning"));
        exMeta.setLore(exLore);
        exchangeBtn.setItemMeta(exMeta);
        menu.addItem(17, exchangeBtn);
        menu.addMenuClickHandler(17, (pl, s, it, a) -> {
            exchangeVoucher(pl);
            return false;
        });

        menu.open(player);
    }

    private static void exchangeVoucher(Player player) {
        boolean isDesk = deskState.getOrDefault(player.getUniqueId(), false);
        ItemStack item = player.getInventory().getItemInMainHand();
        if (LootTier.get(item) != LootTier.UNKNOWN) {
            player.sendMessage(isDesk ? Messages.get("desk.exchange_no_unidentified")
                    : Messages.get("npc.exchange_no_unidentified"));
            return;
        }
        SlimefunItem voucher = SlimefunItem.getById("GARBLED_VOUCHER");
        if (voucher == null) return;
        item.setAmount(item.getAmount() - 1);
        player.getInventory().addItem(voucher.getItem().clone());
        player.sendMessage(isDesk ? Messages.get("desk.exchange_success")
                : Messages.get("npc.exchange_success"));
    }

    private static int getCost(LootTier tier) {
        return Bukkit.getPluginManager().getPlugin("MagicLoot3")
                .getConfig().getInt("costs." + tier.toString());
    }

    private static int getCost(String key) {
        return Bukkit.getPluginManager().getPlugin("MagicLoot3")
                .getConfig().getInt("costs." + key);
    }

    private static Material getPaneMaterial(int colorId) {
        return switch (colorId) {
            case 13 -> Material.LIME_STAINED_GLASS_PANE;
            case 10 -> Material.CYAN_STAINED_GLASS_PANE;
            case 9  -> Material.PURPLE_STAINED_GLASS_PANE;
            case 4  -> Material.YELLOW_STAINED_GLASS_PANE;
            case 1  -> Material.ORANGE_STAINED_GLASS_PANE;
            default -> Material.WHITE_STAINED_GLASS_PANE;
        };
    }
}
