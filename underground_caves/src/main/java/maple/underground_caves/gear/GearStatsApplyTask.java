package maple.underground_caves.gear;

import maple.underground_state.stats.PlayerStatsManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 週期性套用行走速度：總 Speed = 玩家 Speed 屬性 + 堅壁套裝加減，walkSpeed = 0.2 × (總 Speed / 100)，上限 500。
 */
public final class GearStatsApplyTask extends BukkitRunnable {

    private static final float DEFAULT_WALK_SPEED = 0.2f;
    private static final int HYPIXEL_BASE_SPEED = 100;
    private static final int HYPIXEL_SPEED_CAP = 500;

    private final JavaPlugin plugin;
    private final GearManager gearManager;
    private final PlayerStatsManager playerStatsManager;

    public GearStatsApplyTask(JavaPlugin plugin, GearManager gearManager, PlayerStatsManager playerStatsManager) {
        this.plugin = plugin;
        this.gearManager = gearManager;
        this.playerStatsManager = playerStatsManager;
    }

    @Override
    public void run() {
        if (!plugin.isEnabled()) return;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p == null || !p.isOnline()) continue;
            double baseSpeed = playerStatsManager != null ? playerStatsManager.get(p.getUniqueId()).getSpeed() : HYPIXEL_BASE_SPEED;
            int gearMod = gearManager.getSpeedModifierFromGear(p);
            if (plugin instanceof maple.underground_caves.Test_dungeons td) {
                if (td.getSecondWindHelmetManager() != null) gearMod += td.getSecondWindHelmetManager().getBonusSpeed(p);
                if (td.getCavernExplorerSetManager() != null) gearMod += td.getCavernExplorerSetManager().getBonusSpeed(p);
                if (td.getBloodSetManager() != null) gearMod += td.getBloodSetManager().getBonusSpeed(p);
            }
            int total = (int) Math.round(baseSpeed) + gearMod;
            int clamped = Math.max(0, Math.min(HYPIXEL_SPEED_CAP, total));
            float walkSpeed = DEFAULT_WALK_SPEED * clamped / HYPIXEL_BASE_SPEED;
            p.setWalkSpeed((float) Math.max(0.0, Math.min(1.0, walkSpeed)));
            gearManager.refreshSetProgressLore(p);
        }
    }
}
