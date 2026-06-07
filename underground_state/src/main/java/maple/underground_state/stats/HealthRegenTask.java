package maple.underground_state.stats;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class HealthRegenTask extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final PlayerStatsRuntime runtime;

    public HealthRegenTask(JavaPlugin plugin, PlayerStatsRuntime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
    }

    @Override
    public void run() {
        if (!plugin.isEnabled()) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;
            if (player.isDead()) continue;
            if (player.getGameMode() == GameMode.SPECTATOR) continue;

            PlayerStatsData data = runtime.getManager().get(player);
            if (runtime.shouldSkipHealthRegen(player, data)) continue;

            double maxHealth = runtime.getEffectiveMaxHealth(player);
            double healthCap = runtime.getEffectiveHealthCap(player, maxHealth);
            if (data.getCurrentHealth() >= healthCap) continue;

            double regenStat = runtime.getEffectiveHealthRegen(player);
            double gain = PlayerStatsFormulas.healthRegenPerTick(maxHealth, regenStat);
            if (gain <= 0D) continue;

            runtime.setCurrentHealth(player, data.getCurrentHealth() + gain, maxHealth, healthCap, true);
        }
    }
}
