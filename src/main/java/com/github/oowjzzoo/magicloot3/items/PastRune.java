package com.github.oowjzzoo.magicloot3.items;

import java.util.Collection;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.github.oowjzzoo.magicloot3.ItemKeys;
import com.github.oowjzzoo.magicloot3.Messages;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemDropHandler;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;

public class PastRune extends SimpleSlimefunItem<ItemDropHandler> {

    private static final double RANGE = 1.5;

    @ParametersAreNonnullByDefault
    public PastRune(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe,
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
            p.sendMessage(Messages.get("rune.past.no_target"));
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

            ItemStack unanalyzed = makeUnidentified(one);
            l.getWorld().dropItemNaturally(l, unanalyzed);
            p.sendMessage(Messages.get("rune.past.success"));
        }, 10L);
    }

    private boolean isCompatibleDrop(@Nonnull Entity entity) {
        if (!(entity instanceof Item item)) return false;
        ItemStack stack = item.getItemStack();
        if (isItem(stack)) return false; // don't target another PastRune
        return isUnidentified(stack);
    }

    /** Check if the item is valid for conversion: vanilla, no enchants, no affixes. */
    static boolean isUnidentified(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (item.getType() == org.bukkit.Material.ENCHANTED_BOOK) return false;
        if (SlimefunItem.getByItem(item) != null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        // Plain books can't be enchanted but can become enchanted books via this rune
        boolean isBook = item.getType() == org.bukkit.Material.BOOK;
        if (!isBook) {
            boolean enchantable = false;
            for (Enchantment e : Enchantment.values()) {
                if (e.canEnchantItem(item)) { enchantable = true; break; }
            }
            if (!enchantable) return false;
        }
        if (meta.hasEnchants()) return false;
        // No potion affix PDC
        if (meta.getPersistentDataContainer().has(ItemKeys.EFFECTS)) return false;
        // No tier PDC
        if (meta.getPersistentDataContainer().has(ItemKeys.TIER)) return false;
        return true;
    }

    /** Turn a vanilla item into an UNKNOWN-tier MagicLoot3 item with garbled name. */
    static ItemStack makeUnidentified(ItemStack item) {
        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;

        // Books become enchanted books (vanilla book can't hold meta properly)
        if (result.getType() == org.bukkit.Material.BOOK) {
            result.setType(org.bukkit.Material.ENCHANTED_BOOK);
            meta = result.getItemMeta();
        }

        meta.getPersistentDataContainer().set(ItemKeys.TIER,
                PersistentDataType.STRING, "UNKNOWN");
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                "&7&kMEH WANNA BE EXAMINED"));

        java.util.List<String> lore = meta.hasLore() ? new java.util.ArrayList<>(meta.getLore()) : new java.util.ArrayList<>();
        // Remove any existing tier lore line before appending
        String tierPrefix = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
                Messages.get("tier_lore_prefix")));
        lore.removeIf(line -> ChatColor.stripColor(line).startsWith(tierPrefix));
        lore.add("");
        lore.add(Messages.get("tier_lore_prefix") + Messages.get("tiers.UNKNOWN"));
        meta.setLore(lore);

        result.setItemMeta(meta);
        // Tag as unidentified SF item for recipe matching
        Slimefun.getItemDataService().setItemData(result, "MAGICLOOT_UNIDENTIFIED");
        return result;
    }
}
