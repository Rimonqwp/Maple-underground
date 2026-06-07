package maple.underground_caves.dungeon;

import maple.underground_caves.Test_dungeons;
import maple.underground_caves.party.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DungeonBridgeListener implements PluginMessageListener {
    public static final String CHANNEL = "maple:dungeon_bridge";

    private static final String ACTION_START_RUN = "START_RUN";
    private static final String ACTION_ACK = "ACK";
    private static final String ACTION_END_RUN = "END_RUN";

    private final Test_dungeons plugin;
    private final PartyManager partyManager;
    private final DungeonCommand dungeonCommand;
    private final String bridgeToken;
    private final Map<UUID, String> worldByRunId = new ConcurrentHashMap<>();
    private final Map<String, UUID> runIdByWorld = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> bindTasks = new ConcurrentHashMap<>();

    public DungeonBridgeListener(Test_dungeons plugin, PartyManager partyManager, DungeonCommand dungeonCommand) {
        this.plugin = plugin;
        this.partyManager = partyManager;
        this.dungeonCommand = dungeonCommand;
        this.bridgeToken = loadBridgeToken(plugin);
    }

    public void register() {
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    public void unregister() {
        for (BukkitTask task : bindTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        bindTasks.clear();
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player carrier, byte @NotNull [] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }

        UUID runId = null;
        try {
            StartRunRequest request = decodeStartRun(message);
            runId = request.runId();
            String error = validate(request);
            if (error != null) {
                sendAck(carrier, runId, false, error);
                return;
            }

            partyManager.setPartySnapshot(request.leaderUuid(), request.memberUuids());
            Player leader = Bukkit.getPlayer(request.leaderUuid());
            if (leader == null || !leader.isOnline()) {
                sendAck(carrier, runId, false, "队长不在线，副本无法开始。");
                return;
            }

            boolean accepted = dungeonCommand.startDungeonRun(leader, request.floor(), false);
            if (accepted) {
                scheduleWorldBinding(request.runId());
            }
            sendAck(carrier, runId, accepted,
                    accepted ? "副本服务器已接受开局请求，正在生成地图。" : "副本服务器无法开始该楼层。");
        } catch (Exception e) {
            plugin.getLogger().warning("[DungeonBridge] Failed to handle START_RUN: " + e.getMessage());
            if (runId != null) {
                sendAck(carrier, runId, false, "副本服务器处理开局请求时发生错误。");
            }
        }
    }

    public void sendEndRunForWorld(String worldName, String reason) {
        if (worldName == null || worldName.isBlank()) {
            return;
        }
        UUID runId = runIdByWorld.get(worldName);
        if (runId == null) {
            plugin.getLogger().warning("[DungeonBridge] Cannot send END_RUN, no runId bound for world=" + worldName);
            return;
        }
        Optional<Player> carrier = findCarrier(worldName);
        if (carrier.isEmpty()) {
            plugin.getLogger().warning("[DungeonBridge] Cannot send END_RUN, no online carrier for world=" + worldName);
            return;
        }
        sendEndRun(carrier.get(), runId, reason);
    }

    private void scheduleWorldBinding(UUID runId) {
        BukkitTask previous = bindTasks.remove(runId);
        if (previous != null) {
            previous.cancel();
        }
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int attempts = 0;

            @Override
            public void run() {
                attempts++;
                if (tryBindLatestDungeonWorld(runId) || attempts >= 600) {
                    BukkitTask self = bindTasks.remove(runId);
                    if (self != null) {
                        self.cancel();
                    }
                }
            }
        }, 1L, 1L);
        bindTasks.put(runId, task);
    }

    private boolean tryBindLatestDungeonWorld(UUID runId) {
        if (worldByRunId.containsKey(runId)) {
            return true;
        }
        Test_dungeons.DungeonSnapshot snapshot = plugin.getLastDungeon();
        if (snapshot == null || snapshot.worldName() == null || snapshot.worldName().isBlank()) {
            return false;
        }
        String worldName = snapshot.worldName();
        if (!worldName.startsWith("dungeon_")) {
            return false;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null || !plugin.isDungeonWorld(worldName)) {
            return false;
        }
        worldByRunId.put(runId, worldName);
        runIdByWorld.put(worldName, runId);
        plugin.getLogger().info("[DungeonBridge] Bound runId=" + runId + " to world=" + worldName);
        return true;
    }

    private Optional<Player> findCarrier(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            for (Player player : world.getPlayers()) {
                if (player != null && player.isOnline()) {
                    return Optional.of(player);
                }
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player != null && player.isOnline()) {
                return Optional.of(player);
            }
        }
        return Optional.empty();
    }

    private String validate(StartRunRequest request) {
        if (request == null) {
            return "无效的开局请求。";
        }
        if (!bridgeToken.isBlank() && !bridgeToken.equals(request.token())) {
            return "副本桥接 token 不匹配。";
        }
        String floor = normalizeFloor(request.floor());
        if (floor == null) {
            return "楼层无效，请使用 F1、F2 或 F3。";
        }
        if (request.memberUuids().isEmpty() || request.memberUuids().size() > 5) {
            return "队伍人数无效。";
        }
        if (!request.memberUuids().contains(request.leaderUuid())) {
            return "队伍资料缺少队长。";
        }
        for (UUID memberId : request.memberUuids()) {
            Player player = Bukkit.getPlayer(memberId);
            if (player == null || !player.isOnline()) {
                return "队伍中有玩家尚未到达副本服。";
            }
        }
        return null;
    }

    private StartRunRequest decodeStartRun(byte[] message) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
        String action = in.readUTF();
        if (!ACTION_START_RUN.equals(action)) {
            throw new IOException("Unexpected action: " + action);
        }

        UUID runId = UUID.fromString(in.readUTF());
        String token = in.readUTF();
        String floor = normalizeFloor(in.readUTF());
        UUID leaderUuid = UUID.fromString(in.readUTF());
        int memberCount = in.readInt();
        if (memberCount < 1 || memberCount > 5) {
            throw new IOException("Invalid member count: " + memberCount);
        }

        List<UUID> memberUuids = new ArrayList<>();
        List<String> memberNames = new ArrayList<>();
        for (int i = 0; i < memberCount; i++) {
            memberUuids.add(UUID.fromString(in.readUTF()));
            memberNames.add(in.readUTF());
        }
        return new StartRunRequest(runId, token, floor, leaderUuid, memberUuids, memberNames);
    }

    private void sendAck(Player carrier, UUID runId, boolean success, String message) {
        if (carrier == null || runId == null) {
            return;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF(ACTION_ACK);
            out.writeUTF(runId.toString());
            out.writeBoolean(success);
            out.writeUTF(message != null ? message : "");
            out.flush();
            carrier.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("[DungeonBridge] Failed to send ACK: " + e.getMessage());
        }
    }

    private void sendEndRun(Player carrier, UUID runId, String reason) {
        if (carrier == null || runId == null) {
            return;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF(ACTION_END_RUN);
            out.writeUTF(runId.toString());
            out.writeUTF(reason != null ? reason : "副本已结束。");
            out.flush();
            carrier.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
            plugin.getLogger().info("[DungeonBridge] Sent END_RUN runId=" + runId + " reason=" + reason);
        } catch (IOException e) {
            plugin.getLogger().warning("[DungeonBridge] Failed to send END_RUN: " + e.getMessage());
        }
    }

    private static String loadBridgeToken(Test_dungeons plugin) {
        File file = new File(plugin.getDataFolder(), "bridge.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        return config.getString("bridge-token", "");
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

    private record StartRunRequest(
            UUID runId,
            String token,
            String floor,
            UUID leaderUuid,
            List<UUID> memberUuids,
            List<String> memberNames
    ) {
    }
}
