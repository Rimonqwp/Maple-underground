package maple.underground_caves.party;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public final class PartyConnectionListener implements Listener {
    private static final long LEADER_DISCONNECT_GRACE_TICKS = 5L * 60L * 20L;

    private final Plugin plugin;
    private final PartyManager partyManager;

    public PartyConnectionListener(Plugin plugin, PartyManager partyManager) {
        this.plugin = plugin;
        this.partyManager = partyManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        partyManager.markOnline(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        partyManager.markDisconnected(playerId);
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                partyManager.disbandIfLeaderStillDisconnected(playerId), LEADER_DISCONNECT_GRACE_TICKS);
    }
}
