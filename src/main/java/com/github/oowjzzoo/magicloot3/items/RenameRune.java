package com.github.oowjzzoo.magicloot3.items;

import java.util.Collection;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.github.oowjzzoo.magicloot3.ItemManager;
import com.github.oowjzzoo.magicloot3.Messages;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemDropHandler;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;

public class RenameRune extends SimpleSlimefunItem<ItemDropHandler> {

    private static final double RANGE = 1.5;

    @ParametersAreNonnullByDefault
    public RenameRune(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe,
                      ItemStack recipeOutput) {
        super(itemGroup, item, recipeType, recipe, recipeOutput);
    }

    @Override
    public @Nonnull ItemDropHandler getItemHandler() {
        return (e, p, item) -> {
            if (isItem(item.getItemStack())) {
                if (canUse(p, true)) {
                    Slimefun.runSync(() -> activate(p, item), 20L);
                }
                return true;
            }
            return false;
        };
    }

    private void activate(@Nonnull Player p, @Nonnull Item rune) {
        if (!rune.isValid()) return;

        Location l = rune.getLocation();
        Collection<Entity> entities = l.getWorld().getNearbyEntities(
                l, RANGE, RANGE, RANGE, this::isCompatibleDrop);
        Optional<Entity> optional = entities.stream().findFirst();

        if (optional.isEmpty()) {
            p.sendMessage(Messages.get("rune.rename.no_target"));
            return;
        }

        Item target = (Item) optional.get();

        SoundEffect.ENCHANTMENT_RUNE_ADD_ENCHANT_SOUND.playAt(l, SoundCategory.PLAYERS);

        Slimefun.runSync(() -> {
            if (!rune.isValid() || !target.isValid()) return;

            l.getWorld().strikeLightningEffect(l);

            // Take one item from target stack
            ItemStack targetStack = target.getItemStack();
            ItemStack one = targetStack.clone();
            one.setAmount(1);
            targetStack.setAmount(targetStack.getAmount() - 1);
            if (targetStack.getAmount() <= 0) {
                target.remove();
            } else {
                target.setItemStack(targetStack);
            }

            // Consume one rune from the stack
            ItemStack runeStack = rune.getItemStack();
            runeStack.setAmount(runeStack.getAmount() - 1);
            if (runeStack.getAmount() <= 0) {
                rune.remove();
            } else {
                rune.setItemStack(runeStack);
            }

            renameItem(one);
            l.getWorld().dropItemNaturally(l, one);
            p.sendMessage(Messages.get("rune.rename.success"));
        }, 10L);
    }

    private boolean isCompatibleDrop(@Nonnull Entity entity) {
        if (!(entity instanceof Item item)) return false;
        ItemStack stack = item.getItemStack();
        if (isItem(stack)) return false; // don't target another RenameRune
        return !stack.getType().isAir();
    }

    private static void renameItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        String name = ItemManager.generateRandomName();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        item.setItemMeta(meta);
    }
}
