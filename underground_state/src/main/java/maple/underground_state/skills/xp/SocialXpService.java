package maple.underground_state.skills.xp;

import maple.underground_state.UndergroundState;
import maple.underground_state.skills.SkillProgressManager;
import maple.underground_state.skills.SkillRewardService;
import maple.underground_state.skills.SkillType;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SocialXpService implements Listener {

    private static final double RANGE = 15.0D;
    private static final int REQUIRED_SECONDS = 300;

    private final SkillProgressManager skillProgressManager;
    private final SkillRewardService skillRewardService;
    private final CombatXpOverlayService xpOverlayService;
    private final Map<UUID, SocialProgress> progressByPlayer = new ConcurrentHashMap<>();
    private final BukkitTask tickerTask;

    public SocialXpService(@NotNull UndergroundState plugin,
                           @NotNull SkillProgressManager skillProgressManager,
                           @NotNull SkillRewardService skillRewardService,
                           @NotNull CombatXpOverlayService xpOverlayService) {
        this.skillProgressManager = skillProgressManager;
        this.skillRewardService = skillRewardService;
        this.xpOverlayService = xpOverlayService;
        this.tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        progressByPlayer.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        tickerTask.cancel();
        progressByPlayer.clear();
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!canGainXp(player)) {
                progressByPlayer.remove(player.getUniqueId());
                continue;
            }

            int nearbyCount = countNearbyPlayers(player);
            if (nearbyCount <= 0) {
                progressByPlayer.remove(player.getUniqueId());
                continue;
            }

            SocialProgress progress = progressByPlayer.get(player.getUniqueId());
            int seconds = progress == null ? 1 : progress.secondsQualified() + 1;
            if (seconds < REQUIRED_SECONDS) {
                progressByPlayer.put(player.getUniqueId(), new SocialProgress(seconds));
                continue;
            }

            int awardWholeXp = nearbyCount;
            skillRewardService.addSkillXp(player.getUniqueId(), SkillType.SOCIAL, awardWholeXp);
            long totalXp = skillProgressManager.getXp(player.getUniqueId(), SkillType.SOCIAL);
            xpOverlayService.showSkillOverlay(player, SkillType.SOCIAL, awardWholeXp * 10, totalXp * 10L);
            progressByPlayer.put(player.getUniqueId(), new SocialProgress(0));
        }
    }

    private static int countNearbyPlayers(@NotNull Player player) {
        int count = 0;
        for (Player other : player.getWorld().getPlayers()) {
            if (other.equals(player) || !canGainXp(other)) {
                continue;
            }
            if (other.getLocation().distance(player.getLocation()) <= RANGE) {
                count++;
            }
        }
        return count;
    }

    private static boolean canGainXp(@NotNull Player player) {
        return player.isOnline()
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR;
    }

    private record SocialProgress(int secondsQualified) {
    }
}
