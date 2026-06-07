package maple.underground_caves.dungeon;

import maple.underground_caves.Test_dungeons;
import maple.underground_caves.party.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DungeonBridgeListener implements PluginMessageListener {
    public static final String CHANNEL = "maple:dungeon_bridge";

    private static final String ACTION_START_RUN = "START_RUN";
    private static final String ACTION_RESET_SLOTS = "RESET_SLOTS";
    private static final String ACTION_ACK = "ACK";
    private static final String ACTION_END_RUN = "END_RUN";
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Test_dungeons plugin;
    private final PartyManager partyManager;
    private final DungeonCommand dungeonCommand;
    private final YamlConfiguration bridgeConfig;
    private final String bridgeToken;
    private final String serverName;
    private final String preloadFloor;
    private final File poolDir;
    private final File logsDir;
    private final File stateFile;
    private final Map<UUID, RunBinding> runById = new ConcurrentHashMap<>();
    private final Map<String, UUID> runIdByWorld = new ConcurrentHashMap<>();
    private final Map<String, SlotBinding> slotById = new ConcurrentHashMap<>();
    private final Map<String, String> slotIdByWorld = new ConcurrentHashMap<>();
    private final Set<String> buildingWorlds = ConcurrentHashMap.newKeySet();
    private final Map<String, List<Runnable>> readyCallbacksByWorld = new ConcurrentHashMap<>();

    public DungeonBridgeListener(Test_dungeons plugin, PartyManager partyManager, DungeonCommand dungeonCommand) {
        this.plugin = plugin;
        this.partyManager = partyManager;
        this.dungeonCommand = dungeonCommand;
        this.bridgeConfig = loadBridgeConfig(plugin);
        this.bridgeToken = bridgeConfig.getString("bridge-token", "");
        this.serverName = bridgeConfig.getString("server-name", Bukkit.getServer().getName());
        String configuredPreloadFloor = normalizeFloor(bridgeConfig.getString("preload-floor", "F1"));
        this.preloadFloor = configuredPreloadFloor != null ? configuredPreloadFloor : "F1";
        String poolStateDir = bridgeConfig.getString("pool-state-dir", "dungeon-pool");
        this.poolDir = new File(plugin.getDataFolder(), poolStateDir == null || poolStateDir.isBlank() ? "dungeon-pool" : poolStateDir);
        this.logsDir = new File(poolDir, "logs");
        this.stateFile = new File(poolDir, "slots.yml");
        loadConfiguredSlots();
    }

    public void register() {
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        preloadConfiguredSlots();
    }

    public void unregister() {
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player carrier, byte @NotNull [] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String action = in.readUTF();
            if (ACTION_START_RUN.equals(action)) {
                handleStartRun(carrier, decodeStartRun(in));
                return;
            }
            if (ACTION_RESET_SLOTS.equals(action)) {
                handleResetSlots(decodeResetSlots(in));
                return;
            }
            plugin.getLogger().warning("[DungeonBridge] Unknown action: " + action);
        } catch (Exception e) {
            plugin.getLogger().warning("[DungeonBridge] Failed to handle plugin message: " + e.getMessage());
        }
    }

    public void sendEndRunForWorld(String worldName, String reason) {
        if (worldName == null || worldName.isBlank()) {
            return;
        }
        UUID runId = runIdByWorld.get(worldName);
        String slotId = slotIdByWorld.getOrDefault(worldName, "");
        if (runId == null) {
            plugin.getLogger().warning("[DungeonBridge] Cannot send END_RUN, no runId bound for world=" + worldName);
            return;
        }
        Optional<Player> carrier = findCarrier(worldName);
        if (carrier.isEmpty()) {
            plugin.getLogger().warning("[DungeonBridge] Cannot send END_RUN, no online carrier for world=" + worldName);
            return;
        }
        sendEndRun(carrier.get(), runId, slotId, worldName, reason);
    }

    private void handleStartRun(Player carrier, StartRunRequest request) {
        UUID runId = request != null ? request.runId() : null;
        try {
            String error = validate(request);
            if (error != null) {
                sendAck(carrier, runId, false, error, request != null ? request.slotId() : "", request != null ? request.worldName() : "");
                return;
            }

            SlotBinding previousSlot = slotById.get(request.slotId());
            if (previousSlot != null && !previousSlot.worldName().equals(request.worldName())) {
                logWorld(previousSlot.worldName(), "START_RUN replacing idle slot=" + request.slotId()
                        + " oldWorld=" + previousSlot.worldName()
                        + " newWorld=" + request.worldName()
                        + " floor=" + request.floor());
                deleteDungeonWorld(previousSlot.worldName());
            }
            bindSlot(request.slotId(), request.worldName(), request.floor());
            partyManager.setPartySnapshot(request.leaderUuid(), request.memberUuids());
            Player leader = Bukkit.getPlayer(request.leaderUuid());
            if (leader == null || !leader.isOnline()) {
                sendAck(carrier, runId, false, "队长不在线，副本无法开始。", request.slotId(), request.worldName());
                logWorld(request.worldName(), "START_RUN rejected: leader offline runId=" + runId + " slot=" + request.slotId());
                return;
            }

            RunBinding binding = new RunBinding(runId, request.slotId(), request.worldName(), request.floor(), request.memberUuids());
            runById.put(runId, binding);
            runIdByWorld.put(request.worldName(), runId);
            slotIdByWorld.put(request.worldName(), request.slotId());
            saveState();
            logWorld(request.worldName(), "START_RUN accepted runId=" + runId
                    + " server=" + serverName
                    + " slot=" + request.slotId()
                    + " floor=" + request.floor()
                    + " members=" + request.memberNames());

            if (buildingWorlds.contains(request.worldName())) {
                logWorld(request.worldName(), "START_RUN waiting for build runId=" + runId + " slot=" + request.slotId());
                onWorldReady(request.worldName(), () -> Bukkit.getScheduler().runTask(plugin, () -> handleStartRun(carrier, request)));
                return;
            }
            if (plugin.getDungeonSnapshot(request.worldName()) == null) {
                beginWorldBuild(request.worldName());
            }
            boolean accepted = dungeonCommand.startDungeonRun(leader, request.floor(), false, request.worldName(), () -> {
                finishWorldBuild(request.worldName());
                saveState();
                logWorld(request.worldName(), "START_RUN ready runId=" + runId + " slot=" + request.slotId());
                sendAck(carrier, runId, true, "副本地图已准备完成。", request.slotId(), request.worldName());
            });
            if (!accepted) {
                finishWorldBuild(request.worldName());
                runById.remove(runId);
                runIdByWorld.remove(request.worldName(), runId);
                sendAck(carrier, runId, false, "副本服务器无法开始该楼层。", request.slotId(), request.worldName());
                logWorld(request.worldName(), "START_RUN failed runId=" + runId + " slot=" + request.slotId());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[DungeonBridge] Failed to handle START_RUN: " + e.getMessage());
            if (runId != null) {
                finishWorldBuild(request != null ? request.worldName() : null);
                sendAck(carrier, runId, false, "副本服务器处理开局请求时发生错误。", request != null ? request.slotId() : "", request != null ? request.worldName() : "");
            }
        }
    }

    private void handleResetSlots(ResetSlotsRequest request) {
        String error = validateReset(request);
        if (error != null) {
            plugin.getLogger().warning("[DungeonBridge] RESET_SLOTS rejected: " + error);
            return;
        }
        if (request.slots().isEmpty()) {
            return;
        }
        preloadResetSlotsSequentially(request.preloadFloor(), request.slots(), 0);
    }

    private void preloadResetSlotsSequentially(String floor, List<SlotReset> slots, int index) {
        if (index >= slots.size()) {
            saveState();
            return;
        }
        SlotReset reset = slots.get(index);
        SlotBinding previous = slotById.get(reset.slotId());
        String oldWorld = previous != null ? previous.worldName() : null;
        if (oldWorld != null && !oldWorld.isBlank() && !oldWorld.equals(reset.worldName())) {
            logWorld(oldWorld, "RESET deleting old world for slot=" + reset.slotId() + " nextWorld=" + reset.worldName());
            deleteDungeonWorld(oldWorld);
        }

        bindSlot(reset.slotId(), reset.worldName(), floor);
        saveState();
        logWorld(reset.worldName(), "RESET rebuilding slot=" + reset.slotId() + " floor=" + floor + " server=" + serverName);
        beginWorldBuild(reset.worldName());
        boolean accepted = dungeonCommand.preloadDungeonWorld(floor, reset.worldName(), () -> {
            finishWorldBuild(reset.worldName());
            logWorld(reset.worldName(), "RESET preload complete slot=" + reset.slotId() + " floor=" + floor);
            preloadResetSlotsSequentially(floor, slots, index + 1);
        });
        if (!accepted) {
            finishWorldBuild(reset.worldName());
            logWorld(reset.worldName(), "RESET preload failed slot=" + reset.slotId() + " floor=" + floor);
            preloadResetSlotsSequentially(floor, slots, index + 1);
        }
    }

    private void preloadConfiguredSlots() {
        List<SlotBinding> slots = new ArrayList<>(slotById.values());
        slots.sort(Comparator.comparing(SlotBinding::slotId));
        preloadConfiguredSlotsSequentially(slots, 0);
    }

    private void preloadConfiguredSlotsSequentially(List<SlotBinding> slots, int index) {
        if (index >= slots.size()) {
            saveState();
            return;
        }
        SlotBinding slot = slots.get(index);
        Test_dungeons.DungeonSnapshot existing = plugin.getDungeonSnapshot(slot.worldName());
        if (existing != null && slot.floor().equalsIgnoreCase(existing.floor())) {
            logWorld(slot.worldName(), "PRELOAD skipped existing slot=" + slot.slotId() + " floor=" + slot.floor());
            preloadConfiguredSlotsSequentially(slots, index + 1);
            return;
        }
        logWorld(slot.worldName(), "PRELOAD start server=" + serverName + " slot=" + slot.slotId() + " floor=" + slot.floor());
        beginWorldBuild(slot.worldName());
        boolean accepted = dungeonCommand.preloadDungeonWorld(slot.floor(), slot.worldName(), () -> {
            finishWorldBuild(slot.worldName());
            logWorld(slot.worldName(), "PRELOAD complete server=" + serverName + " slot=" + slot.slotId() + " floor=" + slot.floor());
            preloadConfiguredSlotsSequentially(slots, index + 1);
        });
        if (!accepted) {
            finishWorldBuild(slot.worldName());
            logWorld(slot.worldName(), "PRELOAD failed server=" + serverName + " slot=" + slot.slotId() + " floor=" + slot.floor());
            preloadConfiguredSlotsSequentially(slots, index + 1);
        }
    }

    private String validate(StartRunRequest request) {
        if (request == null) {
            return "无效的开局请求。";
        }
        if (!bridgeToken.isBlank() && !bridgeToken.equals(request.token())) {
            return "副本桥接 token 不匹配。";
        }
        if (normalizeFloor(request.floor()) == null) {
            return "楼层无效，请使用 F1、F2 或 F3。";
        }
        if (request.slotId().isBlank() || request.worldName().isBlank()) {
            return "副本槽位资料不完整。";
        }
        if (!request.worldName().startsWith("dungeon_")) {
            return "副本世界名无效。";
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

    private String validateReset(ResetSlotsRequest request) {
        if (request == null) {
            return "empty request";
        }
        if (!bridgeToken.isBlank() && !bridgeToken.equals(request.token())) {
            return "token mismatch";
        }
        if (normalizeFloor(request.preloadFloor()) == null) {
            return "invalid floor";
        }
        for (SlotReset slot : request.slots()) {
            if (slot.slotId().isBlank() || slot.worldName().isBlank() || !slot.worldName().startsWith("dungeon_")) {
                return "invalid slot reset: " + slot;
            }
        }
        return null;
    }

    private StartRunRequest decodeStartRun(DataInputStream in) throws IOException {
        UUID runId = UUID.fromString(in.readUTF());
        String token = in.readUTF();
        String floor = normalizeFloor(in.readUTF());
        String slotId = in.readUTF();
        String worldName = in.readUTF();
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
        return new StartRunRequest(runId, token, floor, slotId, worldName, leaderUuid, memberUuids, memberNames);
    }

    private ResetSlotsRequest decodeResetSlots(DataInputStream in) throws IOException {
        String token = in.readUTF();
        String preloadFloor = normalizeFloor(in.readUTF());
        int count = in.readInt();
        if (count < 0 || count > 32) {
            throw new IOException("Invalid reset slot count: " + count);
        }
        List<SlotReset> slots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            slots.add(new SlotReset(in.readUTF(), in.readUTF()));
        }
        return new ResetSlotsRequest(token, preloadFloor, slots);
    }

    private void sendAck(Player carrier, UUID runId, boolean success, String message, String slotId, String worldName) {
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
            out.writeUTF(slotId != null ? slotId : "");
            out.writeUTF(worldName != null ? worldName : "");
            out.flush();
            carrier.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("[DungeonBridge] Failed to send ACK: " + e.getMessage());
        }
    }

    private void sendEndRun(Player carrier, UUID runId, String slotId, String worldName, String reason) {
        if (carrier == null || runId == null) {
            return;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF(ACTION_END_RUN);
            out.writeUTF(runId.toString());
            out.writeUTF(slotId != null ? slotId : "");
            out.writeUTF(worldName != null ? worldName : "");
            out.writeUTF(reason != null ? reason : "副本已结束。");
            out.flush();
            carrier.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
            runById.remove(runId);
            runIdByWorld.remove(worldName, runId);
            saveState();
            logWorld(worldName, "END_RUN sent runId=" + runId + " slot=" + slotId + " reason=" + reason);
        } catch (IOException e) {
            plugin.getLogger().warning("[DungeonBridge] Failed to send END_RUN: " + e.getMessage());
        }
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

    private void deleteDungeonWorld(String worldName) {
        if (worldName == null || worldName.isBlank() || !worldName.startsWith("dungeon_")) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            Location fallback = plugin.getMainWorldSpawnLocation();
            for (Player player : new ArrayList<>(world.getPlayers())) {
                if (player != null && player.isOnline() && fallback != null) {
                    player.teleport(fallback);
                }
            }
            Bukkit.unloadWorld(world, false);
        }
        plugin.forgetDungeonWorld(worldName);
        runIdByWorld.remove(worldName);
        slotIdByWorld.remove(worldName);
        Path worldContainer = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        Path worldDir = worldContainer.resolve(worldName).normalize();
        if (!worldDir.startsWith(worldContainer) || worldDir.equals(worldContainer) || worldDir.getFileName() == null
                || !worldDir.getFileName().toString().equals(worldName)) {
            plugin.getLogger().warning("[DungeonBridge] Refusing to delete unsafe dungeon world path: " + worldDir);
            return;
        }
        if (Files.notExists(worldDir)) {
            return;
        }
        try (var stream = Files.walk(worldDir)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
            logWorld(worldName, "WORLD deleted path=" + worldDir);
        } catch (IOException e) {
            plugin.getLogger().warning("[DungeonBridge] Failed to delete world " + worldName + ": " + e.getMessage());
            logWorld(worldName, "WORLD delete failed: " + e.getMessage());
        }
    }

    private void loadConfiguredSlots() {
        List<Map<?, ?>> rawSlots = bridgeConfig.getMapList("slots");
        if (rawSlots.isEmpty()) {
            int count = Math.max(0, bridgeConfig.getInt("maps-per-server", 0));
            for (int i = 1; i <= count; i++) {
                String slotId = "slot-" + i;
                String worldName = "dungeon_" + UUID.randomUUID();
                bindSlot(slotId, worldName, preloadFloor);
            }
            return;
        }
        for (Map<?, ?> raw : rawSlots) {
            if (raw == null) {
                continue;
            }
            String slotId = asString(raw.get("id"));
            String worldName = asString(raw.get("world"));
            String floor = normalizeFloor(asString(raw.get("floor")));
            if (slotId.isBlank() || worldName.isBlank()) {
                continue;
            }
            bindSlot(slotId, worldName, floor != null ? floor : preloadFloor);
        }
        saveState();
    }

    private void bindSlot(String slotId, String worldName, String floor) {
        if (slotId == null || slotId.isBlank() || worldName == null || worldName.isBlank()) {
            return;
        }
        SlotBinding previous = slotById.put(slotId, new SlotBinding(slotId, worldName, floor != null ? floor : preloadFloor));
        if (previous != null && !previous.worldName().equals(worldName)) {
            slotIdByWorld.remove(previous.worldName(), slotId);
        }
        slotIdByWorld.put(worldName, slotId);
    }

    private void beginWorldBuild(String worldName) {
        if (worldName != null && !worldName.isBlank()) {
            buildingWorlds.add(worldName);
        }
    }

    private void finishWorldBuild(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return;
        }
        buildingWorlds.remove(worldName);
        List<Runnable> callbacks = readyCallbacksByWorld.remove(worldName);
        if (callbacks == null || callbacks.isEmpty()) {
            return;
        }
        for (Runnable callback : List.copyOf(callbacks)) {
            if (callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        }
    }

    private void onWorldReady(String worldName, Runnable callback) {
        if (worldName == null || worldName.isBlank() || callback == null) {
            return;
        }
        if (!buildingWorlds.contains(worldName)) {
            Bukkit.getScheduler().runTask(plugin, callback);
            return;
        }
        readyCallbacksByWorld.compute(worldName, (ignored, callbacks) -> {
            List<Runnable> list = callbacks != null ? callbacks : new ArrayList<>();
            list.add(callback);
            return list;
        });
    }

    private void saveState() {
        try {
            if (!poolDir.exists() && !poolDir.mkdirs()) {
                return;
            }
            YamlConfiguration state = new YamlConfiguration();
            state.set("server-name", serverName);
            state.set("updated-at", LocalDateTime.now().format(LOG_TIME));
            List<Map<String, Object>> slots = new ArrayList<>();
            for (SlotBinding slot : slotById.values().stream().sorted(Comparator.comparing(SlotBinding::slotId)).toList()) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("id", slot.slotId());
                data.put("world", slot.worldName());
                data.put("floor", slot.floor());
                data.put("state", slotState(slot.worldName()));
                slots.add(data);
            }
            state.set("slots", slots);
            state.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[DungeonBridge] Failed to save slot state: " + e.getMessage());
        }
    }

    private String slotState(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return "BUILDING";
        }
        if (buildingWorlds.contains(worldName)) {
            return "BUILDING";
        }
        if (runIdByWorld.containsKey(worldName)) {
            return "ACTIVE";
        }
        return plugin.getDungeonSnapshot(worldName) != null ? "IDLE" : "BUILDING";
    }

    private void logWorld(String worldName, String line) {
        if (worldName == null || worldName.isBlank()) {
            return;
        }
        try {
            if (!logsDir.exists() && !logsDir.mkdirs()) {
                return;
            }
            File file = new File(logsDir, safeLogName(worldName) + ".log");
            String text = "[" + LocalDateTime.now().format(LOG_TIME) + "] " + line + System.lineSeparator();
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().warning("[DungeonBridge] Failed to write world log: " + e.getMessage());
        }
    }

    private static YamlConfiguration loadBridgeConfig(Test_dungeons plugin) {
        File file = new File(plugin.getDataFolder(), "bridge.yml");
        return YamlConfiguration.loadConfiguration(file);
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String safeLogName(String worldName) {
        return worldName.replaceAll("[^A-Za-z0-9_.-]", "_");
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
            String slotId,
            String worldName,
            UUID leaderUuid,
            List<UUID> memberUuids,
            List<String> memberNames
    ) {
    }

    private record ResetSlotsRequest(String token, String preloadFloor, List<SlotReset> slots) {
    }

    private record SlotReset(String slotId, String worldName) {
    }

    private record SlotBinding(String slotId, String worldName, String floor) {
    }

    private record RunBinding(UUID runId, String slotId, String worldName, String floor, List<UUID> members) {
    }
}
