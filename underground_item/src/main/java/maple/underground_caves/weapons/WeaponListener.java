package maple.underground_caves.weapons;

import maple.underground_item.UndergroundItem;
import maple.underground_caves.weapons.skill.FreezeSkillRunner;
import maple.underground_caves.weapons.skill.ShowtimeSkillRunner;
import maple.underground_state.stats.PlayerStatsData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 武器右鍵使用：指向怪物/Boss 時觸發技能（如冷凍）。
 */
public final class WeaponListener implements Listener {

    private final UndergroundItem plugin;
    private final WeaponManager weaponManager;
    private final FreezeSkillRunner freezeSkillRunner;
    private final ShowtimeSkillRunner showtimeSkillRunner;

    private final Map<UUID, Long> freezeCooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> showtimeCooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dreadlordCooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dragonRageCooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> teleportCooldownUntil = new ConcurrentHashMap<>();
    private final Set<UUID> dreadlordSkullIds = ConcurrentHashMap.newKeySet();

    public WeaponListener(UndergroundItem plugin, WeaponManager weaponManager, FreezeSkillRunner freezeSkillRunner) {
        this(plugin, weaponManager, freezeSkillRunner, null);
    }

    public WeaponListener(UndergroundItem plugin, WeaponManager weaponManager, FreezeSkillRunner freezeSkillRunner, ShowtimeSkillRunner showtimeSkillRunner) {
        this.plugin = plugin;
        this.weaponManager = weaponManager;
        this.freezeSkillRunner = freezeSkillRunner;
        this.showtimeSkillRunner = showtimeSkillRunner;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        var item = player.getInventory().getItemInMainHand();
        String weaponId = weaponManager.getWeaponId(item);
        if (weaponId == null) return;
        if (plugin.isPlayerDungeonGhost(player)) {
            event.setCancelled(true);
            return;
        }

        WeaponConfig cfg = weaponManager.getConfig(weaponId);
        if (cfg == null) return;

        event.setCancelled(true);
        long now = System.currentTimeMillis();

        // 骷髏領主之刃、龍怒、瞬步：從 ability/stats 讀參數，無 skill 區塊
        if ("dreadlord_iron".equals(weaponId)) {
            handleDreadlord(player, cfg, now);
            return;
        }
        if ("dragon_rage_diamond".equals(weaponId)) {
            handleDragonRage(player, cfg, now);
            return;
        }
        if ("teleport_diamond".equals(weaponId)) {
            handleTeleport(player, cfg, now);
            return;
        }

        if (cfg.getSkill() == null) return;

        // Showtime：右鍵發射氣球，不需瞄準目標
        if (cfg.getSkill() instanceof WeaponConfig.ShowtimeSkillConfig showtimeConfig) {
            if (showtimeSkillRunner == null) return;
            long cooldownMs = (long) (showtimeConfig.getCooldownSeconds() * 1000 * plugin.getCooldownMultiplier(player));
            long until = showtimeCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
            if (now < until) {
                long left = (until - now) / 1000;
                player.sendActionBar(Component.text("技能冷卻中，剩餘 " + (left + 1) + " 秒", NamedTextColor.GRAY));
                return;
            }
            double currentMana = plugin.getPlayerStatsManager().get(player).getCurrentIntelligence();
            if (currentMana < showtimeConfig.getManaCost()) {
                player.sendActionBar(Component.text("智力不足！需要 " + showtimeConfig.getManaCost() + "，目前 " + (int) currentMana, NamedTextColor.RED));
                return;
            }
            if (showtimeSkillRunner.run(player, showtimeConfig)) {
                showtimeCooldownUntil.put(player.getUniqueId(), now + cooldownMs);
            }
            return;
        }

        // Freeze：需瞄準 8 格內怪物/Boss
        if (!(cfg.getSkill() instanceof WeaponConfig.FreezeSkillConfig freezeConfig)) return;

        LivingEntity target = getTargetEntity(player, 8.0);
        if (target == null) {
            player.sendActionBar(Component.text("請對準 8 格內的怪物或 Boss 再右鍵使用", NamedTextColor.RED));
            return;
        }

        long cooldownMs = (long) (freezeConfig.getCooldownSeconds() * 1000 * plugin.getCooldownMultiplier(player));
        long until = freezeCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        if (now < until) {
            long left = (until - now) / 1000;
            player.sendActionBar(Component.text("技能冷卻中，剩餘 " + (left + 1) + " 秒", NamedTextColor.GRAY));
            return;
        }

        double currentMana = plugin.getPlayerStatsManager().get(player).getCurrentIntelligence();
        if (currentMana < freezeConfig.getManaCost()) {
            player.sendActionBar(Component.text("智力不足！需要 " + freezeConfig.getManaCost() + "，目前 " + (int) currentMana, NamedTextColor.RED));
            return;
        }

        if (!freezeSkillRunner.run(player, target, freezeConfig)) return;

        freezeCooldownUntil.put(player.getUniqueId(), now + cooldownMs);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.NORMAL, ignoreCancelled = true)
    public void onDreadlordSkullHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof WitherSkull skull)) return;
        if (!dreadlordSkullIds.remove(skull.getUniqueId())) return;
        double baseDmg = getStoredDreadlordDamage(skull);
        event.setDamage(baseDmg);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        freezeSkillRunner.clearMaxDamage(event.getPlayer().getUniqueId());
    }

    private void handleDreadlord(Player player, WeaponConfig cfg, long now) {
        int manaCost = getInt(cfg.getStats(), "mana_cost", 40);
        double baseDmg = getDouble(cfg.getStats(), "base_ability_damage", 500);
        double cooldownSec = getDouble(cfg.getStats(), "cooldown_seconds", 1);
        long cooldownMs = (long) (cooldownSec * 1000 * plugin.getCooldownMultiplier(player));
        long until = dreadlordCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        if (now < until) {
            long left = (until - now) / 1000;
            player.sendActionBar(Component.text("技能冷卻中，剩餘 " + (left + 1) + " 秒", NamedTextColor.GRAY));
            return;
        }
        double currentMana = plugin.getPlayerStatsManager().get(player).getCurrentIntelligence();
        if (currentMana < manaCost) {
            player.sendActionBar(Component.text("智力不足！需要 " + manaCost + "，目前 " + (int) currentMana, NamedTextColor.RED));
            return;
        }
        PlayerStatsData data = plugin.getPlayerStatsManager().get(player);
        data.setCurrentIntelligence(Math.max(0, data.getCurrentIntelligence() - manaCost));
        Vector dir = player.getEyeLocation().getDirection().normalize();
        WitherSkull skull = player.getWorld().spawn(player.getEyeLocation().add(dir.clone().multiply(1.5)), WitherSkull.class, s -> {
            s.setDirection(dir);
            s.setVelocity(dir.multiply(1.8));
            s.setShooter(player);
        });
        dreadlordSkullIds.add(skull.getUniqueId());
        dreadlordCooldownUntil.put(player.getUniqueId(), now + cooldownMs);
        // 命中時傷害在 onDreadlordSkullHit 設為 baseDmg；需傳遞 baseDmg，用 PDC 或暫存
        storeDreadlordDamage(skull, baseDmg);
    }

    private void storeDreadlordDamage(WitherSkull skull, double damage) {
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "dreadlord_damage");
        skull.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.DOUBLE, damage);
    }

    private double getStoredDreadlordDamage(WitherSkull skull) {
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "dreadlord_damage");
        Double v = skull.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.DOUBLE);
        return v != null ? v : 500;
    }

    private void handleDragonRage(Player player, WeaponConfig cfg, long now) {
        int manaCost = getInt(cfg.getStats(), "mana_cost", 100);
        double baseDmg = getDouble(cfg.getStats(), "base_ability_damage", 12000);
        double cooldownSec = getDouble(cfg.getStats(), "cooldown_seconds", 2);
        long cooldownMs = (long) (cooldownSec * 1000 * plugin.getCooldownMultiplier(player));
        long until = dragonRageCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        if (now < until) {
            long left = (until - now) / 1000;
            player.sendActionBar(Component.text("技能冷卻中，剩餘 " + (left + 1) + " 秒", NamedTextColor.GRAY));
            return;
        }
        double currentMana = plugin.getPlayerStatsManager().get(player).getCurrentIntelligence();
        if (currentMana < manaCost) {
            player.sendActionBar(Component.text("智力不足！需要 " + manaCost + "，目前 " + (int) currentMana, NamedTextColor.RED));
            return;
        }
        PlayerStatsData data = plugin.getPlayerStatsManager().get(player);
        data.setCurrentIntelligence(Math.max(0, data.getCurrentIntelligence() - manaCost));
        dragonRageCooldownUntil.put(player.getUniqueId(), now + cooldownMs);
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        double coneDist = 6;
        double coneAngle = 0.6;
        for (LivingEntity e : eye.getWorld().getNearbyLivingEntities(eye, coneDist + 2)) {
            if (e == player) continue;
            Vector to = e.getLocation().add(0, 1, 0).toVector().subtract(eye.toVector()).normalize();
            if (dir.dot(to) < coneAngle) continue;
            e.damage(baseDmg, player);
            Vector away = e.getLocation().toVector().subtract(eye.toVector()).normalize();
            away.setY(0.4);
            e.setVelocity(e.getVelocity().add(away.multiply(1.2)));
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 0.8f);
    }

    private void handleTeleport(Player player, WeaponConfig cfg, long now) {
        int manaCost = getInt(cfg.getStats(), "mana_cost", 50);
        int blocks = getInt(cfg.getStats(), "teleport_blocks", 8);
        int speedBonus = getInt(cfg.getStats(), "speed_bonus", 50);
        double durationSec = getDouble(cfg.getStats(), "speed_duration_seconds", 3);
        double cooldownSec = getDouble(cfg.getStats(), "cooldown_seconds", 1);
        long cooldownMs = (long) (cooldownSec * 1000 * plugin.getCooldownMultiplier(player));
        long until = teleportCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        if (now < until) {
            long left = (until - now) / 1000;
            player.sendActionBar(Component.text("技能冷卻中，剩餘 " + (left + 1) + " 秒", NamedTextColor.GRAY));
            return;
        }
        double currentMana = plugin.getPlayerStatsManager().get(player).getCurrentIntelligence();
        if (currentMana < manaCost) {
            player.sendActionBar(Component.text("智力不足！需要 " + manaCost + "，目前 " + (int) currentMana, NamedTextColor.RED));
            return;
        }
        PlayerStatsData data = plugin.getPlayerStatsManager().get(player);
        data.setCurrentIntelligence(Math.max(0, data.getCurrentIntelligence() - manaCost));
        teleportCooldownUntil.put(player.getUniqueId(), now + cooldownMs);
        Location from = player.getLocation();
        Vector dir = from.getDirection().multiply(blocks);
        Location to = from.clone().add(dir.getX(), dir.getY(), dir.getZ());
        if (to.getBlockY() < from.getWorld().getMinHeight()) to.setY(from.getWorld().getMinHeight());
        if (to.getBlockY() >= from.getWorld().getMaxHeight()) to.setY(from.getWorld().getMaxHeight() - 1);
        player.teleport(to);
        int durationTicks = (int) (durationSec * 20);
        int level = Math.min(4, Math.max(0, speedBonus / 25));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, level, false, true));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1f);
    }

    private static int getInt(Map<String, String> stats, String key, int def) {
        if (stats == null) return def;
        String v = stats.get(key);
        if (v == null) return def;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static double getDouble(Map<String, String> stats, String key, double def) {
        if (stats == null) return def;
        String v = stats.get(key);
        if (v == null) return def;
        try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    /** 取得玩家視線上的第一個活體目標（排除自己），用於技能施放。 */
    private LivingEntity getTargetEntity(Player player, double maxDistance) {
        Vector dir = player.getEyeLocation().getDirection();
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                dir,
                maxDistance,
                1.0
        );
        if (result == null) return null;
        Entity hit = result.getHitEntity();
        if (hit == null || hit.equals(player) || !(hit instanceof LivingEntity le) || le.isDead()) return null;
        return le;
    }
}
