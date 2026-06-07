package maple.underground_state.skills.xp;

import maple.underground_state.skills.SkillProgressManager;
import maple.underground_state.skills.SkillRewardService;
import maple.underground_state.skills.SkillType;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class ExplorationXpService implements Listener {

    private static final int MILLIS_PER_BLOCK = 1_000;
    private static final int MILLIS_PER_XP = 100 * MILLIS_PER_BLOCK;
    private static final double MAX_COUNTED_STEP = 10.0D;

    private final SkillProgressManager skillProgressManager;
    private final SkillRewardService skillRewardService;
    private final CombatXpOverlayService xpOverlayService;

    public ExplorationXpService(@NotNull SkillProgressManager skillProgressManager,
                                @NotNull SkillRewardService skillRewardService,
                                @NotNull CombatXpOverlayService xpOverlayService) {
        this.skillProgressManager = skillProgressManager;
        this.skillRewardService = skillRewardService;
        this.xpOverlayService = xpOverlayService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) {
            return;
        }

        Player player = event.getPlayer();
        if (!canGainXp(player) || event.getTo() == null) {
            return;
        }
        if (event.getFrom().getWorld() != event.getTo().getWorld()) {
            return;
        }

        double moved = event.getFrom().distance(event.getTo());
        if (moved <= 0.0D || moved > MAX_COUNTED_STEP) {
            return;
        }

        UUID playerId = player.getUniqueId();
        int movedMillis = (int) Math.round(moved * MILLIS_PER_BLOCK);
        int pendingMillis = skillProgressManager.getExplorationDistanceRemainderMillis(playerId) + movedMillis;
        int awardWholeXp = pendingMillis / MILLIS_PER_XP;
        skillProgressManager.setExplorationDistanceRemainderMillis(playerId, pendingMillis % MILLIS_PER_XP);

        if (awardWholeXp <= 0) {
            return;
        }

        skillRewardService.addSkillXp(playerId, SkillType.EXPLORATION, awardWholeXp);
        long totalXp = skillProgressManager.getXp(playerId, SkillType.EXPLORATION);
        xpOverlayService.showSkillOverlay(player, SkillType.EXPLORATION, awardWholeXp * 10, totalXp * 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Progress remainder is persisted via SkillProgressManager/DB.
    }

    private static boolean canGainXp(@NotNull Player player) {
        return player.isOnline()
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR;
    }
}
