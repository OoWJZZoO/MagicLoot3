package com.github.oowjzzoo.magicloot3;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class LootListener implements Listener {

    private final Plugin plugin;

    private static final List<DamageCause> CAUSES = Arrays.asList(
            DamageCause.BLOCK_EXPLOSION,
            DamageCause.CONTACT,
            DamageCause.ENTITY_ATTACK,
            DamageCause.ENTITY_EXPLOSION,
            DamageCause.FALL,
            DamageCause.FALLING_BLOCK,
            DamageCause.FIRE,
            DamageCause.LAVA,
            DamageCause.MAGIC,
            DamageCause.THORNS
    );

    public LootListener(Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // --- Lost Librarian GUI click handler ---

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!e.getView().getTitle().equals(LostLibrarianGUI.getTitle())) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        LostLibrarianGUI.handleClick(player, e.getRawSlot());
    }

    // --- Ruin generation ---

    @EventHandler
    public void onRuinGenerate(ChunkPopulateEvent e) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        List<String> worldWhitelist = plugin.getConfig().getStringList("world-whitelist");
        if (!worldWhitelist.contains(e.getWorld().getName())) return;
        if (random.nextInt(100) >= plugin.getConfig().getInt("chances.ruin", 30)) return;

        int x = e.getChunk().getX() * 16 + random.nextInt(16);
        int z = e.getChunk().getZ() * 16 + random.nextInt(16);
        int minY = plugin.getConfig().getInt("ruin.min-y", 30);

        for (int y = e.getWorld().getMaxHeight(); y > minY; y--) {
            Block current = e.getWorld().getBlockAt(x, y, z);
            if (current.getType().isAir()) {
                boolean flat = true;
                // 6x8x6 flat terrain check
                outer:
                for (int i = 0; i < 6; i++) {
                    for (int k = 0; k < 8; k++) {
                        for (int j = 0; j < 6; j++) {
                            Block relBlock = current.getRelative(i, k, j);
                            if (relBlock.getType().isSolid()
                                    || relBlock.getType().toString().contains("LEAVES")
                                    || !current.getRelative(i, -1, j).getType().isSolid()) {
                                flat = false;
                                break outer;
                            }
                        }
                    }
                }
                if (flat) {
                    RuinBuilder.buildRuin(current.getLocation());
                    break;
                }
            }
        }
    }

    // --- Lost Librarian villager interaction ---

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        Entity entity = e.getRightClicked();
        if (entity instanceof Villager
                && entity.getPersistentDataContainer().has(ItemKeys.LIBRARIAN)) {
            e.setCancelled(true);
            try {
                LostLibrarian.openMenu(e.getPlayer());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    // --- Combat potion effect triggers ---

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        if (!CAUSES.contains(e.getCause())) return;
        if (!e.getEntity().getWorld().getPVP()) return;

        // Protect Lost Librarian villagers
        if (victim instanceof Villager
                && victim.getPersistentDataContainer().has(ItemKeys.LIBRARIAN)) {
            e.setCancelled(true);
            return;
        }

        try {
            if (e instanceof EntityDamageByEntityEvent damageEvent) {
                if (damageEvent.getDamager() instanceof LivingEntity attacker) {
                    applyWeaponEffects(attacker, victim);
                } else if (damageEvent.getDamager() instanceof Projectile projectile
                        && projectile.getShooter() instanceof LivingEntity shooter) {
                    applyWeaponEffects(shooter, victim);
                }
            }
            // Armor effects on victim
            for (ItemStack armor : getArmorContents(victim)) {
                applyArmorEffects(victim, armor, e);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    // --- Mob spawn equipment ---

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSpawn(CreatureSpawnEvent e) {
        EntityType type = e.getEntityType();
        if (type == EntityType.ZOMBIE || type == EntityType.SKELETON
                || type == EntityType.ZOMBIFIED_PIGLIN) {
            if (ThreadLocalRandom.current().nextInt(100) < plugin.getConfig().getInt("chances.mobs", 40)) {
                ItemManager.equipEntity(e.getEntity());
            }
        }
    }

    // --- Helper methods ---

    private void applyWeaponEffects(LivingEntity attacker, LivingEntity victim) {
        ItemStack weapon = getItemInMainHand(attacker);
        applyEffectsFromItem(weapon, attacker, victim);
    }

    private void applyArmorEffects(LivingEntity wearer, ItemStack armor, EntityDamageEvent event) {
        applyEffectsFromItem(armor, wearer,
                event instanceof EntityDamageByEntityEvent dmg ? (LivingEntity) dmg.getDamager() : null);
    }

    /** Reads effects from PDC and applies them. Language-independent. */
    private void applyEffectsFromItem(ItemStack item, LivingEntity wearer, LivingEntity attacker) {
        if (item == null || !item.hasItemMeta()) return;
        var meta = item.getItemMeta();
        if (meta == null) return;
        String data = meta.getPersistentDataContainer().get(ItemKeys.EFFECTS,
                org.bukkit.persistence.PersistentDataType.STRING);
        if (data == null || data.isEmpty()) return;

        for (String entry : data.split(",")) {
            String[] parts = entry.split(":");
            if (parts.length < 3) continue;
            String enKey = parts[0];
            boolean isPositive = "+".equals(parts[1]);
            int level;
            try { level = Integer.parseInt(parts[2]); } catch (NumberFormatException e) { continue; }

            PotionEffectType type = ItemManager.potion.get(enKey);
            if (type == null) continue;

            if (isPositive) {
                wearer.addPotionEffect(new PotionEffect(type, (level + 1) * 3 * 20, level));
            } else if (attacker != null) {
                attacker.addPotionEffect(new PotionEffect(type, (level + 1) * 3 * 20, level));
            }
        }
    }

    private ItemStack getItemInMainHand(LivingEntity entity) {
        if (entity instanceof Player player) {
            return player.getInventory().getItemInMainHand();
        } else if (entity.getEquipment() != null) {
            return entity.getEquipment().getItemInMainHand();
        }
        return null;
    }

    private ItemStack[] getArmorContents(LivingEntity entity) {
        if (entity instanceof Player player) {
            return player.getInventory().getArmorContents();
        } else if (entity.getEquipment() != null) {
            return entity.getEquipment().getArmorContents();
        }
        return new ItemStack[0];
    }
}
