package maple.underground_island;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

final class IslandVelocityBridge {
    static final String CHANNEL = "maple:island_bridge";
    private static final String ACTION_CONNECT_SERVER = "CONNECT_SERVER";

    private final JavaPlugin plugin;
    private final String targetServer;
    private final String bridgeToken;

    IslandVelocityBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        this.targetServer = plugin.getConfig().getString("main-city-server", "server1");
        this.bridgeToken = plugin.getConfig().getString("bridge-token", "");
    }

    void register() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    void unregister() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
    }

    void sendToMainCity(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF(ACTION_CONNECT_SERVER);
            out.writeUTF(bridgeToken != null ? bridgeToken : "");
            out.writeUTF(player.getUniqueId().toString());
            out.writeUTF(targetServer != null && !targetServer.isBlank() ? targetServer : "server1");
            out.flush();
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("[Island] Failed to send Velocity bridge message: " + e.getMessage());
        }
    }
}
