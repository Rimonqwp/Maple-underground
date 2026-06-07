package maple.underground_caves.weapons.skill;

import maple.underground_caves.Test_dungeons;
import maple.underground_caves.gear.ImmortalSkullManager;
import maple.underground_state.stats.PlayerStatsData;
import maple.underground_state.stats.PlayerStatsFormulas;
import maple.underground_state.stats.PlayerStatsManager;
import maple.underground_caves.weapons.WeaponConfig;
import org.bukkit.*;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Showtime 技能：發射氣球（隱身盔甲架+隨機頭顱），沿視線飛行並旋轉，命中怪物/Boss 時爆炸，對半徑內非玩家造成 Base Ability Damage。
 */
public final class ShowtimeSkillRunner {

    private static final String BALLOON_TEXTURE_PREFIX = "http://textures.minecraft.net/texture/";
    /** 只判定盔甲架「頭部」是否碰到怪物（頭部約在腳底 +1 格），半徑小才精準 */
    private static final double HEAD_HIT_RADIUS = 0.5;
    /** 氣球飛行超過此格數未命中則自動爆炸 */
    private static final double MAX_DISTANCE_BLOCKS = 15.0;

    /** 用於標記 Showtime 煙花，避免煙花爆炸造成重複傷害（傷害由本類計算） */
    public static final String SHOWTIME_FIREWORK_PDC = "showtime_firework";

    private final JavaPlugin plugin;
    private final PlayerStatsManager statsManager;
    private final ImmortalSkullManager immortalSkullManager;
    private final NamespacedKey showtimeFireworkKey;

    public ShowtimeSkillRunner(JavaPlugin plugin, PlayerStatsManager statsManager, ImmortalSkullManager immortalSkullManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
        this.immortalSkullManager = immortalSkullManager;
        this.showtimeFireworkKey = new NamespacedKey(plugin, SHOWTIME_FIREWORK_PDC);
    }

    public NamespacedKey getShowtimeFireworkKey() {
        return showtimeFireworkKey;
    }

    /**
     * 施放 Showtime：扣藍、在玩家前方生成氣球盔甲架並發射。
     */
    public boolean run(Player player, WeaponConfig.ShowtimeSkillConfig config) {
        if (player == null || config == null) return false;
        List<String> textures = config.getBalloonTextures();
        if (textures.isEmpty()) return false;

        if (plugin instanceof Test_dungeons td) {
            if (td.getPlayerStatsRuntime() == null || !td.getPlayerStatsRuntime().trySpendMana(player, config.getManaCost())) {
                return false;
            }
        } else {
            PlayerStatsData data = statsManager.get(player).copy();
            data.setCurrentIntelligence(data.getCurrentIntelligence() - config.getManaCost());
            statsManager.set(player.getUniqueId(), data);
        }

        Location spawnAt = player.getEyeLocation().clone().add(player.getEyeLocation().getDirection().multiply(1.5));
        // 玩家朝向的單位向量，每 tick 移動的位移（盔甲架不受 setVelocity 影響，需手動 teleport）
        Vector direction = player.getEyeLocation().getDirection().normalize().multiply(config.getBalloonSpeed());

        ArmorStand as = player.getWorld().spawn(spawnAt, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setSmall(false);
            stand.setMarker(false);
            stand.setCollidable(false);
            stand.setHelmet(createSkullWithTexture(textures.get((int) (Math.random() * textures.size()))));
        });

        UUID shooterId = player.getUniqueId();
        double baseDamage = config.getBaseAbilityDamage();
        double radius = config.getExplosionRadius();

