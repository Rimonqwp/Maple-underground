package maple.underground_state.stats;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerStatsListener implements Listener {

    private final JavaPlugin plugin;
    private final PlayerStatsManager manager;

    public PlayerStatsListener(JavaPlugin plugin, PlayerStatsManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        manager.onJoin(uuid);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            var loaded = manager.loadOrCreateSnapshot(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> manager.completeInitialLoad(uuid, loaded));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        manager.onQuit(event.getPlayer().getUniqueId());
    }
}
