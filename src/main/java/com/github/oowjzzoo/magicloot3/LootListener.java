package com.github.oowjzzoo.magicloot3;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import com.github.oowjzzoo.magicloot3.items.TotemItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class LootListener implements Listener {

    private final Plugin plugin;

    private static final Set<String> SELF_DAMAGE_EFFECTS = Set.of("instant_damage", "poison", "wither");
    private static final Map<UUID, Map<String, Long>> selfDamageTimers = new HashMap<>();
    private static final Set<String> INSTANT_KEYS = Set.of("instant_damage", "instant_health");
    private static final Map<UUID, Long> instantCooldown = new HashMap<>();

    private static final List<DamageCause> CAUSES = Arrays.asList(
            DamageCause.BLOCK_EXPLOSION,
            DamageCause.CONTACT,
            DamageCause.ENTITY_ATTACK,
            DamageCause.ENTITY_EXPLOSION,
            DamageCause.FALL,
            DamageCause.FALLING_BLOCK,
            DamageCause.LAVA
    );

    private final Map<UUID, Map<String, Integer>> pendingInstants = new HashMap<>();
    private final Set<UUID> pendingSelfDamage = new HashSet<>();
    private boolean dispatchScheduled = false;

    public LootListener(Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
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

        // Protect the main End island from structure generation
        if (e.getWorld().getEnvironment() == World.Environment.THE_END
                && Math.abs(x) <= 400 && Math.abs(z) <= 400) return;

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

        // Melee weapon effects: only for applicable damage causes (FIRE/MAGIC/THORNS excluded)
        if (CAUSES.contains(e.getCause()) && e instanceof EntityDamageByEntityEvent damageEvent) {
            if (damageEvent.getDamager() instanceof LivingEntity attacker) {
                applyWeaponEffects(attacker, victim);
            }
        }

        // Projectile effects: read PDC from projectile entity (new logic, any damage cause)
        if (e instanceof EntityDamageByEntityEvent damageEvent
                && damageEvent.getDamager() instanceof Projectile projectile) {
            applyProjectileEffects(projectile, victim);
        }

        // Armor effects on victim
        if (CAUSES.contains(e.getCause())) {
            for (ItemStack armor : getArmorContents(victim)) {
                applyArmorEffects(victim, armor, e);
            }
        }
    }

    // --- Projectile launch: tag projectiles with weapon effects ---

    /** Bow: copy bow's EFFECTS PDC to arrow. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof LivingEntity shooter)) return;
        if (e.getBow() == null || !e.getBow().hasItemMeta()) return;
        String effects = e.getBow().getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.EFFECTS, org.bukkit.persistence.PersistentDataType.STRING);
        if (effects == null || effects.isEmpty()) return;

        e.getProjectile().getPersistentDataContainer()
                .set(ItemKeys.EFFECTS, org.bukkit.persistence.PersistentDataType.STRING, effects);
        e.getProjectile().getPersistentDataContainer()
                .set(ItemKeys.SHOOTER_UUID, org.bukkit.persistence.PersistentDataType.STRING,
                        shooter.getUniqueId().toString());
    }

    /** Crossbow / Trident: copy weapon's EFFECTS PDC to projectile. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof LivingEntity shooter)) return;
        Projectile proj = e.getEntity();

        String effects = null;
        if (proj instanceof Trident trident) {
            ItemStack tridentItem = trident.getItem();
            if (tridentItem.hasItemMeta()) {
                effects = tridentItem.getItemMeta().getPersistentDataContainer()
                        .get(ItemKeys.EFFECTS, org.bukkit.persistence.PersistentDataType.STRING);
            }
        } else if (proj instanceof Arrow || proj instanceof Firework) {
            ItemStack weapon = getItemInMainHand(shooter);
            if (weapon != null && weapon.hasItemMeta()) {
                effects = weapon.getItemMeta().getPersistentDataContainer()
                        .get(ItemKeys.EFFECTS, org.bukkit.persistence.PersistentDataType.STRING);
            }
        }
        if (effects == null || effects.isEmpty()) return;

        proj.getPersistentDataContainer()
                .set(ItemKeys.EFFECTS, org.bukkit.persistence.PersistentDataType.STRING, effects);
        proj.getPersistentDataContainer()
                .set(ItemKeys.SHOOTER_UUID, org.bukkit.persistence.PersistentDataType.STRING,
                        shooter.getUniqueId().toString());
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

    /** Reads effects from ItemStack PDC and applies them. */
    private void applyEffectsFromItem(ItemStack item, LivingEntity wearer, LivingEntity attacker) {
        if (item == null || !item.hasItemMeta()) return;
        var meta = item.getItemMeta();
        if (meta == null) return;
        String data = meta.getPersistentDataContainer().get(ItemKeys.EFFECTS,
                org.bukkit.persistence.PersistentDataType.STRING);
        applyEffectsFromData(data, wearer, attacker);
    }

    /** Parses effects string and applies to wearer/attacker. Language-independent.
     *  Effects format: "key:+/-:level,key:+/-:level,..." */
    private void applyEffectsFromData(String data, LivingEntity wearer, LivingEntity attacker) {
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

            if (INSTANT_KEYS.contains(enKey)) {
                if (isPositive) {
                    collectInstant(wearer, enKey, level, true);
                } else if (attacker != null) {
                    collectInstant(attacker, enKey, level, false);
                }
            } else if (isPositive) {
                if (wearer instanceof Player && SELF_DAMAGE_EFFECTS.contains(enKey)) {
                    long durationMs = (level + 1) * 1000L;
                    Map<String, Long> t = selfDamageTimers.computeIfAbsent(wearer.getUniqueId(), k -> new HashMap<>());
                    t.entrySet().removeIf(e2 -> System.currentTimeMillis() >= e2.getValue());
                    t.put(enKey, System.currentTimeMillis() + durationMs);
                }
                wearer.addPotionEffect(new PotionEffect(type, (level + 1) * 20, level));
            } else if (attacker != null) {
                attacker.addPotionEffect(new PotionEffect(type, (level + 1) * 20, level));
            }
        }
    }

    /** Read EFFECTS + SHOOTER_UUID from projectile PDC, resolve shooter, apply effects. */
    private void applyProjectileEffects(Projectile projectile, LivingEntity victim) {
        var pdc = projectile.getPersistentDataContainer();
        String effects = pdc.get(ItemKeys.EFFECTS, org.bukkit.persistence.PersistentDataType.STRING);
        if (effects == null || effects.isEmpty()) return;

        // Resolve shooter
        LivingEntity shooter = null;
        String uuidStr = pdc.get(ItemKeys.SHOOTER_UUID, org.bukkit.persistence.PersistentDataType.STRING);
        if (uuidStr != null) {
            try {
                Entity e = Bukkit.getEntity(UUID.fromString(uuidStr));
                if (e instanceof LivingEntity le && le.isValid()) shooter = le;
            } catch (IllegalArgumentException ignored) {}
        }

        // Apply weapon effects from projectile PDC (wearer=shooter, attacker=victim)
        applyEffectsFromData(effects, shooter, victim);

        // Apply victim's armor effects (same as melee path)
        for (ItemStack armor : getArmorContents(victim)) {
            applyEffectsFromItem(armor, victim, shooter);
        }
    }

    private void collectInstant(LivingEntity target, String enKey, int level, boolean isSelfDamage) {
        UUID targetId = target.getUniqueId();
        Long cooldownEnd = instantCooldown.get(targetId);
        if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) return;

        pendingInstants.computeIfAbsent(targetId, k -> new HashMap<>())
                .merge(enKey, level, Math::max);
        if (isSelfDamage && "instant_damage".equals(enKey) && target instanceof Player) {
            pendingSelfDamage.add(targetId);
        }
        if (!dispatchScheduled) {
            dispatchScheduled = true;
            Bukkit.getScheduler().runTask(plugin, this::dispatchInstantEffects);
        }
    }

    private void dispatchInstantEffects() {
        dispatchScheduled = false;

        for (var entry : pendingInstants.entrySet()) {
            UUID targetId = entry.getKey();
            Map<String, Integer> effects = entry.getValue();

            LivingEntity target = (LivingEntity) Bukkit.getEntity(targetId);
            if (target == null || !target.isValid()) continue;

            int dmgAmp = effects.getOrDefault("instant_damage", -1);
            int healAmp = effects.getOrDefault("instant_health", -1);
            boolean selfDmg = pendingSelfDamage.contains(targetId);

            instantCooldown.put(targetId, System.currentTimeMillis() + 150L);

            int tick = 0;
            if (dmgAmp >= 0) {
                final int amp = dmgAmp;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!target.isValid()) return;
                    if (selfDmg) {
                        setSelfDamageTimer(targetId, "instant_damage", amp);
                    }
                    applyInstantPotion(target, PotionEffectType.INSTANT_DAMAGE, amp);
                }, ++tick);
            }
            ++tick; // tick 2 is intentionally empty
            if (healAmp >= 0) {
                final int amp = healAmp;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!target.isValid()) return;
                    applyInstantPotion(target, PotionEffectType.INSTANT_HEALTH, amp);
                }, ++tick);
            }
        }

        pendingInstants.clear();
        pendingSelfDamage.clear();
    }

    private void applyInstantPotion(LivingEntity target, PotionEffectType type, int amplifier) {
        int oldTicks = target.getNoDamageTicks();
        if (oldTicks > 0) {
            target.setNoDamageTicks(0);
        }
        target.addPotionEffect(new PotionEffect(type, 1, amplifier));
        if (oldTicks > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (target.isValid() && target.getNoDamageTicks() == 0) {
                    target.setNoDamageTicks(Math.max(0, oldTicks - 1));
                }
            }, 1L);
        }
    }

    private void setSelfDamageTimer(UUID playerId, String enKey, int level) {
        Map<String, Long> t = selfDamageTimers.computeIfAbsent(playerId, k -> new HashMap<>());
        t.entrySet().removeIf(e2 -> System.currentTimeMillis() >= e2.getValue());
        t.put(enKey, System.currentTimeMillis() + 250L); // 5 ticks
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
            SlimefunItem brainItem = SlimefunItem.getById("MAGICLOOT_LOST_LIBRARIAN_BRAIN");
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
                SlimefunItem dummy = SlimefunItem.getById("MAGICLOOT_MAGIC_SILICONE_DUMMY");
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
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Long> entry : timers.entrySet()) {
                if (now < entry.getValue() && matchesDeathCause(entry.getKey(), cause)) {
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
            Bukkit.broadcastMessage(Messages.get("death.suicide_broadcast", deadPlayer.getName()));
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
        var cit = instantCooldown.entrySet().iterator();
        while (cit.hasNext()) {
            if (System.currentTimeMillis() >= cit.next().getValue()) { cit.remove(); removed++; }
        }
        return removed;
    }

    static void clearSelfDamageTimers() {
        selfDamageTimers.clear();
        instantCooldown.clear();
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
                .has(ItemKeys.LIBRARIAN_FRAME, PersistentDataType.BOOLEAN)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;

        EquipmentSlot hand = e.getHand();
        if (hand == null) return;

        ItemStack totem = hand == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();

        if (totem == null || !totem.hasItemMeta()) return;

        // Check if this is a sealed Crystal Totem
        Byte sealed = totem.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.TOTEM_SEALED, PersistentDataType.BYTE);
        if (sealed == null || sealed != 1) return;

        Integer forgeObj = totem.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.TOTEM_FORGE_COUNT, PersistentDataType.INTEGER);
        int forge = forgeObj != null ? forgeObj : 0;

        if (forge > 0) {
            // Clone before vanilla consumes the original
            ItemStack saved = totem.clone();
            TotemItem.decrementForgeCount(saved);

            final EquipmentSlot slot = hand;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (slot == EquipmentSlot.HAND) {
                    player.getInventory().setItemInMainHand(saved);
                } else {
                    player.getInventory().setItemInOffHand(saved);
                }
                player.sendMessage(Messages.get("totem.forge.resurrect", forge - 1));
            });
        }
        // forge == 0: let vanilla consume the totem
    }
}
