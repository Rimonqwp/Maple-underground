package maple.underground_caves.weapons.skill;

import maple.underground_caves.Test_dungeons;
import maple.underground_caves.gear.ImmortalSkullManager;
import maple.underground_state.stats.PlayerStatsData;
import maple.underground_state.stats.PlayerStatsFormulas;
import maple.underground_state.stats.PlayerStatsManager;
import maple.underground_caves.weapons.WeaponConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 冷凍技能：可被多把武器引用。定住目標、造成傷害（Hypixel 公式）、消耗智力、冷卻。
 * 施放時在 Action Bar 顯示本次傷害與當次連線最大傷害。
 */
public final class FreezeSkillRunner {

    private final Test_dungeons plugin;
    private final PlayerStatsManager statsManager;
    private final ImmortalSkullManager immortalSkullManager;

    /** 實體 UUID -> 冷凍結束時間戳（毫秒） */
    private final Map<UUID, Long> frozenUntil = new ConcurrentHashMap<>();
    /** 玩家 UUID -> 當次連線內該技能造成的最大傷害（用於傷害顯示） */
    private final Map<UUID, Double> maxSkillDamageThisSession = new ConcurrentHashMap<>();

    public FreezeSkillRunner(Test_dungeons plugin, PlayerStatsManager statsManager) {
        this(plugin, statsManager, null);
    }

    public FreezeSkillRunner(Test_dungeons plugin, PlayerStatsManager statsManager, ImmortalSkullManager immortalSkullManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
        this.immortalSkullManager = immortalSkullManager;
        startFreezeTicker();
    }

    /**
     * 對目標執行冷凍技能。會檢查藍量並扣除、造成傷害、施加定身與粒子。
     * @return true 表示成功施放
     */
    public boolean run(Player player, LivingEntity target, WeaponConfig.FreezeSkillConfig config) {
        if (player == null || target == null || config == null) return false;
        int cost = config.getManaCost();
        double wiseRatio = (plugin.getBloodSetManager() != null) ? plugin.getBloodSetManager().getWiseManaCostRatio(player) : 1.0;
        int actualCost = Math.max(0, (int) Math.ceil(cost * wiseRatio));
        if (plugin.getPlayerStatsRuntime() == null || !plugin.getPlayerStatsRuntime().trySpendMana(player, actualCost)) return false;

        // 扣藍（睿智套裝可減少消耗）
        PlayerStatsData data = statsManager.get(player);

        // Hypixel 技能傷害：智力含裝備加成（不死頭顱、洞窟法術頭盔），能力傷害含洞窟法術頭盔
        double effectiveInt = plugin.getPlayerStatsRuntime() != null
                ? plugin.getPlayerStatsRuntime().getEffectiveMaxIntelligence(player)
                : data.getIntelligence() + (immortalSkullManager != null ? immortalSkullManager.getBonusIntelligence(player) : 0);
        double abilityDmg = data.getAbilityDamage() + (plugin.getCaveSpecialHelmetManager() != null ? plugin.getCaveSpecialHelmetManager().getBonusAbilityDamagePercent(player) : 0);
        double rawDamage = PlayerStatsFormulas.abilityDamage(
                config.getBaseAbilityDamage(),
                effectiveInt,
                config.getAbilityScaling(),
                abilityDmg,
                1.0,
                1.0
        );
        double enemyDefense = getEnemyDefense(target);
        double finalDamage = PlayerStatsFormulas.damageAfterDefense(rawDamage, enemyDefense);
        finalDamage = plugin.applyDungeonBlessing(player, maple.underground_caves.dungeon.DungeonBlessingManager.BlessingStat.DAMAGE, finalDamage);
        finalDamage = Math.max(0, finalDamage);
        target.damage(finalDamage, player);

        // 傷害顯示：Action Bar 顯示本次傷害與當次連線最大傷害
        showSkillDamage(player, config.getName(), finalDamage);

        // 冷凍時長
        long endTime = System.currentTimeMillis() + (config.getFreezeDurationTicks() * 50L);
        frozenUntil.put(target.getUniqueId(), endTime);

        // 施放動畫：玩家/手部附近粒子 + 音效
        playCastEffect(player, config);
        // 目標身上立即一輪粒子
        playFrozenParticles(target, config);

        return true;
    }

    /** 取得目標防禦值（可擴展：從自訂屬性或 NBT 讀取，預設 0） */
    private double getEnemyDefense(LivingEntity target) {
        return 0;
    }

