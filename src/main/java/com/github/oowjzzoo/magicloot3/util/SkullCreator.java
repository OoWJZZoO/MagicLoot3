package com.github.oowjzzoo.magicloot3.util;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

public final class SkullCreator {

    private SkullCreator() {}

    public static ItemStack createSkull(String base64Texture) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), null);
            profile.getProperties().add(new ProfileProperty("textures", base64Texture));
            meta.setPlayerProfile(profile);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    public static ItemStack createSkull(String base64Texture, String displayName) {
        ItemStack skull = createSkull(base64Texture);
        if (skull.hasItemMeta()) {
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', displayName));
            skull.setItemMeta(meta);
        }
        return skull;
    }
}
