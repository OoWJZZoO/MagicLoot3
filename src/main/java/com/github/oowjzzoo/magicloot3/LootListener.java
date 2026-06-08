package com.github.oowjzzoo.magicloot3;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ArmorStand;
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
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class LootListener implements Listener {

    private final Plugin plugin;

    private static final Set<String> SELF_DAMAGE_EFFECTS = Set.of("instant_damage", "poison", "wither");
    private static final Map<UUID, Map<String, Long>> selfDamageTimers = new HashMap<>();

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
        String title = e.getView().getTitle();
        if (!title.equals(LostLibrarianGUI.getTitle())
                && !title.equals(Messages.get("desk.title"))) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        LostLibrarianGUI.handleClick(player, e.getRawSlot());
    }

    // --- Ruin generation ---

    @EventHandler
    public void onRuinGenerate(ChunkPopulateEvent e) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        String worldName = e.getWorld().getName();
        String path = "worlds." + worldName;
        if (!plugin.getConfig().contains(path)) return;
        int ruinChance = plugin.getConfig().getInt(path + ".ruin-chance", 30);
        if (random.nextInt(100) >= ruinChance) return;

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
                LostLibrarian.openMenu(e.getPlayer(), false);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    // --- Combat potion effect triggers ---

    private static final Set<DamageCause> ALLOWED_CAUSES = Set.of(
            DamageCause.MAGIC, DamageCause.POISON, DamageCause.WITHER);

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) return;

        // Protect Lost Librarian villagers
        if (victim instanceof Villager
                && victim.getPersistentDataContainer().has(ItemKeys.LIBRARIAN)) {
            if (ALLOWED_CAUSES.contains(e.getCause())) {
                // Poison, wither, instant damage passes through
            } else if (e instanceof EntityDamageByEntityEvent dmgEvent
                    && dmgEvent.getDamager() instanceof Player attacker) {
                // Physical hit: cancel damage, apply weapon affixes to NPC
                e.setCancelled(true);
                ItemStack weapon = getItemInMainHand(attacker);
                applyEffectsFromItem(weapon, attacker, victim);
            } else {
                // All other damage sources blocked
                e.setCancelled(true);
            }
            return;
        }

        if (!CAUSES.contains(e.getCause())) return;

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
        LivingEntity attacker = null;
        if (event instanceof EntityDamageByEntityEvent dmg) {
            if (dmg.getDamager() instanceof LivingEntity le) {
                attacker = le;
            } else if (dmg.getDamager() instanceof Projectile proj
                    && proj.getShooter() instanceof LivingEntity shooter) {
                attacker = shooter;
            }
        }
        applyEffectsFromItem(armor, wearer, attacker);
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

            PotionEffectType type = ItemManager.potionEffectMap.get(enKey);
            if (type == null) continue;

            if (isPositive) {
                if (wearer instanceof Player && SELF_DAMAGE_EFFECTS.contains(enKey)) {
                    long durationMs = (level + 1) * 3 * 1000L;
                    Map<String, Long> t = selfDamageTimers.computeIfAbsent(wearer.getUniqueId(), k -> new HashMap<>());
                    t.entrySet().removeIf(e2 -> System.currentTimeMillis() >= e2.getValue());
                    t.put(enKey, System.currentTimeMillis() + durationMs);
                }
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

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        // Existing Librarian logic
        if (e.getEntity() instanceof Villager
                && e.getEntity().getPersistentDataContainer().has(ItemKeys.LIBRARIAN)) {
            SlimefunItem brainItem = SlimefunItem.getById("LOST_LIBRARIAN_BRAIN");
            if (brainItem != null)
                e.getEntity().getWorld().dropItemNaturally(
                        e.getEntity().getLocation(), brainItem.getItem().clone());
            return;
        }

        // Magic Silicone Dummy armor stand
        if (e.getEntity() instanceof ArmorStand stand) {
            String name = stand.getCustomName();
            String zhName = ChatColor.translateAlternateColorCodes('&', "&e魔法硅胶假人");
            String enName = ChatColor.translateAlternateColorCodes('&', "&eMagic Silicone Dummy");
            if ((name != null && name.equals(zhName)) || (name != null && name.equals(enName))) {
                e.getDrops().removeIf(drop -> drop.getType() == Material.ARMOR_STAND);
                SlimefunItem dummy = SlimefunItem.getById("MAGIC_SILICONE_DUMMY");
                if (dummy != null)
                    e.getEntity().getWorld().dropItemNaturally(
                            e.getEntity().getLocation(), dummy.getItem().clone());
            }
            return;
        }

        if (!(e.getEntity() instanceof Player deadPlayer)) return;

        // Self-kill check: player killed themselves (e.g., arrow shot straight up)
        Player killer = deadPlayer.getKiller();
        boolean selfKill = killer != null && killer.equals(deadPlayer);

        // Self-inflicted potion effect check
        boolean potionSuicide = false;
        DamageCause cause = deadPlayer.getLastDamageCause() != null
                ? deadPlayer.getLastDamageCause().getCause() : null;
        Map<String, Long> timers = selfDamageTimers.get(deadPlayer.getUniqueId());

        if (cause != null && timers != null) {
            for (Map.Entry<String, Long> entry : timers.entrySet()) {
                if (matchesDeathCause(entry.getKey(), cause)) {
                    potionSuicide = true;
                    break;
                }
            }
        }

        if (selfKill || potionSuicide) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skull) {
                skull.setOwningPlayer(deadPlayer);
                head.setItemMeta(skull);
            }
            deadPlayer.getWorld().dropItemNaturally(deadPlayer.getLocation(), head);
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    "&e" + deadPlayer.getName() + " &4&l自刎归天!"));
        }
    }

    private static boolean matchesDeathCause(String effectKey, DamageCause cause) {
        return switch (effectKey) {
            case "instant_damage" -> cause == DamageCause.MAGIC;
            case "poison" -> cause == DamageCause.POISON;
            case "wither" -> cause == DamageCause.WITHER;
            default -> false;
        };
    }

    static int cleanupStaleSelfDamageTimers() {
        int removed = 0;
        var it = selfDamageTimers.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            entry.getValue().entrySet().removeIf(e -> System.currentTimeMillis() >= e.getValue());
            if (entry.getValue().isEmpty()) { it.remove(); removed++; }
        }
        return removed;
    }

    static void clearSelfDamageTimers() {
        selfDamageTimers.clear();
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Villager v
                && v.getPersistentDataContainer().has(ItemKeys.LIBRARIAN)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent e) {
        if (e.getEntity().getPersistentDataContainer()
                .has(ItemKeys.LIBRARIAN_FRAME, org.bukkit.persistence.PersistentDataType.BOOLEAN)) {
            e.setCancelled(true);
        }
    }
}
