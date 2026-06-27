package maple.underground_island;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

final class IslandPlayerListener implements Listener {
    private final JavaPlugin plugin;
    private final IslandService islandService;

    IslandPlayerListener(JavaPlugin plugin, IslandService islandService) {
        this.plugin = plugin;
        this.islandService = islandService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                islandService.prepareOnJoin(event.getPlayer());
            }
        }, 10L);
    }
}
