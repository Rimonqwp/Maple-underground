package maple.underground_caves.dungeon.classskill;

import maple.underground_caves.Test_dungeons;
import maple.underground_state.dungeon.panel.DungeonClassSkills;
import maple.underground_caves.party.PartyManager;
import maple.underground_state.stats.PlayerStatsData;
import maple.underground_state.stats.PlayerStatsFormulas;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class HealerClassSkillListener extends AbstractDungeonClassSkillListener implements Listener {
    private final Map<UUID, Long> healerWishCooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> healerCircleCooldownUntil = new ConcurrentHashMap<>();
    private static final class HealerCircle { final UUID healerId; final long expiryMs; HealerCircle(UUID id, long ms){this.healerId=id;this.expiryMs=ms;} }
    private final Map<String, HealerCircle> healerCircleExpiry = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UUID> healerResonanceBoundTarget = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> healerInspireBuffUntil = new ConcurrentHashMap<>();

    public HealerClassSkillListener(Test_dungeons plugin) {
        super(plugin);
        startHealerAuraTask();
        startHealerCircleTask();
        startHealerCircleParticleTask();
        startHealerReviveTask();
    }

    private boolean isOnlyHealerSelectedInParty(Player healer) {
        PartyManager pm = plugin.getPartyManager();
        if (pm == null) return true;
        List<UUID> members = pm.getPartyMembers(healer);
        if (members == null || members.isEmpty()) return true;
        int healerCount = 0;
        for (UUID id : members) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline() || !p.getWorld().equals(healer.getWorld())) continue;
            if (PartyManager.ROLE_HEALER.equals(pm.getRole(p))) healerCount++;
        }
        return healerCount <= 1;
    }

    private void healPercentFrom(Player healerOrNull, Player target, double percentOfMax, boolean activeSkill) {
        if (plugin.getPlayerStatsManager() == null) return;
        double maxH = plugin.getEffectiveMaxHealth(target);
        double raw = maxH * (percentOfMax / 100.0);
        double recvVit = plugin.getEffectiveVitalityForSelfHeal(target);
        double heal;
        // Hypixel：他人奶你時，施治者 Mending 加在受治療者 Vitality 上
        if (healerOrNull != null && !healerOrNull.getUniqueId().equals(target.getUniqueId())) {
            double hMend = plugin.getEffectiveOutgoingMending(healerOrNull);
            heal = PlayerStatsFormulas.totalHealthGainedFromHealer(raw, recvVit, hMend);
        } else {
            heal = PlayerStatsFormulas.totalHealthGained(raw, recvVit);
        }
        if (activeSkill && healerOrNull != null) {
            UUID bound = healerResonanceBoundTarget.get(healerOrNull.getUniqueId());
            if (bound != null && bound.equals(target.getUniqueId())) heal *= 1.5;
            PlayerStatsData tData = plugin.getPlayerStatsManager().get(target);
            if (maxH > 0 && (tData.getCurrentHealth() / maxH) < 0.25) healerInspireBuffUntil.put(healerOrNull.getUniqueId(), System.currentTimeMillis() + 5000L);
        }
        if (plugin.getPlayerStatsRuntime() != null) {
            plugin.getPlayerStatsRuntime().heal(target, heal);
        } else {
            PlayerStatsData data = plugin.getPlayerStatsManager().get(target);
            double cap = maxH + plugin.getWishShieldExtra(target.getUniqueId());
            data.setCurrentHealthWithCap(Math.min(data.getCurrentHealth() + heal, cap), cap);
            plugin.syncVirtualHealthBar(target);
        }
    }

    private void startHealerAuraTask() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!inDungeon(p) || !dungeonStarted(p) || classIndex(p) != 0 || isDungeonSoulMode(p)) continue;
                    int lvl = Math.max(1, classLevel(p));
                    double radius = DungeonClassSkills.healerAuraRadius(lvl);
                    double percent = DungeonClassSkills.healerAuraHealPctPerSec(lvl);
                    if (isOnlyHealerSelectedInParty(p)) percent *= 2.0;
                    for (Player other : p.getWorld().getPlayers()) {
                        if (other.getUniqueId().equals(p.getUniqueId()) || other.getGameMode() == GameMode.SPECTATOR) continue;
                        if (p.getLocation().distance(other.getLocation()) <= radius) healPercentFrom(p, other, percent, false);
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 20L);
    }

    private void startHealerCircleTask() {
        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                for (Iterator<Map.Entry<String, HealerCircle>> it = healerCircleExpiry.entrySet().iterator(); it.hasNext();) {
                    var e = it.next();
                    HealerCircle c = e.getValue();
                    if (c == null || c.expiryMs <= now) { it.remove(); continue; }
                    String[] parts = e.getKey().split(":");
                    if (parts.length != 4) continue;
                    World w = Bukkit.getWorld(parts[0]);
                    if (w == null) continue;
                    Player healer = c.healerId != null ? Bukkit.getPlayer(c.healerId) : null;
                    // 靈魂模式：治療圈視為完全失效，立即移除（不可放置見 onHealerSneakForCircle）
                    if (healer != null && isDungeonSoulMode(healer)) {
                        it.remove();
                        continue;
                    }
                    int lvl = healer != null && healer.isOnline() ? Math.max(1, classLevel(healer)) : 1;
                    Location center = new Location(w, Double.parseDouble(parts[1]) + 0.5, Double.parseDouble(parts[2]) + 0.5, Double.parseDouble(parts[3]) + 0.5);
                    double radius = DungeonClassSkills.healerCircleRadius(lvl);
                    double percent = DungeonClassSkills.healerCircleHealPctPerSec(lvl);
                    for (Player other : w.getPlayers()) if (other.getGameMode() != GameMode.SPECTATOR && center.distance(other.getLocation()) <= radius) healPercentFrom(healer, other, percent, true);
                }
            }
        }.runTaskTimer(plugin, 30L, 20L);
    }

    /**
     * 治療圈視覺：治療邏輯本身不會產生粒子，另開較高頻任務僅繪製圓環（force 略過客戶端「減少粒子」時仍盡量顯示）。
     */
    private void startHealerCircleParticleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Map.Entry<String, HealerCircle> e : healerCircleExpiry.entrySet()) {
                    HealerCircle c = e.getValue();
                    if (c == null || c.expiryMs <= now) continue;
                    String[] parts = e.getKey().split(":");
                    if (parts.length != 4) continue;
                    World w = Bukkit.getWorld(parts[0]);
                    if (w == null) continue;
                    Player healer = c.healerId != null ? Bukkit.getPlayer(c.healerId) : null;
                    if (healer != null && isDungeonSoulMode(healer)) continue;
                    int lvl = healer != null && healer.isOnline() ? Math.max(1, classLevel(healer)) : 1;
                    Location center = new Location(w,
                            Double.parseDouble(parts[1]) + 0.5,
                            Double.parseDouble(parts[2]) + 0.5,
                            Double.parseDouble(parts[3]) + 0.5);
                    double radius = DungeonClassSkills.healerCircleRadius(lvl);
                    spawnHealerCircleParticles(w, center, radius);
                }
            }
        }.runTaskTimer(plugin, 25L, 5L);
    }

    private static void spawnHealerCircleParticles(World w, Location center, double radius) {
        if (w == null || center == null || radius <= 0) return;
        Particle.DustOptions rim = new Particle.DustOptions(Color.fromRGB(160, 255, 190), 1.0f);
        Particle.DustOptions core = new Particle.DustOptions(Color.fromRGB(80, 220, 120), 0.75f);
        int n = Math.min(72, Math.max(28, (int) (radius * 10)));
        double y = center.getY() + 0.08;
        for (int i = 0; i < n; i++) {
            double a = (i / (double) n) * Math.PI * 2.0;
            double x = center.getX() + radius * Math.cos(a);
            double z = center.getZ() + radius * Math.sin(a);
            w.spawnParticle(Particle.DUST, x, y, z, 1, 0.03, 0.04, 0.03, 0, rim, true);
        }
        w.spawnParticle(Particle.DUST, center.getX(), y + 0.05, center.getZ(), 8,
                radius * 0.25, 0.12, radius * 0.25, 0, core, true);
    }

    private void startHealerReviveTask() {
        new BukkitRunnable() {
            @Override public void run() {
                var death = plugin.getDungeonDeathListener();
                if (death == null) return;
                long now = System.currentTimeMillis();
                for (Player healer : Bukkit.getOnlinePlayers()) {
                    if (!inDungeon(healer) || !dungeonStarted(healer) || classIndex(healer) != 0) continue;
                    String world = healer.getWorld().getName();
                    if (now < death.getReviveCooldownUntil(world)) continue;
                    UUID next = death.pollNextGhostPreferHealer(world);
                    if (next == null) {
                        continue;
                    }
                    death.resurrectGhostNow(next, world, healer);
                    death.setReviveCooldownUntil(world, now + 50_000L);
                    break;
                }
            }
        }.runTaskTimer(plugin, 40L, 20L);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHealerBindResonance(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) return;
        Player healer = event.getPlayer();
        if (!inDungeon(healer) || !dungeonStarted(healer) || classIndex(healer) != 0 || isDungeonSoulMode(healer) || !target.getWorld().equals(healer.getWorld())) return;
        healerResonanceBoundTarget.put(healer.getUniqueId(), target.getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHealerSneakForCircle(PlayerToggleSneakEvent event) {
        Player healer = event.getPlayer();
        if (!event.isSneaking()) return;
        if (!inDungeon(healer) || !dungeonStarted(healer) || classIndex(healer) != 0 || isDungeonSoulMode(healer)) return;
        long now = System.currentTimeMillis();
        long until = healerCircleCooldownUntil.getOrDefault(healer.getUniqueId(), 0L);
        if (now < until) return;
        int lvl = Math.max(1, classLevel(healer));
        healerCircleCooldownUntil.put(healer.getUniqueId(), now + 2000L);
        Location loc = healer.getLocation();
        String key = healer.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
        long durMs = (long) (DungeonClassSkills.healerCircleDurationSec(lvl) * 1000L);
        healerCircleExpiry.put(key, new HealerCircle(healer.getUniqueId(), now + durMs));
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!inDungeon(player) || !dungeonStarted(player) || classIndex(player) != 0 || isDungeonSoulMode(player)) return;
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        long until = healerWishCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        if (now < until) return;
        healerWishCooldownUntil.put(player.getUniqueId(), now + 120_000L);
        double shieldPercent = DungeonClassSkills.healerWishShieldPct(Math.max(1, classLevel(player)));
        if (plugin.getPlayerStatsManager() == null) return;
        for (Player p : player.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            double maxH = plugin.getEffectiveMaxHealth(p);
            double shieldExtra = maxH * (shieldPercent / 100.0);
            plugin.setWishShieldExtra(p.getUniqueId(), shieldExtra);
            if (plugin.getPlayerStatsRuntime() != null) {
                plugin.getPlayerStatsRuntime().restoreFullHealth(p);
            } else {
                double cap = maxH + shieldExtra;
                plugin.getPlayerStatsManager().get(p).setCurrentHealthWithCap(cap, cap);
                plugin.syncVirtualHealthBar(p);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHealerDamageBuff(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!inDungeon(damager) || classIndex(damager) != 0 || isDungeonSoulMode(damager)) return;
        long until = healerInspireBuffUntil.getOrDefault(damager.getUniqueId(), 0L);
        if (System.currentTimeMillis() < until) event.setDamage(event.getDamage() * 1.015);
    }
}