        Location lastLoc = spawnAt.clone();
        new BukkitRunnable() {
            double distanceTraveled = 0;

            @Override
            public void run() {
                if (!as.isValid() || !player.isOnline()) {
                    cancel();
                    return;
                }
                Location now = as.getLocation();
                now.add(direction);
                as.teleport(now);
                as.setHeadPose(as.getHeadPose().add(0.05, 0.1, 0.05));
                distanceTraveled += as.getLocation().distance(lastLoc);
                lastLoc.setWorld(as.getWorld());
                lastLoc.setX(as.getLocation().getX());
                lastLoc.setY(as.getLocation().getY());
                lastLoc.setZ(as.getLocation().getZ());
                if (distanceTraveled >= MAX_DISTANCE_BLOCKS) {
                    explode(as.getLocation(), shooterId, baseDamage, radius);
                    as.remove();
                    cancel();
                    return;
                }
                // 只以盔甲架頭部位置做碰撞判定（頭在腳底 +1 格）
                Location headAt = as.getLocation().clone().add(0, 1, 0);
                for (LivingEntity nearby : headAt.getNearbyLivingEntities(HEAD_HIT_RADIUS)) {
                    if (nearby.equals(as)) continue;
                    if (nearby instanceof Player) continue;
                    if (nearby.getUniqueId().equals(shooterId)) continue;
                    explode(as.getLocation(), shooterId, baseDamage, radius);
                    as.remove();
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private ItemStack createSkullWithTexture(String textureHash) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
        skull.editMeta(SkullMeta.class, meta -> {
            try {
                org.bukkit.profile.PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                profile.getTextures().setSkin(new URL(BALLOON_TEXTURE_PREFIX + textureHash));
                meta.setOwnerProfile(profile);
            } catch (MalformedURLException e) {
                plugin.getLogger().warning("Showtime invalid texture hash: " + textureHash);
            }
        });
        return skull;
    }

    private void explode(Location loc, UUID shooterId, double baseAbilityDamage, double radius) {
        spawnShowtimeFirework(loc);

        Player shooter = Bukkit.getPlayer(shooterId);
        double effectiveInt = 0;
        double abilityDamageStat = 0;
        if (shooter != null && plugin instanceof Test_dungeons td) {
            PlayerStatsData data = td.getPlayerStatsManager().get(shooter);
            effectiveInt = td.getPlayerStatsRuntime() != null
                    ? td.getPlayerStatsRuntime().getEffectiveMaxIntelligence(shooter)
                    : data.getIntelligence() + (immortalSkullManager != null ? immortalSkullManager.getBonusIntelligence(shooter) : 0);
            abilityDamageStat = data.getAbilityDamage() + (td.getCaveSpecialHelmetManager() != null ? td.getCaveSpecialHelmetManager().getBonusAbilityDamagePercent(shooter) : 0);
        }
        double rawDamage = PlayerStatsFormulas.abilityDamage(baseAbilityDamage, effectiveInt, 0, abilityDamageStat, 1.0, 1.0);

        // 碰撞已改為頭部小範圍，觸發時怪物必在爆炸點附近，用 config 半徑即可
        double damageRadius = radius;
        for (LivingEntity entity : loc.getWorld().getNearbyLivingEntities(loc, damageRadius)) {
            if (entity instanceof Player) continue;
            if (entity.getUniqueId().equals(shooterId)) continue;
            double defense = getEnemyDefense(entity);
            double finalDmg = PlayerStatsFormulas.damageAfterDefense(rawDamage, defense);
            if (shooter != null && plugin instanceof Test_dungeons td) {
                finalDmg = td.applyDungeonBlessing(shooter, maple.underground_caves.dungeon.DungeonBlessingManager.BlessingStat.DAMAGE, finalDmg);
            }
            if (shooter != null) entity.damage(Math.max(0, finalDmg), shooter);
            else entity.damage(Math.max(0, finalDmg));
        }
    }

    private static final Color[] FIREWORK_COLORS = {
            Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.AQUA, Color.BLUE,
            Color.FUCHSIA, Color.MAROON, Color.NAVY, Color.PURPLE, Color.TEAL, Color.WHITE
    };

    /** 煙花效果：固定 BALL_LARGE，只隨機主色與 fade 色 */
    private void spawnShowtimeFirework(Location loc) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Color> colors = new ArrayList<>(Arrays.asList(FIREWORK_COLORS));
        Collections.shuffle(colors);
        int mainCount = 1 + r.nextInt(3);
        Color[] mainColors = colors.subList(0, Math.min(mainCount, colors.size())).toArray(Color[]::new);
        Collections.shuffle(colors);
        int fadeCount = r.nextInt(3);
        Color[] fadeColors = fadeCount == 0 ? new Color[0] : colors.subList(0, Math.min(fadeCount, colors.size())).toArray(Color[]::new);

        org.bukkit.FireworkEffect.Builder builder = org.bukkit.FireworkEffect.builder()
                .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                .withColor(mainColors)
                .trail(true)
                .flicker(true);
        if (fadeColors.length > 0) builder.withFade(fadeColors);

        org.bukkit.FireworkEffect effect = builder.build();
        Firework fw = loc.getWorld().spawn(loc, Firework.class, firework -> {
            FireworkMeta meta = firework.getFireworkMeta();
            meta.setPower(0);
            meta.addEffect(effect);
            firework.setFireworkMeta(meta);
            firework.getPersistentDataContainer().set(showtimeFireworkKey, PersistentDataType.BYTE, (byte) 1);
        });
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, fw::detonate, 1L);
    }

    private double getEnemyDefense(LivingEntity entity) {
        if (entity.getAttribute(Attribute.ARMOR) != null) {
            return entity.getAttribute(Attribute.ARMOR).getValue();
        }
        return 0;
    }
}
