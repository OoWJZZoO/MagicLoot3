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
            player.openInventory(LostLibrarianGUI.create(player));
        } else {
            player.sendMessage(isDesk ? Messages.get("desk.cannot_examine") : Messages.get("npc.cannot_examine"));
        }
    }

    public static void examineTier(Player player, LootTier tier, boolean isDesk) {
        int cost = Bukkit.getPluginManager().getPlugin("MagicLoot3")
                .getConfig().getInt("costs." + tier.toString());

        if (player.getLevel() >= cost) {
            ItemStack item = player.getInventory().getItemInMainHand();
            player.getInventory().setItemInMainHand(ItemManager.applyTier(item, tier));
            player.updateInventory();
            player.setLevel(player.getLevel() - cost);
            player.sendMessage(isDesk ? Messages.get("desk.success") : Messages.get("npc.success"));
        } else {
            player.sendMessage(isDesk ? Messages.get("desk.no_xp") : Messages.get("npc.no_xp"));
        }
    }
}
