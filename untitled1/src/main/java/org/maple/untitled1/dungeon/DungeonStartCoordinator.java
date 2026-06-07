package org.maple.untitled1.dungeon;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.maple.untitled1.bridge.BridgeProtocol;
import org.maple.untitled1.config.BridgeConfig;
import org.maple.untitled1.party.PartyMember;
import org.maple.untitled1.party.PartyService;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class DungeonStartCoordinator {
    private final ProxyServer server;
    private final Object plugin;
    private final Logger logger;
    private final BridgeConfig config;
    private final PartyService partyService;
    private final ChannelIdentifier channel;
    private final Map<UUID, PendingRun> pendingRuns = new ConcurrentHashMap<>();

    public DungeonStartCoordinator(
            ProxyServer server,
            Object plugin,
            Logger logger,
            BridgeConfig config,
            PartyService partyService,
            ChannelIdentifier channel
    ) {
        this.server = server;
        this.plugin = plugin;
        this.logger = logger;
        this.config = config;
        this.partyService = partyService;
        this.channel = channel;
    }

    public void startDungeon(Player leader, String rawFloor) {
        String floor = normalizeFloor(rawFloor);
        if (floor == null) {
            leader.sendPlainMessage("楼层无效，请使用 F1、F2 或 F3。");
            return;
        }
        if (!partyService.isLeaderOrSolo(leader.getUniqueId())) {
            leader.sendPlainMessage("只有队长可以开始副本。");
            return;
        }

        List<UUID> missing = partyService.onlineMissingMembers(leader.getUniqueId());
        if (!missing.isEmpty()) {
            leader.sendPlainMessage("队伍中有人不在线，无法开始副本。");
            return;
        }

        List<PartyMember> members = partyService.getOnlinePartyMembers(leader.getUniqueId());
        if (members.isEmpty()) {
            leader.sendPlainMessage("无法取得队伍成员。");
            return;
        }

        Optional<RegisteredServer> dungeonServer = server.getServer(config.dungeonServer());
        if (dungeonServer.isEmpty()) {
            leader.sendPlainMessage("找不到副本服务器：" + config.dungeonServer());
            return;
        }

        UUID runId = UUID.randomUUID();
        Map<UUID, RegisteredServer> originalServers = new LinkedHashMap<>();
        for (PartyMember member : members) {
            server.getPlayer(member.uuid()).flatMap(Player::getCurrentServer)
                    .map(ServerConnection::getServer)
                    .ifPresent(current -> originalServers.put(member.uuid(), current));
        }

        PendingRun pendingRun = new PendingRun(runId, leader.getUniqueId(), floor, members, originalServers, dungeonServer.get());
        pendingRuns.put(runId, pendingRun);
        partyService.broadcast(memberIds(members), "正在传送队伍到副本服务器...");

        List<CompletableFuture<Boolean>> connects = new ArrayList<>();
        for (PartyMember member : members) {
            Optional<Player> player = server.getPlayer(member.uuid());
            if (player.isEmpty()) {
                connects.add(CompletableFuture.completedFuture(false));
                continue;
            }
            CompletableFuture<Boolean> connected = player.get().createConnectionRequest(dungeonServer.get())
                    .connect()
                    .thenApply(result -> result.getStatus() == ConnectionRequestBuilder.Status.SUCCESS
                            || result.getStatus() == ConnectionRequestBuilder.Status.ALREADY_CONNECTED)
                    .exceptionally(error -> false);
            connects.add(connected);
        }

        CompletableFuture.allOf(connects.toArray(CompletableFuture[]::new)).thenRun(() -> {
            boolean ok = connects.stream().allMatch(future -> future.getNow(false));
            if (!ok) {
                failRun(runId, "有队员传送到副本服失败，已取消开局。", true);
                return;
            }
            sendStartMessage(pendingRun);
        });

        server.getScheduler().buildTask(plugin, () -> {
            PendingRun stillPending = pendingRuns.get(runId);
            if (stillPending != null) {
                failRun(runId, "副本服务器响应超时，已取消开局。", true);
            }
        }).delay(config.startTimeoutSeconds(), TimeUnit.SECONDS).schedule();
    }

    public void handlePluginMessage(ServerConnection source, byte[] data) {
        if (source == null || data == null) {
            return;
        }
        try {
            BridgeProtocol.AckMessage ack = BridgeProtocol.decodeAck(data);
            PendingRun run = pendingRuns.remove(ack.runId());
            if (run == null) {
                return;
            }
            List<UUID> members = memberIds(run.members());
            if (ack.success()) {
                partyService.broadcast(members, ack.message().isBlank() ? "副本服务器已开始生成地牢。" : ack.message());
            } else {
                failRun(run, ack.message().isBlank() ? "副本服务器拒绝了开局请求。" : ack.message(), true);
            }
        } catch (IOException | IllegalArgumentException e) {
            logger.warn("Failed to read dungeon bridge ACK.", e);
        }
    }

    public void handleDisconnect(UUID playerId) {
        for (PendingRun run : List.copyOf(pendingRuns.values())) {
            boolean related = run.members().stream().anyMatch(member -> member.uuid().equals(playerId));
            if (related) {
                failRun(run.runId(), "有队员断线，已取消副本开局。", false);
            }
        }
    }

    private void sendStartMessage(PendingRun run) {
        byte[] payload = BridgeProtocol.encodeStartRun(
                run.runId(),
                config.bridgeToken(),
                run.floor(),
                run.leaderId(),
                run.members()
        );
        boolean sent = run.dungeonServer().sendPluginMessage(channel, payload);
        if (!sent) {
            failRun(run.runId(), "无法发送开局请求到副本服务器。", true);
        }
    }

    private void failRun(UUID runId, String message, boolean returnPlayers) {
        PendingRun run = pendingRuns.remove(runId);
        if (run != null) {
            failRun(run, message, returnPlayers);
        }
    }

    private void failRun(PendingRun run, String message, boolean returnPlayers) {
        List<UUID> members = memberIds(run.members());
        partyService.broadcast(members, message);
        if (!returnPlayers) {
            return;
        }
        for (PartyMember member : run.members()) {
            Optional<Player> player = server.getPlayer(member.uuid());
            RegisteredServer original = run.originalServers().get(member.uuid());
            if (player.isPresent() && original != null) {
                player.get().createConnectionRequest(original).fireAndForget();
            }
        }
    }

    private static String normalizeFloor(String raw) {
        if (raw == null) {
            return null;
        }
        String floor = raw.trim().toUpperCase(Locale.ROOT);
        return switch (floor) {
            case "F1", "F2", "F3" -> floor;
            default -> null;
        };
    }

    private static List<UUID> memberIds(List<PartyMember> members) {
        return members.stream().map(PartyMember::uuid).toList();
    }

    private record PendingRun(
            UUID runId,
            UUID leaderId,
            String floor,
            List<PartyMember> members,
            Map<UUID, RegisteredServer> originalServers,
            RegisteredServer dungeonServer
    ) {
    }
}
