package org.maple.underground_velocity.dungeon;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.maple.underground_velocity.bridge.BridgeProtocol;
import org.maple.underground_velocity.config.BridgeConfig;
import org.maple.underground_velocity.party.PartyMember;
import org.maple.underground_velocity.party.PartyService;
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
    private final DungeonInstanceManager instanceManager;
    private final Map<UUID, PendingRun> pendingRuns = new ConcurrentHashMap<>();
    private final List<QueuedRun> queuedRuns = new ArrayList<>();
    private volatile boolean queueProcessing;

    public DungeonStartCoordinator(
            ProxyServer server,
            Object plugin,
            Logger logger,
            BridgeConfig config,
            PartyService partyService,
            ChannelIdentifier channel,
            DungeonInstanceManager instanceManager
    ) {
        this.server = server;
        this.plugin = plugin;
        this.logger = logger;
        this.config = config;
        this.partyService = partyService;
        this.channel = channel;
        this.instanceManager = instanceManager;
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
        if (hasPendingOrQueuedRun(members)) {
            leader.sendPlainMessage("你的队伍已经有副本开局或候补正在进行。");
            return;
        }

        UUID runId = UUID.randomUUID();
        Map<UUID, RegisteredServer> originalServers = currentServersOf(members);
        if (config.dynamicInstancesEnabled()) {
            requestDynamicRun(new QueuedRun(runId, leader.getUniqueId(), floor, members, originalServers, System.currentTimeMillis()), false);
            return;
        }

        Optional<RegisteredServer> dungeonServer = server.getServer(config.dungeonServer());
        if (dungeonServer.isEmpty()) {
            leader.sendPlainMessage("找不到副本服务器：" + config.dungeonServer());
            return;
        }
        PendingRun run = new PendingRun(
                runId,
                leader.getUniqueId(),
                floor,
                members,
                originalServers,
                dungeonServer.get(),
                false,
                dungeonServer.get().getServerInfo().getName(),
                "",
                ""
        );
        pendingRuns.put(runId, run);
        connectPartyAndStart(run);
        scheduleAckTimeout(runId);
    }

    public void leaveDungeon(Player player) {
        if (!config.dynamicInstancesEnabled()) {
            player.sendPlainMessage("当前没有启用动态副本服务器模式。");
            return;
        }
        instanceManager.leave(player);
    }

    public void handlePluginMessage(ServerConnection source, byte[] data) {
        if (source == null || data == null) {
            return;
        }
        try {
            BridgeProtocol.Message message = BridgeProtocol.decode(data);
            if (message instanceof BridgeProtocol.AckMessage ack) {
                handleAck(ack);
                return;
            }
            if (message instanceof BridgeProtocol.EndRunMessage endRun) {
                handleEndRun(endRun);
            }
        } catch (IOException | IllegalArgumentException e) {
            logger.warn("Failed to read dungeon bridge message.", e);
        }
    }

    public void handleDisconnect(UUID playerId) {
        for (PendingRun run : List.copyOf(pendingRuns.values())) {
            boolean related = run.members().stream().anyMatch(member -> member.uuid().equals(playerId));
            if (related) {
                failRun(run.runId(), "有队员断线，已取消副本开局。", false);
            }
        }
        List<QueuedRun> removed = new ArrayList<>();
        synchronized (queuedRuns) {
            queuedRuns.removeIf(run -> {
                boolean related = run.members().stream().anyMatch(member -> member.uuid().equals(playerId));
                if (related) {
                    removed.add(run);
                }
                return related;
            });
        }
        for (QueuedRun run : removed) {
            partyService.broadcast(memberIds(run.members()), "有队员断线，已取消副本候补。");
        }
    }

    public String describeQueue() {
        synchronized (queuedRuns) {
            if (queuedRuns.isEmpty()) {
                return "候补队列：空";
            }
            StringBuilder out = new StringBuilder("候补队列：").append(queuedRuns.size()).append(" 组");
            for (int i = 0; i < queuedRuns.size(); i++) {
                QueuedRun run = queuedRuns.get(i);
                out.append("\n").append(i + 1).append(". ")
                        .append(run.floor()).append(" ")
                        .append(memberNames(run.members()))
                        .append(" 等待 ").append(waitSeconds(run.enqueuedAtMillis())).append(" 秒");
            }
            return out.toString();
        }
    }

    public void sendQueueStatus(Player player) {
        synchronized (queuedRuns) {
            for (int i = 0; i < queuedRuns.size(); i++) {
                QueuedRun run = queuedRuns.get(i);
                boolean related = run.members().stream().anyMatch(member -> member.uuid().equals(player.getUniqueId()));
                if (related) {
                    player.sendPlainMessage("你的队伍正在副本候补队列第 " + (i + 1) + " 位，楼层：" + run.floor() + "。");
                    return;
                }
            }
        }
        player.sendPlainMessage("你的队伍当前不在副本候补队列中。");
    }

    private void requestDynamicRun(QueuedRun queued, boolean fromQueue) {
        if (!fromQueue) {
            partyService.broadcast(memberIds(queued.members()), "正在分配副本地图，请稍候。");
        }
        instanceManager.reserveSlot(queued.floor(), queued.runId(), queued.members()).thenAccept(handle -> {
            PendingRun run = new PendingRun(
                    queued.runId(),
                    queued.leaderId(),
                    queued.floor(),
                    queued.members(),
                    queued.originalServers(),
                    handle.registeredServer(),
                    true,
                    handle.serverName(),
                    handle.slotId(),
                    handle.worldName()
            );
            pendingRuns.put(run.runId(), run);
            partyService.broadcast(memberIds(run.members()), fromQueue
                    ? "候补已轮到你的队伍，正在传送到副本。"
                    : "已分配副本地图，正在准备传送。");
            connectPartyAndStart(run);
            scheduleAckTimeout(run.runId());
            finishQueueAttempt(true, queued);
        }).exceptionally(error -> {
            if (DungeonInstanceManager.isPoolFull(error)) {
                if (!fromQueue) {
                    enqueueRun(queued);
                } else {
                    finishQueueAttempt(false, queued);
                    scheduleQueuePump();
                }
                return null;
            }
            String message = DungeonInstanceManager.userMessage(error);
            partyService.broadcast(memberIds(queued.members()), "副本地图分配失败：" + message);
            finishQueueAttempt(true, queued);
            return null;
        });
    }

    private void enqueueRun(QueuedRun queued) {
        int position;
        synchronized (queuedRuns) {
            queuedRuns.add(queued);
            position = queuedRuns.size();
        }
        partyService.broadcast(memberIds(queued.members()),
                "副本实例暂时满载，已加入候补队列（第 " + position + " 位）。可用 /dungeon queue 查看进度。");
        scheduleQueuePump();
    }

    private void scheduleQueuePump() {
        server.getScheduler().buildTask(plugin, this::processQueue)
                .delay(10, TimeUnit.SECONDS)
                .schedule();
    }

    private void processQueue() {
        QueuedRun next;
        synchronized (queuedRuns) {
            if (queueProcessing || queuedRuns.isEmpty()) {
                return;
            }
            queueProcessing = true;
            next = queuedRuns.get(0);
        }
        if (!queuedMembersOnline(next)) {
            synchronized (queuedRuns) {
                queuedRuns.remove(next);
                queueProcessing = false;
            }
            partyService.broadcast(memberIds(next.members()), "候补队伍有成员不在线，已移出副本队列。");
            processQueue();
            return;
        }
        requestDynamicRun(next, true);
    }

    private void finishQueueAttempt(boolean remove, QueuedRun queued) {
        boolean hasMore;
        synchronized (queuedRuns) {
            if (remove) {
                queuedRuns.remove(queued);
            }
            queueProcessing = false;
            hasMore = !queuedRuns.isEmpty();
        }
        if (hasMore && remove) {
            processQueue();
        }
    }

    private void connectPartyAndStart(PendingRun run) {
        partyService.broadcast(memberIds(run.members()), "正在传送队伍到副本服务器：" + run.serverName());

        List<CompletableFuture<Boolean>> connects = new ArrayList<>();
        for (PartyMember member : run.members()) {
            Optional<Player> player = server.getPlayer(member.uuid());
            if (player.isEmpty()) {
                connects.add(CompletableFuture.completedFuture(false));
                continue;
            }
            CompletableFuture<Boolean> connected = player.get().createConnectionRequest(run.dungeonServer())
                    .connect()
                    .thenApply(result -> result.getStatus() == ConnectionRequestBuilder.Status.SUCCESS
                            || result.getStatus() == ConnectionRequestBuilder.Status.ALREADY_CONNECTED)
                    .exceptionally(error -> {
                        logger.warn("Failed to connect player {} to dungeon server {}",
                                member.name(), run.serverName(), error);
                        return false;
                    });
            connects.add(connected);
        }

        CompletableFuture.allOf(connects.toArray(CompletableFuture[]::new)).thenRun(() -> {
            boolean ok = connects.stream().allMatch(future -> future.getNow(false));
            if (!ok) {
                failRun(run.runId(), "有队员传送到副本服务器失败，已取消开局。", true);
                return;
            }
            sendStartMessage(run);
        });
    }

    private void scheduleAckTimeout(UUID runId) {
        server.getScheduler().buildTask(plugin, () -> {
            PendingRun stillPending = pendingRuns.get(runId);
            if (stillPending != null) {
                failRun(runId, "副本服务器响应超时，已取消开局。", true);
            }
        }).delay(config.startTimeoutSeconds(), TimeUnit.SECONDS).schedule();
    }

    private void sendStartMessage(PendingRun run) {
        byte[] payload = BridgeProtocol.encodeStartRun(
                run.runId(),
                config.bridgeToken(),
                run.floor(),
                run.slotId(),
                run.worldName(),
                run.leaderId(),
                run.members()
        );
        boolean sent = run.dungeonServer().sendPluginMessage(channel, payload);
        if (!sent) {
            failRun(run.runId(), "无法发送开局请求到副本服务器。", true);
        }
    }

    private void handleAck(BridgeProtocol.AckMessage ack) {
        PendingRun run = pendingRuns.remove(ack.runId());
        if (run == null) {
            return;
        }
        List<UUID> members = memberIds(run.members());
        if (ack.success()) {
            if (run.dynamicInstance()) {
                instanceManager.activateRun(run.runId(), run.members());
            }
            partyService.broadcast(members, ack.message().isBlank() ? "副本已生成。" : ack.message());
            return;
        }
        failRun(run, ack.message().isBlank() ? "副本服务器拒绝了开局请求。" : ack.message(), true);
    }

    private void handleEndRun(BridgeProtocol.EndRunMessage endRun) {
        instanceManager.endRun(endRun.runId(), endRun.slotId(), endRun.worldName(), endRun.reason());
    }

    private void failRun(UUID runId, String message, boolean returnPlayers) {
        PendingRun run = pendingRuns.remove(runId);
        if (run != null) {
            failRun(run, message, returnPlayers);
        } else if (config.dynamicInstancesEnabled()) {
            instanceManager.failRun(runId, message, returnPlayers);
        }
    }

    private void failRun(PendingRun run, String message, boolean returnPlayers) {
        List<UUID> members = memberIds(run.members());
        partyService.broadcast(members, message);
        if (run.dynamicInstance()) {
            instanceManager.failRun(run.runId(), message, returnPlayers);
            return;
        }
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

    private Map<UUID, RegisteredServer> currentServersOf(List<PartyMember> members) {
        Map<UUID, RegisteredServer> originalServers = new LinkedHashMap<>();
        for (PartyMember member : members) {
            server.getPlayer(member.uuid()).flatMap(Player::getCurrentServer)
                    .map(ServerConnection::getServer)
                    .ifPresent(current -> originalServers.put(member.uuid(), current));
        }
        return originalServers;
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

    private boolean hasPendingOrQueuedRun(List<PartyMember> members) {
        List<UUID> ids = memberIds(members);
        for (PendingRun run : pendingRuns.values()) {
            if (run.members().stream().anyMatch(member -> ids.contains(member.uuid()))) {
                return true;
            }
        }
        synchronized (queuedRuns) {
            for (QueuedRun run : queuedRuns) {
                if (run.members().stream().anyMatch(member -> ids.contains(member.uuid()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean queuedMembersOnline(QueuedRun run) {
        for (PartyMember member : run.members()) {
            if (server.getPlayer(member.uuid()).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String memberNames(List<PartyMember> members) {
        return members.stream().map(PartyMember::name).toList().toString();
    }

    private static long waitSeconds(long enqueuedAtMillis) {
        return Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - enqueuedAtMillis));
    }

    private record QueuedRun(
            UUID runId,
            UUID leaderId,
            String floor,
            List<PartyMember> members,
            Map<UUID, RegisteredServer> originalServers,
            long enqueuedAtMillis
    ) {
    }

    private record PendingRun(
            UUID runId,
            UUID leaderId,
            String floor,
            List<PartyMember> members,
            Map<UUID, RegisteredServer> originalServers,
            RegisteredServer dungeonServer,
            boolean dynamicInstance,
            String serverName,
            String slotId,
            String worldName
    ) {
    }
}