    /**
     * 在玩家聊天欄顯示技能造成的傷害，並更新/顯示當次連線最大傷害。
     */
    private void showSkillDamage(Player player, String skillName, double damage) {
        UUID uuid = player.getUniqueId();
        double prevMax = maxSkillDamageThisSession.getOrDefault(uuid, 0.0);
        if (damage > prevMax) {
            maxSkillDamageThisSession.put(uuid, damage);
        }
        double max = maxSkillDamageThisSession.get(uuid);
        int dmgInt = (int) Math.round(damage);
        int maxInt = (int) Math.round(max);
        Component line = Component.text(skillName + " 造成 ", NamedTextColor.GRAY)
                .append(Component.text(dmgInt + " 點傷害", NamedTextColor.RED).decorate(TextDecoration.BOLD))
                .append(Component.text(" (最大: " + maxInt + ")", NamedTextColor.YELLOW));
        player.sendMessage(line);
    }

    /** 玩家離線時可呼叫，清除該玩家的最大傷害紀錄（可選，不呼叫則下次進服仍會重新累計） */
    public void clearMaxDamage(UUID playerUuid) {
        maxSkillDamageThisSession.remove(playerUuid);
    }

    public boolean isFrozen(UUID entityUuid) {
        Long end = frozenUntil.get(entityUuid);
        if (end == null) return false;
        if (System.currentTimeMillis() >= end) {
            frozenUntil.remove(entityUuid);
            return false;
        }
        return true;
    }

    private void startFreezeTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                frozenUntil.entrySet().removeIf(e -> {
                    if (e.getValue() <= now) return true;
                    org.bukkit.entity.Entity entity = plugin.getServer().getEntity(e.getKey());
                    if (!(entity instanceof LivingEntity living) || living.isDead()) return true;
                    living.setVelocity(new Vector(0, 0, 0));
                    WeaponConfig.FreezeSkillConfig cfg = getDefaultFreezeConfig();
                    if (cfg != null) playFrozenParticles(living, cfg);
                    return false;
                });
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private WeaponConfig.FreezeSkillConfig getDefaultFreezeConfig() {
        var cfg = plugin.getWeaponManager().getConfig("cyclone_stick");
        if (cfg == null || !(cfg.getSkill() instanceof WeaponConfig.FreezeSkillConfig f)) return null;
        return f;
    }

    private void playCastEffect(Player player, WeaponConfig.FreezeSkillConfig config) {
        try {
            Particle p = Particle.valueOf(config.getCastParticle());
            player.getWorld().spawnParticle(p, player.getEyeLocation(), 25, 0.3, 0.5, 0.3, 0.02);
        } catch (Exception ignored) {}
        player.getWorld().playSound(player.getEyeLocation(), Sound.ENTITY_PLAYER_BREATH, 0.6f, 0.6f);
        player.getWorld().playSound(player.getEyeLocation(), Sound.BLOCK_GLASS_BREAK, 0.3f, 1.2f);
        if (config.isSpiralEnabled()) {
            playSpiralAnimation(player, config);
        }
    }

    /** 以玩家為中心，螺旋圈往外擴散的粒子動畫（水平面 XZ）。 */
    private void playSpiralAnimation(Player player, WeaponConfig.FreezeSkillConfig config) {
        Particle particle;
        try {
            particle = Particle.valueOf(config.getSpiralParticle());
        } catch (Exception e) { return; }
        final Location center = player.getLocation().clone().add(0, 1, 0);
        final int duration = config.getSpiralDurationTicks();
        final double maxRadius = config.getSpiralMaxRadius();
        final int points = config.getSpiralPointsPerRing();
        final int rotations = config.getSpiralRotations();

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (!player.isOnline() || !player.getWorld().equals(center.getWorld())) {
                    cancel();
                    return;
                }
                double progress = (double) tick / Math.max(1, duration);
                double radius = maxRadius * progress;
                double baseAngle = 2 * Math.PI * rotations * progress;
                for (int i = 0; i < points; i++) {
                    double angle = baseAngle + 2 * Math.PI * i / points;
                    double x = radius * Math.cos(angle);
                    double z = radius * Math.sin(angle);
                    Location at = center.clone().add(x, 0, z);
                    player.getWorld().spawnParticle(particle, at, 1, 0, 0, 0, 0);
                }
                tick++;
                if (tick >= duration) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void playFrozenParticles(LivingEntity entity, WeaponConfig.FreezeSkillConfig config) {
        try {
            Particle p = Particle.valueOf(config.getFrozenParticle());
            int n = config.getFrozenParticleCount();
            entity.getWorld().spawnParticle(p, entity.getLocation().add(0, entity.getHeight() * 0.5, 0), n, entity.getWidth() * 0.6, entity.getHeight() * 0.3, entity.getWidth() * 0.6, 0.01);
        } catch (Exception ignored) {}
    }
}
