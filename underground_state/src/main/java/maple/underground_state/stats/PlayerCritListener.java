package maple.underground_state.stats;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerCritListener implements Listener {

    private static final ThreadLocal<Boolean> FEROCITY_CHAIN_DAMAGE = new ThreadLocal<>();
    private static final NamespacedKey UNDERGROUND_ITEM_WEAPON_ID_KEY =
            new NamespacedKey("underground_item", "weapon_id");

    private final PlayerStatsManager statsManager;
    private final PlayerStatsRuntime runtime;

    public PlayerCritListener(PlayerStatsManager statsManager,
                              PlayerStatsRuntime runtime) {
        this.statsManager = statsManager;
        this.runtime = runtime;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamageEntity(EntityDamageByEntityEvent event) {
        Player player = getPlayerDamager(event.getDamager());
        if (player == null) return;
        if (PlayerStatsRuntimeListener.isClientDamagePulse()) return;
        if (Boolean.TRUE.equals(FEROCITY_CHAIN_DAMAGE.get())) return;

        PlayerStatsData data = statsManager.get(player);
        boolean meleeHit = event.getDamager() instanceof Player;
        boolean rangedArrowHit = event.getDamager() instanceof AbstractArrow;

        double damage = event.getDamage();
        if (meleeHit) {
            double rawCooldown = Math.max(0D, Math.min(1D, player.getAttackCooldown()));
            double effectiveCooldown = PlayerStatsFormulas.effectiveAttackCooldown(
                    rawCooldown,
                    runtime.getEffectiveBonusAttackSpeed(player)
            );
            damage = PlayerStatsFormulas.adjustDamageForAttackCooldown(damage, rawCooldown, effectiveCooldown);
        }

        double effectiveStrength = runtime.getEffectiveStrength(player);
        if (meleeHit || rangedArrowHit) {
            damage = PlayerStatsFormulas.damageWithStrength(damage, effectiveStrength, hasUndergroundItemWeapon(player));
        }

        damage = runtime.applyOutgoingDamageModifiers(player, event, damage);
        if (event.getEntity() instanceof Monster) {
            double monsterDamageMultiplier = 1D + Math.max(0D, data.getMonsterDamagePercent()) / 100D;
            damage *= monsterDamageMultiplier;
        }

        double critChance = runtime.getEffectiveCritChance(player);
        boolean critical = critChance >= 100D;
        if (critical) {
            damage *= 2.0D;
        }

        double finalDamage = Math.max(0.0001D, damage);
        event.setDamage(finalDamage);

        if (meleeHit && event.getEntity() instanceof LivingEntity victim) {
            int extraHits = computeFerocityExtraHits(runtime.getEffectiveFerocity(player));
            if (extraHits > 0) {
                scheduleFerocityHits(player, victim, finalDamage, extraHits);
            }
        }
    }

    private int computeFerocityExtraHits(double ferocity) {
        double clamped = Math.max(0D, ferocity);
        int guaranteed = (int) Math.floor(clamped / 100D);
        double remainderChance = (clamped % 100D) / 100D;
        return guaranteed + (Math.random() < remainderChance ? 1 : 0);
    }

    private void scheduleFerocityHits(Player attacker, LivingEntity victim, double rawDamage, int extraHits) {
        JavaPlugin plugin = runtime.getPlugin();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!attacker.isOnline()) return;
            for (int i = 0; i < extraHits; i++) {
                if (!victim.isValid() || victim.isDead()) break;
                FEROCITY_CHAIN_DAMAGE.set(true);
                try {
                    victim.damage(Math.max(0.0001D, rawDamage), attacker);
                } finally {
                    FEROCITY_CHAIN_DAMAGE.remove();
                }
            }
        });
    }

    private static Player getPlayerDamager(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private static boolean hasUndergroundItemWeapon(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(UNDERGROUND_ITEM_WEAPON_ID_KEY, PersistentDataType.STRING);
    }
}
