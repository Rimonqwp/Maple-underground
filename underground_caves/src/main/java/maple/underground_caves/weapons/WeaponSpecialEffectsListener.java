package maple.underground_caves.weapons;

import maple.underground_caves.Test_dungeons;
import maple.underground_caves.gear.SieveSetManager;
import maple.underground_state.stats.PlayerStatsData;
import maple.underground_state.stats.PlayerStatsFormulas;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * 武器特殊效果：餘波斬 cleave、憐觸回血、殭屍騎士套裝加成、洞窟共鳴劍職業加成、翠玉之刃金幣增傷。
 */
public final class WeaponSpecialEffectsListener implements Listener {

    private static final double CLEAVE_DAMAGE_RATIO = 0.5;
    private static final int LOVE_TAP_HEAL = 10;
    private static final int ZOMBIE_KNIGHT_SIEVE_BONUS_STR = 30;
    private static final double EMERALD_COIN_CAP = 2_000_000_000.0;

    private final WeaponManager weaponManager;
    private final Test_dungeons plugin;

    public WeaponSpecialEffectsListener(WeaponManager weaponManager, Test_dungeons plugin) {
        this.weaponManager = weaponManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType().isAir()) return;
        String weaponId = weaponManager.getWeaponId(weapon);
        if (weaponId == null) return;
        WeaponConfig cfg = weaponManager.getConfig(weaponId);
        if (cfg == null) return;

        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        double damage = event.getDamage();

        // 翠玉之刃：傷害 = base_damage + 2.5 * (coins^0.25)，金幣上限 20 億
        if ("emerald_blade".equals(weaponId)) {
            int base = getInt(cfg.getStats(), "base_damage", 130);
            long coins = getPlayerCoins(player);
            double d = base + 2.5 * Math.pow(Math.min(coins, (long) EMERALD_COIN_CAP), 0.25);
            event.setDamage(Math.max(0.0001, d));
            damage = d;
        }

        // 洞窟共鳴劍：依地牢職業加傷與力量係數（0=治療 1=法師 2=戰士 3=弓手 4=坦克）；靈魂模式不套用職業加成
        if ("scarf_adaptive_sword".equals(weaponId) && !plugin.isPlayerDungeonGhost(player)) {
            int classIdx = plugin.getDungeonClassIndex(player);
            if (classIdx >= 0) {
                Map<String, String> stats = cfg.getStats();
                int addDamage = 0;
                int addStr = 0;
                if (classIdx == 0) { /* healer */ }
                else if (classIdx == 1) { addDamage = getInt(stats, "mage_damage", 10); }
                else if (classIdx == 2) { addDamage = getInt(stats, "berserk_damage", 10); addStr = getInt(stats, "berserk_strength", 75); }
                else if (classIdx == 3) { /* archer */ }
                else if (classIdx == 4) { /* tank */ }
                double strMult = addStr > 0 ? (1 + addStr / 100.0) : 1.0;
                event.setDamage(Math.max(0.0001, (damage + addDamage) * strMult));
                damage = event.getDamage();
            }
        }

        // 殭屍騎士劍：穿齊篩網套裝時 +30 力量（約 30% 傷害）
        if ("zombie_knight_iron".equals(weaponId)) {
            SieveSetManager sieve = plugin.getSieveSetManager();
            if (sieve != null && sieve.isWearingFullSet(player)) {
                event.setDamage(damage * (1.0 + ZOMBIE_KNIGHT_SIEVE_BONUS_STR / 100.0));
                damage = event.getDamage();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType().isAir()) return;
        String weaponId = weaponManager.getWeaponId(weapon);
        if (weaponId == null) return;
        WeaponConfig cfg = weaponManager.getConfig(weaponId);
        if (cfg == null || !(event.getEntity() instanceof LivingEntity victim)) return;

        double damage = event.getDamage();

        // 餘波斬：主目標周圍 cleave_radius 格內怪物受到部分傷害
        String cleaveRadiusStr = cfg.getStats().get("cleave_radius");
        if (cleaveRadiusStr != null) {
            int radius;
            try { radius = Integer.parseInt(cleaveRadiusStr.trim()); } catch (NumberFormatException e) { radius = 0; }
            if (radius > 0) {
                double cleaveDmg = damage * CLEAVE_DAMAGE_RATIO;
                for (LivingEntity nearby : victim.getLocation().getNearbyLivingEntities(radius)) {
                    if (nearby == player || nearby == victim) continue;
                    if (!(nearby instanceof Monster)) continue;
                    nearby.damage(Math.max(0.0001, cleaveDmg), player);
                }
            }
        }

        // 憐觸石劍：命中回血 +10（經 Vitality），只增加虛擬血量 PlayerStats，不提高實體上限
        if ("love_tap_stone".equals(weaponId)) {
            double vitalityEff = plugin.getEffectiveVitalityForSelfHeal(player);
            double heal = PlayerStatsFormulas.totalHealthGained(LOVE_TAP_HEAL, vitalityEff);
            if (heal <= 0) return;
            if (plugin.getPlayerStatsRuntime() != null) {
                plugin.getPlayerStatsRuntime().heal(player, heal);
            } else {
                double virtualMax = plugin.getEffectiveMaxHealth(player);
                PlayerStatsData stats = plugin.getPlayerStatsManager().get(player);
                double newVirtual = Math.min(stats.getCurrentHealth() + heal, virtualMax);
                stats.setCurrentHealthWithCap(newVirtual, virtualMax);
                plugin.syncVirtualHealthBar(player);
            }
        }
    }

    private static int getInt(Map<String, String> stats, String key, int def) {
        if (stats == null) return def;
        String v = stats.get(key);
        if (v == null) return def;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    /** 玩家金幣數（預設 0，可接經濟插件） */
    private long getPlayerCoins(Player player) {
        return 0;
    }
}
