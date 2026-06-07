package maple.underground_state.stats;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public final class PlayerStatsRuntimeListener implements Listener {

    private static final ThreadLocal<Boolean> CLIENT_DAMAGE_PULSE = new ThreadLocal<>();
    private final JavaPlugin plugin;
    private final PlayerStatsRuntime runtime;

    public PlayerStatsRuntimeListener(JavaPlugin plugin, PlayerStatsRuntime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
    }

    public static boolean isClientDamagePulse() {
        return Boolean.TRUE.equals(CLIENT_DAMAGE_PULSE.get());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) return;
                runtime.syncVirtualHealthBar(player);
            }
        }.runTaskLater(plugin, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        runtime.clearPendingVanillaDeath(player);
        runtime.markRespawnRecovery(player);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    runtime.clearRespawnRecovery(player);
                    return;
                }
                try {
                    boolean handled = runtime.handleRespawn(player);
                    if (!handled) {
                        runtime.restoreFullHealth(player);
                    }
                } finally {
                    runtime.clearRespawnRecovery(player);
                }
            }
        }.runTaskLater(plugin, 5L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (Boolean.TRUE.equals(CLIENT_DAMAGE_PULSE.get())) return;
        if (player.isDead()) return;
        if (runtime.shouldCancelIncomingDamage(player, event)) {
            event.setCancelled(true);
            return;
        }

        double finalDamage = event.getFinalDamage();
        if (finalDamage <= 0D) return;
        double vanillaHealthBefore = player.getHealth();

        event.setCancelled(true);
        applyDamageSourceKnockback(player, event);

        boolean bypassAllDefense = event.getCause() == EntityDamageEvent.DamageCause.VOID
                || event.getCause() == EntityDamageEvent.DamageCause.SUICIDE
                || event.getCause() == EntityDamageEvent.DamageCause.KILL;
        boolean trueDamage = !bypassAllDefense && PlayerStatsFormulas.consumeTrueDamageMark(player);
        double dealt;
        if (bypassAllDefense) {
            dealt = finalDamage;
        } else if (trueDamage) {
            double trueDefense = runtime.getEffectiveTrueDefense(player);
            dealt = PlayerStatsFormulas.damageAfterTrueDefense(finalDamage, Math.max(0D, trueDefense));
        } else {
            double defense = runtime.getEffectiveDefense(player);
            dealt = PlayerStatsFormulas.damageAfterDefense(finalDamage, Math.max(0D, defense));
        }
        double effectiveMaxHealth = runtime.getEffectiveMaxHealth(player);
        double effectiveCap = runtime.getEffectiveHealthCap(player, effectiveMaxHealth);
        PlayerStatsData data = runtime.getManager().get(player);
        double previousHealth = data.getCurrentHealth();
        double newHealth = Math.max(0D, previousHealth - dealt);
        runtime.setCurrentHealth(player, newHealth, effectiveMaxHealth, effectiveCap, false);

        if (newHealth <= 0D) {
            runtime.handleLethalDamage(player, event, previousHealth, dealt, effectiveMaxHealth, effectiveCap);
            runtime.syncVirtualHealthBar(player);
            return;
        }

        applyNativeClientDamagePulse(player, event, finalDamage, dealt);
        runtime.syncVirtualHealthBar(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED) return;
        double amount = event.getAmount();
        if (amount <= 0D) return;
        event.setCancelled(true);
        runtime.applySatiatedRegenToVirtualHealth(player, amount);
    }

    private void applyNativeClientDamagePulse(Player player,
                                              EntityDamageEvent originalEvent,
                                              double rawFinalDamage,
                                              double dealtVirtual) {
        if (!player.isOnline()) return;
        double health = player.getHealth();
        if (health <= 0.001D) return;

        double scale = Math.max(rawFinalDamage, dealtVirtual);
        double tickle = Math.max(0.08D, Math.min(2.0D, scale * 0.12D));
        tickle = Math.min(tickle, Math.max(0.04D, health - 0.02D));

        CLIENT_DAMAGE_PULSE.set(true);
        try {
            Entity source = resolveClientDamageAttacker(originalEvent);
            if (source != null) {
                player.damage(tickle, source);
            } else {
                player.damage(tickle);
            }
        } finally {
            CLIENT_DAMAGE_PULSE.remove();
        }
    }

    private static Entity resolveClientDamageAttacker(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) return null;
        Entity damager = byEntity.getDamager();
        if (damager instanceof LivingEntity) return damager;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }

    private static void applyDamageSourceKnockback(Player victim, EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) return;
        Entity damager = byEntity.getDamager();
        Location from;
        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof LivingEntity shooter) {
                from = shooter.getEyeLocation();
            } else {
                from = projectile.getLocation();
            }
        } else if (damager instanceof LivingEntity livingEntity) {
            from = livingEntity.getLocation();
        } else {
            from = damager.getLocation();
        }

        Vector delta = victim.getLocation().toVector().subtract(from.toVector());
        double px = delta.getX();
        double pz = delta.getZ();
        double lenSq = px * px + pz * pz;
        if (lenSq < 1.0E-8D) {
            if (damager instanceof LivingEntity livingEntity) {
                Vector direction = livingEntity.getLocation().getDirection();
                direction = new Vector(direction.getX(), 0D, direction.getZ());
                if (direction.lengthSquared() < 1.0E-8D) return;
                direction.normalize();
                victim.knockback(0.4D, direction.getX(), direction.getZ());
            }
            return;
        }

        Vector horizontal = new Vector(px, 0D, pz).normalize();
        double strength = damager instanceof Projectile ? 0.35D : 0.4D;
        victim.knockback(strength, horizontal.getX(), horizontal.getZ());
    }

}
