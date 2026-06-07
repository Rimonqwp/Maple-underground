package org.maple.underground_velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.maple.underground_velocity.command.DungeonProxyCommand;
import org.maple.underground_velocity.command.PartyProxyCommand;
import org.maple.underground_velocity.config.BridgeConfig;
import org.maple.underground_velocity.dungeon.DungeonStartCoordinator;
import org.maple.underground_velocity.party.PartyService;
import org.slf4j.Logger;

import java.nio.file.Path;

public final class UndergroundVelocity {
    public static final MinecraftChannelIdentifier DUNGEON_BRIDGE_CHANNEL =
            MinecraftChannelIdentifier.create("maple", "dungeon_bridge");

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private BridgeConfig config;
    private PartyService partyService;
    private DungeonStartCoordinator dungeonStartCoordinator;

    @Inject
    public UndergroundVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.config = BridgeConfig.load(dataDirectory, logger);
        this.partyService = new PartyService(server, config.maxPartySize(), config.inviteTimeoutSeconds());
        this.dungeonStartCoordinator = new DungeonStartCoordinator(server, this, logger, config, partyService, DUNGEON_BRIDGE_CHANNEL);

        server.getChannelRegistrar().register(DUNGEON_BRIDGE_CHANNEL);
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("p").aliases("party").plugin(this).build(),
                new PartyProxyCommand(server, partyService)
        );
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("dungeon").plugin(this).build(),
                new DungeonProxyCommand(dungeonStartCoordinator)
        );

        logger.info("Maple dungeon bridge enabled. dungeon-server={}", config.dungeonServer());
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!DUNGEON_BRIDGE_CHANNEL.equals(event.getIdentifier())) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (event.getSource() instanceof ServerConnection source) {
            dungeonStartCoordinator.handlePluginMessage(source, event.getData());
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        partyService.removePlayer(event.getPlayer().getUniqueId());
        dungeonStartCoordinator.handleDisconnect(event.getPlayer().getUniqueId());
    }
}
