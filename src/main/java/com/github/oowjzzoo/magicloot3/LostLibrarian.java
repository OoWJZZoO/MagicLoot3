package com.github.oowjzzoo.magicloot3;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class LostLibrarian {

    private LostLibrarian() {}

    public static void openMenu(Player player, boolean isDesk) {
        ItemStack item = player.getInventory().getItemInMainHand();
        LootTier tier = LootTier.get(item);

        if (tier == LootTier.UNKNOWN) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1F, 1F);
            LostLibrarianGUI.open(player, isDesk);
        } else {
            player.sendMessage(isDesk ? Messages.get("desk.cannot_examine") : Messages.get("npc.cannot_examine"));
        }
    }

    public static void examineTier(Player player, LootTier tier, boolean isDesk) {
        int cost;
        LootTier actualTier;
        if (tier == null) {
            cost = Bukkit.getPluginManager().getPlugin("MagicLoot3")
                    .getConfig().getInt("costs.RANDOM");
            actualTier = LootTier.getRandomApplicable();
        } else {
            cost = Bukkit.getPluginManager().getPlugin("MagicLoot3")
                    .getConfig().getInt("costs." + tier.toString());
            actualTier = tier;
        }

        if (player.getLevel() >= cost) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (LootTier.get(hand) != LootTier.UNKNOWN) {
                player.sendMessage(isDesk ? Messages.get("desk.cannot_examine") : Messages.get("npc.cannot_examine"));
                return;
            }
            ItemStack single = hand.clone();
            single.setAmount(1);
            if (hand.getAmount() > 1) {
                hand.setAmount(hand.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            player.getInventory().addItem(ItemManager.applyTier(single, actualTier)).values()
                    .forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
            player.updateInventory();
            player.setLevel(player.getLevel() - cost);
            player.sendMessage(isDesk ? Messages.get("desk.success") : Messages.get("npc.success"));
        } else {
            player.sendMessage(isDesk ? Messages.get("desk.no_xp") : Messages.get("npc.no_xp"));
        }
    }
}
