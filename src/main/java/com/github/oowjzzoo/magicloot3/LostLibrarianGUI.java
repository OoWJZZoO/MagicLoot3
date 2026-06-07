package com.github.oowjzzoo.magicloot3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import com.github.oowjzzoo.magicloot3.util.SkullCreator;

public final class LostLibrarianGUI {

    private static final Map<UUID, Boolean> deskState = new HashMap<>();

    public static String getTitle() {
        return Messages.get("gui.title");
    }

    private LostLibrarianGUI() {}

    public static Inventory create(Player player, boolean isDesk) {
        deskState.put(player.getUniqueId(), isDesk);
        String title = isDesk ? Messages.get("desk.title") : getTitle();
        Inventory inv = Bukkit.createInventory(null, 18, title);

        // Border panes
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.setDisplayName(Messages.get("gui.border"));
        border.setItemMeta(borderMeta);
        inv.setItem(0, border);
        inv.setItem(8, border);
        inv.setItem(9, border);
        inv.setItem(17, border);

        // Random option (slot 4) — fixed cost from config
        ItemStack randomIcon = SkullCreator.createSkull(
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzk3OTU1NDYyZTRlNTc2NjY0NDk5YWM0YTFjNTcyZjYxNDNmMTlhZDJkNjE5NDc3NjE5OGY4ZDEzNmZkYjIifX19",
                Messages.get("gui.random"));
        ItemMeta randomMeta = randomIcon.getItemMeta();
        List<String> randomLore = new ArrayList<>();
        randomLore.add("");
        randomLore.add(Messages.get("gui.cost", getCost("RANDOM")));
        randomMeta.setLore(randomLore);
        randomIcon.setItemMeta(randomMeta);
        inv.setItem(4, randomIcon);

        // Tier buttons
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
            inv.setItem(11 + i, paneItem);
        }

        // Exchange button (slot 16) — convert unidentified item to voucher
        ItemStack exchangeBtn = new ItemStack(Material.CAULDRON);
        ItemMeta exMeta = exchangeBtn.getItemMeta();
        exMeta.setDisplayName(Messages.get("gui.exchange_voucher"));
        exchangeBtn.setItemMeta(exMeta);
        inv.setItem(16, exchangeBtn);

        return inv;
    }

    public static boolean handleClick(Player player, int slot) {
        boolean isDesk = deskState.getOrDefault(player.getUniqueId(), false);
        switch (slot) {
            case 4:  LostLibrarian.examineTier(player, null, isDesk); break;
            case 11: LostLibrarian.examineTier(player, LootTier.COMMON, isDesk); break;
            case 12: LostLibrarian.examineTier(player, LootTier.UNCOMMON, isDesk); break;
            case 13: LostLibrarian.examineTier(player, LootTier.RARE, isDesk); break;
            case 14: LostLibrarian.examineTier(player, LootTier.EPIC, isDesk); break;
            case 15: LostLibrarian.examineTier(player, LootTier.LEGENDARY, isDesk); break;
            case 16: exchangeVoucher(player); break;
            default: return false;
        }
        return true;
    }

    private static void exchangeVoucher(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (LootTier.get(item) != LootTier.UNKNOWN) {
            player.sendMessage(Messages.get("gui.exchange_no_unidentified"));
            return;
        }
        SlimefunItem voucher = SlimefunItem.getById("GARBLED_VOUCHER");
        if (voucher == null) return;
        item.setAmount(item.getAmount() - 1);
        player.getInventory().addItem(voucher.getItem().clone());
        player.sendMessage(Messages.get("gui.exchange_success"));
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
            case 10 -> Material.PURPLE_STAINED_GLASS_PANE;
            case 9  -> Material.CYAN_STAINED_GLASS_PANE;
            case 4  -> Material.YELLOW_STAINED_GLASS_PANE;
            case 1  -> Material.ORANGE_STAINED_GLASS_PANE;
            default -> Material.WHITE_STAINED_GLASS_PANE;
        };
    }
}
