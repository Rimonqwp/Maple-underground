package org.maple.underground_velocity.island;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.maple.underground_velocity.config.BridgeConfig;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public final class IslandBridgeHandler {
    private static final String ACTION_CONNECT_SERVER = "CONNECT_SERVER";

    private final ProxyServer server;
    private final Logger logger;
    private final BridgeConfig config;

    public IslandBridgeHandler(ProxyServer server, Logger logger, BridgeConfig config) {
        this.server = server;
        this.logger = logger;
        this.config = config;
    }

    public void handlePluginMessage(ServerConnection source, byte[] data) {
        if (source == null || data == null) {
            return;
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            String action = in.readUTF();
            if (!ACTION_CONNECT_SERVER.equals(action)) {
                logger.warn("Unknown island bridge action: {}", action);
                return;
            }
            String token = in.readUTF();
            UUID playerId = UUID.fromString(in.readUTF());
            String requestedServer = in.available() > 0 ? in.readUTF() : "";
            if (!config.bridgeToken().isBlank() && !config.bridgeToken().equals(token)) {
                logger.warn("Rejected island bridge message because bridge-token did not match.");
                return;
            }

            Player player = source.getPlayer();
            if (!player.getUniqueId().equals(playerId)) {
                logger.warn("Rejected island bridge message for mismatched player. source={} payload={}",
                        player.getUniqueId(), playerId);
                return;
            }

            String targetName = config.mainCityServer().isBlank()
                    ? (requestedServer == null || requestedServer.isBlank() ? "server1" : requestedServer)
                    : config.mainCityServer();
            Optional<RegisteredServer> target = server.getServer(targetName);
            if (target.isEmpty()) {
                player.sendPlainMessage("找不到主城服务器：" + targetName);
                logger.warn("Island bridge target server is not registered: {}", targetName);
                return;
            }

            player.createConnectionRequest(target.get()).connect().thenAccept(result -> {
                ConnectionRequestBuilder.Status status = result.getStatus();
                if (status != ConnectionRequestBuilder.Status.SUCCESS
                        && status != ConnectionRequestBuilder.Status.ALREADY_CONNECTED) {
                    player.sendPlainMessage("传送到主城失败，请稍后再试。");
                    logger.warn("Failed to connect {} to main city {}: {}", player.getUsername(), targetName, status);
                }
            }).exceptionally(error -> {
                player.sendPlainMessage("传送到主城失败，请稍后再试。");
                logger.warn("Failed to connect {} to main city {}", player.getUsername(), targetName, error);
                return null;
            });
        } catch (IOException | IllegalArgumentException e) {
            logger.warn("Failed to read island bridge message.", e);
        }
    }
}
