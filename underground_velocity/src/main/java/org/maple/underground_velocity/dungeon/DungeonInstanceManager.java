package org.maple.underground_velocity.dungeon;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.maple.underground_velocity.bridge.BridgeProtocol;
import org.maple.underground_velocity.config.BridgeConfig;
import org.maple.underground_velocity.party.PartyMember;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DungeonInstanceManager {
    private static final Set<String> TEMPLATE_RUNTIME_DIRS = Set.of(
            "logs",
            "cache",
            ".paper-remapped",
            ".mixin.out",
            "crash-reports",
            "world",
            "world_nether",
            "world_the_end"
    );

    private final ProxyServer server;
    private final Object plugin;
    private final Logger logger;
    private final BridgeConfig config;
    private final ChannelIdentifier channel;
    private final Path templateDir;
    private final Path workDir;
    private final Map<String, Instance> byServerName = new ConcurrentHashMap<>();
    private final Map<Integer, Instance> byPort = new ConcurrentHashMap<>();
    private final Map<UUID, Slot> slotByRunId = new ConcurrentHashMap<>();

    public DungeonInstanceManager(
            ProxyServer server,
            Object plugin,
            Logger logger,
            BridgeConfig config,
            ChannelIdentifier channel
    ) {
        this.server = server;
        this.plugin = plugin;
        this.logger = logger;
        this.config = config;
        this.channel = channel;
        this.templateDir = resolveProcessPath(config.instanceTemplateDir());
        this.workDir = resolveProcessPath(config.instanceWorkDir());
    }

    public void ensureMinimumPool() {
        if (!config.dynamicInstancesEnabled()) {
            return;
        }
        int missing = Math.max(0, config.poolMinServers() - byServerName.size());
        for (int i = 0; i < missing; i++) {
            createPoolInstance().exceptionally(error -> {
                logger.warn("Failed to create startup dungeon pool server: {}", userMessage(error));
                return null;
            });
        }
    }

    public CompletableFuture<SlotHandle> reserveSlot(String floor, UUID runId, List<PartyMember> members) {
        String normalizedFloor = normalizeFloor(floor);
        Slot slot = reserveExistingSlot(normalizedFloor, runId, members);
        if (slot != null) {
            return CompletableFuture.completedFuture(slot.handle());
        }
        if (byServerName.size() >= config.poolMaxServers()) {
            return failedFuture(new PoolFullException("动态副本服务器池已满，已进入候补队列。"));
        }
        return createPoolInstance().thenApply(instance -> {
            Slot created = reserveExistingSlot(normalizedFloor, runId, members);
            if (created == null) {
                throw new CompletionException(new InstanceStartupException("新副本服务器没有可用地图槽位。"));
            }
            return created.handle();
        });
    }

    public void activateRun(UUID runId, List<PartyMember> members) {
        Slot slot = slotByRunId.get(runId);
        if (slot == null) {
            return;
        }
        synchronized (slot.instance) {
            slot.state = SlotState.ACTIVE;
            slot.disconnectedAt.clear();
            addMembers(slot.instance, members);
        }
    }

    public void endRun(UUID runId, String slotId, String worldName, String reason) {
        Slot slot = findSlot(runId, slotId, worldName);
        if (slot == null) {
            return;
        }
        String message = reason == null || reason.isBlank() ? "副本已结束，正在返回大厅。" : reason;
        returnSlotPlayersToLobby(slot, message);
        synchronized (slot.instance) {
            slot.state = SlotState.WAITING_RESET;
            slot.runId = null;
            slot.instance.memberIds.removeAll(slot.members);
            slot.members.clear();
            slot.disconnectedAt.clear();
            slotByRunId.remove(runId, slot);
            logger.info("Dungeon slot ended: server={} slot={} world={} reason={}",
                    slot.instance.serverName, slot.slotId, slot.worldName, reason);
            maybeResetInstance(slot.instance);
        }
    }

    public void failRun(UUID runId, String reason, boolean returnPlayers) {
        Slot slot = slotByRunId.remove(runId);
        if (slot == null) {
            return;
        }
        if (returnPlayers) {
            returnSlotPlayersToLobby(slot, reason == null || reason.isBlank() ? "副本已取消，正在返回大厅。" : reason);
        }
        synchronized (slot.instance) {
            slot.state = SlotState.IDLE;
            slot.runId = null;
            slot.instance.memberIds.removeAll(slot.members);
            slot.members.clear();
            slot.disconnectedAt.clear();
        }
    }

    public void leave(Player player) {
        if (player == null) {
            return;
        }
        Optional<ServerConnection> current = player.getCurrentServer();
        if (current.isEmpty()) {
            player.sendPlainMessage("你当前不在动态副本服务器中。");
            return;
        }
        String serverName = current.get().getServerInfo().getName();
        if (!byServerName.containsKey(serverName)) {
            player.sendPlainMessage("你当前不在动态副本服务器中。");
            return;
        }
        removeFromActiveSlot(player.getUniqueId(), "玩家主动离开副本。");
        connectToReturnServer(player, "正在返回大厅。");
    }

    public void handleDisconnect(UUID playerId) {
        Slot slot = findActiveSlotByMember(playerId);
        if (slot == null) {
            return;
        }
        synchronized (slot.instance) {
            if (slot.state != SlotState.ACTIVE || !slot.members.contains(playerId)) {
                return;
            }
            slot.disconnectedAt.put(playerId, System.currentTimeMillis());
        }
        logger.info("Player {} disconnected in dungeon run {}; holding slot for {} seconds.",
                playerId, slot.runId, config.reconnectGraceSeconds());
        scheduleReconnectExpiry(slot, playerId);
    }

    public boolean tryReconnect(Player player) {
        if (player == null) {
            return false;
        }
        Slot slot = findActiveSlotByMember(player.getUniqueId());
        if (slot == null) {
            return false;
        }
        synchronized (slot.instance) {
            Long disconnectedAt = slot.disconnectedAt.get(player.getUniqueId());
            if (disconnectedAt == null) {
                return false;
            }
            long ageMillis = System.currentTimeMillis() - disconnectedAt;
            if (ageMillis > TimeUnit.SECONDS.toMillis(config.reconnectGraceSeconds())) {
                expireDisconnectedMember(slot, player.getUniqueId());
                return false;
            }
            slot.disconnectedAt.remove(player.getUniqueId());
        }
        player.sendPlainMessage("已恢复你的副本会话，正在送回副本服务器。");
        player.createConnectionRequest(slot.instance.registeredServer)
                .connect()
                .exceptionally(error -> {
                    logger.warn("Failed to reconnect player {} to dungeon server {}",
                            player.getUsername(), slot.instance.serverName, error);
                    return null;
                });
        return true;
    }

    public boolean isDynamicServer(String serverName) {
        return serverName != null && byServerName.containsKey(serverName);
    }

    public void scheduleEmptyCheck(RegisteredServer registeredServer) {
        if (registeredServer == null) {
            return;
        }
        scheduleEmptyCheck(registeredServer.getServerInfo().getName());
    }

    public void scheduleEmptyCheck(String serverName) {
        Instance instance = byServerName.get(serverName);
        if (instance == null) {
            return;
        }
        server.getScheduler().buildTask(plugin, () -> recoverAbandonedSlots(instance))
                .delay(Math.max(5, config.emptyShutdownDelaySeconds()), TimeUnit.SECONDS)
                .schedule();
    }

    public CompletableFuture<Integer> addServers(int count) {
        int allowed = Math.max(0, Math.min(count, config.poolMaxServers() - byServerName.size()));
        List<CompletableFuture<Instance>> futures = new ArrayList<>();
        for (int i = 0; i < allowed; i++) {
            futures.add(createPoolInstance());
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(ignored -> futures.size());
    }

    public AdminResult deleteServer(String serverName, boolean force) {
        Instance instance = byServerName.get(serverName);
        if (instance == null) {
            return AdminResult.fail("找不到动态副本服务器：" + serverName);
        }
        if (!force && !canStopSafely(instance)) {
            return AdminResult.fail("该服务器仍有玩家或活动槽位，请加 force。");
        }
        stopInstance(instance, force, "admin-delete");
        return AdminResult.ok("已删除动态副本服务器：" + serverName);
    }

    public CompletableFuture<AdminResult> restartServer(String serverName, boolean force) {
        Instance instance = byServerName.get(serverName);
        if (instance == null) {
            return CompletableFuture.completedFuture(AdminResult.fail("找不到动态副本服务器：" + serverName));
        }
        if (!force && !canStopSafely(instance)) {
            return CompletableFuture.completedFuture(AdminResult.fail("该服务器仍有玩家或活动槽位，请加 force。"));
        }
        stopInstance(instance, force, "admin-restart");
        return createPoolInstance()
                .thenApply(created -> AdminResult.ok("已重启动态副本服务器：" + serverName + " -> " + created.serverName))
                .exceptionally(error -> AdminResult.fail("重启失败：" + userMessage(error)));
    }

    public String describeStatus() {
        StringBuilder out = new StringBuilder();
        int totalSlots = byServerName.values().stream().mapToInt(instance -> instance.slots.size()).sum();
        long idleSlots = byServerName.values().stream()
                .flatMap(instance -> instance.slots.stream())
                .filter(slot -> slot.state == SlotState.IDLE)
                .count();
        long activeSlots = byServerName.values().stream()
                .flatMap(instance -> instance.slots.stream())
                .filter(slot -> slot.state == SlotState.ACTIVE || slot.state == SlotState.RESERVED)
                .count();
        out.append("动态副本实例面板\n");
        out.append("服务器：").append(byServerName.size()).append("/")
                .append(config.poolMaxServers()).append(" 台，槽位：")
                .append(idleSlots).append("/").append(totalSlots).append(" 可用，")
                .append(activeSlots).append(" 使用中\n");
        for (Instance instance : byServerName.values().stream()
                .sorted(Comparator.comparing(i -> i.serverName))
                .toList()) {
            out.append("- ").append(instance.serverName)
                    .append(" port=").append(instance.port)
                    .append(" state=").append(instance.state)
                    .append(" players=").append(instance.registeredServer.getPlayersConnected().size())
                    .append("\n");
            for (Slot slot : instance.slots) {
                out.append("  * ").append(slot.slotId)
                        .append(" ").append(slot.floor)
                        .append(" ").append(slot.state)
                        .append(" world=").append(slot.worldName);
                if (slot.runId != null) {
                    out.append(" run=").append(shortId(slot.runId));
                }
                if (!slot.members.isEmpty()) {
                    out.append(" members=").append(slot.members.size());
                }
                if (!slot.disconnectedAt.isEmpty()) {
                    out.append(" reconnecting=").append(slot.disconnectedAt.size());
                }
                out
                        .append("\n");
            }
        }
        return out.toString().stripTrailing();
    }

    public List<String> serverNames() {
        return byServerName.keySet().stream().sorted().toList();
    }

    public void stopAll() {
        for (Instance instance : List.copyOf(byServerName.values())) {
            stopInstanceSynchronously(instance, true, "proxy-shutdown");
        }
    }

    public static String userMessage(Throwable throwable) {
        Throwable current = unwrap(throwable);
        if (current instanceof InstanceStartupException) {
            return current.getMessage();
        }
        String message = current != null ? current.getMessage() : null;
        return message == null || message.isBlank() ? "动态副本实例启动失败。" : message;
    }

    public static boolean isPoolFull(Throwable throwable) {
        return unwrap(throwable) instanceof PoolFullException;
    }

    private synchronized Slot reserveExistingSlot(String floor, UUID runId, List<PartyMember> members) {
        List<Instance> candidates = byServerName.values().stream()
                .filter(instance -> instance.state == InstanceState.READY)
                .filter(instance -> instance.freeSlotCountFor(floor) > 0 || instance.anyIdleSlot() != null)
                .sorted(Comparator
                        .comparingInt((Instance instance) -> {
                            int exact = instance.freeSlotCountFor(floor);
                            return exact > 0 ? exact : Integer.MAX_VALUE;
                        })
                        .thenComparing(instance -> instance.serverName))
                .toList();
        for (Instance instance : candidates) {
            synchronized (instance) {
                Slot slot = instance.findIdleSlotFor(floor);
                if (slot == null && config.onDemandFloors().contains(floor)) {
                    slot = instance.anyIdleSlot();
                    if (slot != null) {
                        slot.floor = floor;
                        slot.worldName = randomWorldName();
                    }
                }
                if (slot == null) {
                    continue;
                }
                slot.state = SlotState.RESERVED;
                slot.runId = runId;
                slot.members.clear();
                addMembers(instance, members);
                if (members != null) {
                    for (PartyMember member : members) {
                        if (member != null && member.uuid() != null) {
                            slot.members.add(member.uuid());
                        }
                    }
                }
                slotByRunId.put(runId, slot);
                return slot;
            }
        }
        return null;
    }

    private CompletableFuture<Instance> createPoolInstance() {
        return CompletableFuture.supplyAsync(() -> {
            int port = -1;
            Instance instance = null;
            Path instanceDir = null;
            ServerInfo info = null;
            Process process = null;
            String serverName = null;
            try {
                validateTemplate();
                Files.createDirectories(workDir);

                port = allocatePort();
                serverName = uniqueServerName();
                instanceDir = workDir.resolve(serverName).normalize();
                if (!instanceDir.startsWith(workDir)) {
                    throw new InstanceStartupException("实例路径不安全：" + instanceDir);
                }
                if (Files.exists(instanceDir)) {
                    throw new InstanceStartupException("实例目录已存在：" + instanceDir);
                }

                List<Slot> slots = createSlots(serverName, null);
                copyTemplate(instanceDir);
                patchServerProperties(instanceDir, port);
                writePaperForwardingConfig(instanceDir);
                writeBridgeConfig(instanceDir, serverName, slots);

                info = new ServerInfo(serverName, new InetSocketAddress("127.0.0.1", port));
                process = startPaper(instanceDir);
                RegisteredServer registeredServer = server.registerServer(info);
                instance = new Instance(serverName, port, instanceDir, info, registeredServer, process, slots);
                for (Slot slot : slots) {
                    slot.instance = instance;
                }
                byServerName.put(serverName, instance);
                byPort.put(port, instance);
                watchProcess(instance);

                waitForPing(instance);
                waitForSlotsReady(instance);
                instance.state = InstanceState.READY;
                logger.info("Dynamic dungeon pool server ready: server={} port={} dir={}",
                        serverName, port, instanceDir);
                return instance;
            } catch (Exception e) {
                if (instance != null) {
                    cleanupFailedInstance(instance);
                } else if (port > 0) {
                    byPort.remove(port);
                    stopProcess(process);
                    if (info != null) {
                        try {
                            server.unregisterServer(info);
                        } catch (Exception ignored) {
                        }
                    }
                    deleteInstanceDirectory(instanceDir);
                }
                throw new CompletionException(e);
            }
        });
    }

    private void maybeResetInstance(Instance instance) {
        if (instance == null || instance.state != InstanceState.READY) {
            return;
        }
        boolean hasWaiting = instance.slots.stream().anyMatch(slot -> slot.state == SlotState.WAITING_RESET);
        boolean hasBusySlot = instance.slots.stream().anyMatch(slot ->
                slot.state == SlotState.BUILDING
                        || slot.state == SlotState.RESERVED
                        || slot.state == SlotState.ACTIVE
                        || slot.state == SlotState.RESETTING);
        if (!hasWaiting || hasBusySlot) {
            return;
        }
        instance.state = InstanceState.DRAINING;
        List<BridgeProtocol.SlotReset> resetSlots = new ArrayList<>();
        for (Slot slot : instance.slots) {
            if (slot.state != SlotState.WAITING_RESET) {
                continue;
            }
            slot.state = SlotState.RESETTING;
            slot.floor = config.preloadFloor();
            slot.worldName = randomWorldName();
            resetSlots.add(new BridgeProtocol.SlotReset(slot.slotId, slot.worldName));
        }
        byte[] payload = BridgeProtocol.encodeResetSlots(config.bridgeToken(), config.preloadFloor(), resetSlots);
        if (!instance.registeredServer.sendPluginMessage(channel, payload)) {
            logger.warn("Failed to send RESET_SLOTS to {}", instance.serverName);
            for (Slot slot : instance.slots) {
                slot.state = SlotState.FAILED;
            }
            instance.state = InstanceState.READY;
            return;
        }
        server.getScheduler().buildTask(plugin, () -> {
            synchronized (instance) {
                for (Slot slot : instance.slots) {
                    if (slot.state == SlotState.RESETTING) {
                        slot.state = SlotState.IDLE;
                    }
                }
                instance.state = InstanceState.READY;
            }
        }).delay(Math.max(5, config.startTimeoutSeconds()), TimeUnit.SECONDS).schedule();
    }

    private List<Slot> createSlots(String serverName, Instance instance) {
        List<Slot> slots = new ArrayList<>();
        for (int i = 1; i <= config.mapsPerServer(); i++) {
            String slotId = "slot-" + i;
            slots.add(new Slot(instance, slotId, config.preloadFloor(), randomWorldName()));
        }
        return slots;
    }

    private void validateTemplate() {
        if (Files.notExists(templateDir) || !Files.isDirectory(templateDir)) {
            throw new InstanceStartupException("副本模板目录不存在：" + templateDir);
        }
        Path paperJar = templateDir.resolve(config.paperJar());
        if (Files.notExists(paperJar) || !Files.isRegularFile(paperJar)) {
            throw new InstanceStartupException("副本模板缺少 Paper jar：" + paperJar);
        }
    }

    private synchronized int allocatePort() {
        for (int port = config.portMin(); port <= config.portMax(); port++) {
            if (byPort.containsKey(port)) {
                continue;
            }
            if (!isPortFree(port)) {
                continue;
            }
            byPort.put(port, Instance.reserved(port));
            return port;
        }
        throw new InstanceStartupException("动态副本端口池已满（" + config.portMin() + "-" + config.portMax() + "）。");
    }

    private boolean isPortFree(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private String uniqueServerName() {
        for (int i = 1; i <= config.poolMaxServers() + 20; i++) {
            String name = "dungeon_pool_" + i;
            if (server.getServer(name).isEmpty() && !byServerName.containsKey(name)) {
                return name;
            }
        }
        return "dungeon_pool_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String randomWorldName() {
        return "dungeon_" + UUID.randomUUID();
    }

    private void copyTemplate(Path instanceDir) throws IOException {
        Files.walkFileTree(templateDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = templateDir.relativize(dir);
                if (!relative.toString().isBlank() && shouldSkip(relative)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(instanceDir.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = templateDir.relativize(file);
                if (!shouldSkip(relative)) {
                    Files.copy(file, instanceDir.resolve(relative), StandardCopyOption.COPY_ATTRIBUTES);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean shouldSkip(Path relative) {
        if (relative == null || relative.getNameCount() == 0) {
            return false;
        }
        String first = relative.getName(0).toString().toLowerCase(Locale.ROOT);
        if (TEMPLATE_RUNTIME_DIRS.contains(first) || first.startsWith("dungeon_")) {
            return true;
        }
        String fileName = relative.getFileName() != null ? relative.getFileName().toString().toLowerCase(Locale.ROOT) : "";
        return fileName.equals("session.lock")
                || fileName.endsWith(".lock")
                || fileName.endsWith(".tmp");
    }

    private void patchServerProperties(Path instanceDir, int port) throws IOException {
        Path file = instanceDir.resolve("server.properties");
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (var reader = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
                properties.load(reader);
            }
        }
        properties.setProperty("server-port", String.valueOf(port));
        properties.setProperty("online-mode", "false");
        properties.setProperty("enable-query", "false");
        properties.setProperty("enable-rcon", "false");
        try (var writer = Files.newBufferedWriter(file, StandardCharsets.ISO_8859_1)) {
            properties.store(writer, "Generated by underground_velocity dynamic dungeon instance manager");
        }
    }

    private void writePaperForwardingConfig(Path instanceDir) throws IOException {
        String secret = readForwardingSecret();
        Path configDir = instanceDir.resolve("config");
        Files.createDirectories(configDir);
        Path file = configDir.resolve("paper-global.yml");

        List<String> lines = Files.exists(file)
                ? Files.readAllLines(file, StandardCharsets.UTF_8)
                : new ArrayList<>(List.of("_version: 31"));
        List<String> withoutProxies = removeTopLevelSection(lines, "proxies");
        if (!withoutProxies.isEmpty() && !withoutProxies.get(withoutProxies.size() - 1).isBlank()) {
            withoutProxies.add("");
        }
        withoutProxies.add("proxies:");
        withoutProxies.add("  bungee-cord:");
        withoutProxies.add("    online-mode: true");
        withoutProxies.add("  proxy-protocol: false");
        withoutProxies.add("  velocity:");
        withoutProxies.add("    enabled: true");
        withoutProxies.add("    online-mode: true");
        withoutProxies.add("    secret: " + secret);
        Files.write(file, withoutProxies, StandardCharsets.UTF_8);
    }

    private String readForwardingSecret() throws IOException {
        Path secretFile = resolveProcessPath(config.forwardingSecretFile());
        if (Files.notExists(secretFile) || !Files.isRegularFile(secretFile)) {
            throw new InstanceStartupException("找不到 Velocity forwarding.secret：" + secretFile);
        }
        String secret = Files.readString(secretFile, StandardCharsets.UTF_8).trim();
        if (secret.isBlank()) {
            throw new InstanceStartupException("Velocity forwarding.secret 为空：" + secretFile);
        }
        return secret;
    }

    private static List<String> removeTopLevelSection(List<String> lines, String sectionName) {
        List<String> out = new ArrayList<>();
        String header = sectionName + ":";
        boolean skipping = false;
        for (String line : lines) {
            String trimmed = line.trim();
            boolean topLevel = !line.startsWith(" ") && !line.startsWith("\t");
            if (!skipping && topLevel && header.equals(trimmed)) {
                skipping = true;
                continue;
            }
            if (skipping && topLevel && !trimmed.isEmpty() && !trimmed.startsWith("#")) {
                skipping = false;
            }
            if (!skipping) {
                out.add(line);
            }
        }
        return out;
    }

    private void writeBridgeConfig(Path instanceDir, String serverName, List<Slot> slots) throws IOException {
        Path dir = instanceDir.resolve("plugins").resolve("underground_caves");
        Files.createDirectories(dir);
        Path file = dir.resolve("bridge.yml");
        List<String> lines = new ArrayList<>();
        lines.add("bridge-token: \"" + yamlDoubleQuoted(config.bridgeToken()) + "\"");
        lines.add("server-name: \"" + yamlDoubleQuoted(serverName) + "\"");
        lines.add("maps-per-server: " + config.mapsPerServer());
        lines.add("preload-floor: \"" + yamlDoubleQuoted(config.preloadFloor()) + "\"");
        lines.add("pool-state-dir: \"dungeon-pool\"");
        lines.add("slots:");
        for (Slot slot : slots) {
            lines.add("  - id: \"" + yamlDoubleQuoted(slot.slotId) + "\"");
            lines.add("    floor: \"" + yamlDoubleQuoted(slot.floor) + "\"");
            lines.add("    world: \"" + yamlDoubleQuoted(slot.worldName) + "\"");
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private String yamlDoubleQuoted(String value) {
        String raw = value != null ? value : "";
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Process startPaper(Path instanceDir) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(config.javaCommand());
        command.add("-Xms" + config.instanceXms());
        command.add("-Xmx" + config.instanceXmx());
        command.add("-jar");
        command.add(config.paperJar());
        command.add("nogui");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(instanceDir.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(instanceDir.resolve("instance.log").toFile()));
        logger.info("Starting dynamic dungeon pool server: dir={} portRange={}-{} command={}",
                instanceDir, config.portMin(), config.portMax(), String.join(" ", command));
        return builder.start();
    }

    private void waitForPing(Instance instance) {
        long deadline = System.nanoTime() + Duration.ofSeconds(config.instanceStartupTimeoutSeconds()).toNanos();
        while (System.nanoTime() < deadline) {
            if (!instance.process.isAlive()) {
                throw new InstanceStartupException("动态副本实例进程提前退出：" + instance.serverName);
            }
            try {
                instance.registeredServer.ping().get(3, TimeUnit.SECONDS);
                return;
            } catch (Exception ignored) {
                sleep(1000L);
            }
        }
        throw new InstanceStartupException("动态副本实例启动超时：" + instance.serverName);
    }

    private void waitForSlotsReady(Instance instance) {
        Path stateFile = instance.instanceDir
                .resolve("plugins")
                .resolve("underground_caves")
                .resolve("dungeon-pool")
                .resolve("slots.yml");
        long deadline = System.nanoTime() + Duration.ofSeconds(config.instanceStartupTimeoutSeconds()).toNanos();
        while (System.nanoTime() < deadline) {
            if (!instance.process.isAlive()) {
                throw new InstanceStartupException("Dynamic dungeon process exited before slots were ready: " + instance.serverName);
            }
            if (areAllSlotsIdle(stateFile, instance.slots.size())) {
                return;
            }
            sleep(1000L);
        }
        throw new InstanceStartupException("Dynamic dungeon slot preload timed out: " + instance.serverName);
    }

    private boolean areAllSlotsIdle(Path stateFile, int expectedSlots) {
        if (expectedSlots <= 0 || stateFile == null || Files.notExists(stateFile)) {
            return false;
        }
        try {
            int idle = 0;
            for (String line : Files.readAllLines(stateFile, StandardCharsets.UTF_8)) {
                String trimmed = line != null ? line.trim() : "";
                if ("state: IDLE".equalsIgnoreCase(trimmed) || "state: \"IDLE\"".equalsIgnoreCase(trimmed)) {
                    idle++;
                }
            }
            return idle >= expectedSlots;
        } catch (IOException ignored) {
            return false;
        }
    }

    private void watchProcess(Instance instance) {
        instance.process.onExit().thenRun(() -> {
            if (instance.stopping.get()) {
                return;
            }
            logger.warn("Dynamic dungeon pool server crashed/exited unexpectedly: server={}", instance.serverName);
            returnPlayersToLobby(instance, "副本服务器已关闭，正在返回大厅。");
            removeInstance(instance);
            deleteInstanceDirectory(instance);
        });
    }

    private void scheduleReconnectExpiry(Slot slot, UUID playerId) {
        server.getScheduler().buildTask(plugin, () -> {
            synchronized (slot.instance) {
                Long disconnectedAt = slot.disconnectedAt.get(playerId);
                if (disconnectedAt == null) {
                    return;
                }
                long ageMillis = System.currentTimeMillis() - disconnectedAt;
                if (ageMillis < TimeUnit.SECONDS.toMillis(config.reconnectGraceSeconds())) {
                    return;
                }
                expireDisconnectedMember(slot, playerId);
            }
        }).delay(config.reconnectGraceSeconds(), TimeUnit.SECONDS).schedule();
    }

    private void expireDisconnectedMember(Slot slot, UUID playerId) {
        slot.disconnectedAt.remove(playerId);
        slot.members.remove(playerId);
        slot.instance.memberIds.remove(playerId);
        logger.info("Dungeon reconnect window expired: player={} server={} slot={} run={}",
                playerId, slot.instance.serverName, slot.slotId, slot.runId);
        if (slot.state == SlotState.ACTIVE && slot.members.isEmpty()) {
            recycleAbandonedSlot(slot, "所有队员均已断线，自动回收副本槽位。");
        }
    }

    private void recoverAbandonedSlots(Instance instance) {
        if (instance == null || instance.state != InstanceState.READY) {
            return;
        }
        synchronized (instance) {
            Set<UUID> online = new HashSet<>();
            for (Player player : instance.registeredServer.getPlayersConnected()) {
                online.add(player.getUniqueId());
            }
            for (Slot slot : instance.slots) {
                if (slot.state != SlotState.ACTIVE) {
                    continue;
                }
                boolean hasOnlineMember = slot.members.stream().anyMatch(online::contains);
                if (!hasOnlineMember && slot.disconnectedAt.size() >= slot.members.size()) {
                    boolean expired = slot.disconnectedAt.values().stream().allMatch(disconnectedAt ->
                            System.currentTimeMillis() - disconnectedAt >= TimeUnit.SECONDS.toMillis(config.reconnectGraceSeconds()));
                    if (expired) {
                        recycleAbandonedSlot(slot, "活跃槽位已无在线队员，自动回收。");
                    }
                }
            }
        }
    }

    private void removeFromActiveSlot(UUID playerId, String reason) {
        Slot slot = findActiveSlotByMember(playerId);
        if (slot == null) {
            return;
        }
        synchronized (slot.instance) {
            slot.disconnectedAt.remove(playerId);
            slot.members.remove(playerId);
            slot.instance.memberIds.remove(playerId);
            if (slot.state == SlotState.ACTIVE && slot.members.isEmpty()) {
                recycleAbandonedSlot(slot, reason);
            }
        }
    }

    private void recycleAbandonedSlot(Slot slot, String reason) {
        UUID oldRunId = slot.runId;
        if (oldRunId != null) {
            slotByRunId.remove(oldRunId, slot);
        }
        slot.state = SlotState.WAITING_RESET;
        slot.runId = null;
        slot.instance.memberIds.removeAll(slot.members);
        slot.members.clear();
        slot.disconnectedAt.clear();
        logger.info("Dungeon slot recycled: server={} slot={} reason={}",
                slot.instance.serverName, slot.slotId, reason);
        maybeResetInstance(slot.instance);
    }

    private Slot findActiveSlotByMember(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        for (Instance instance : byServerName.values()) {
            for (Slot slot : instance.slots) {
                if (slot.state == SlotState.ACTIVE && slot.members.contains(playerId)) {
                    return slot;
                }
            }
        }
        return null;
    }

    private void cleanupFailedInstance(Instance instance) {
        instance.stopping.set(true);
        stopProcess(instance);
        removeInstance(instance);
        deleteInstanceDirectory(instance);
    }

    private boolean canStopSafely(Instance instance) {
        if (instance == null || !instance.registeredServer.getPlayersConnected().isEmpty()) {
            return false;
        }
        return instance.slots.stream().noneMatch(slot ->
                slot.state == SlotState.RESERVED || slot.state == SlotState.ACTIVE || slot.state == SlotState.BUILDING);
    }

    private void stopInstance(Instance instance, boolean returnPlayers, String reason) {
        if (instance == null || !instance.stopping.compareAndSet(false, true)) {
            return;
        }
        instance.state = InstanceState.STOPPING;
        if (returnPlayers) {
            returnPlayersToLobby(instance, reason == null || reason.isBlank() ? "副本服务器正在关闭，正在返回大厅。" : reason);
        }
        CompletableFuture.runAsync(() -> stopInstanceResources(instance, reason));
    }

    private void stopInstanceSynchronously(Instance instance, boolean returnPlayers, String reason) {
        if (instance == null || !instance.stopping.compareAndSet(false, true)) {
            return;
        }
        instance.state = InstanceState.STOPPING;
        if (returnPlayers) {
            returnPlayersToLobby(instance, reason == null || reason.isBlank() ? "副本服务器正在关闭，正在返回大厅。" : reason);
        }
        stopInstanceResources(instance, reason);
    }

    private void stopInstanceResources(Instance instance, String reason) {
        stopProcess(instance);
        removeInstance(instance);
        deleteInstanceDirectory(instance);
        logger.info("Dynamic dungeon pool server stopped: server={} reason={}", instance.serverName, reason);
    }

    private void stopProcess(Instance instance) {
        stopProcess(instance.process);
    }

    private void stopProcess(Process process) {
        if (process == null) {
            return;
        }
        if (process.isAlive()) {
            try {
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
                writer.write("stop");
                writer.newLine();
                writer.flush();
            } catch (IOException ignored) {
            }
            try {
                if (!process.waitFor(config.shutdownGraceSeconds(), TimeUnit.SECONDS)) {
                    process.destroy();
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }

    private void returnSlotPlayersToLobby(Slot slot, String message) {
        Set<UUID> ids = new HashSet<>(slot.members);
        for (UUID id : ids) {
            server.getPlayer(id).ifPresent(player -> connectToReturnServer(player, message));
        }
    }

    private void returnPlayersToLobby(Instance instance, String message) {
        Set<UUID> ids = new HashSet<>(instance.memberIds);
        for (Player player : instance.registeredServer.getPlayersConnected()) {
            ids.add(player.getUniqueId());
        }
        for (UUID id : ids) {
            server.getPlayer(id).ifPresent(player -> connectToReturnServer(player, message));
        }
    }

    private CompletableFuture<Boolean> connectToReturnServer(Player player, String message) {
        Optional<RegisteredServer> target = server.getServer(config.returnServer());
        if (target.isEmpty()) {
            player.sendPlainMessage("找不到返回服务器：" + config.returnServer());
            return CompletableFuture.completedFuture(false);
        }
        if (message != null && !message.isBlank()) {
            player.sendPlainMessage(message);
        }
        return player.createConnectionRequest(target.get())
                .connect()
                .thenApply(result -> result.getStatus() == ConnectionRequestBuilder.Status.SUCCESS
                        || result.getStatus() == ConnectionRequestBuilder.Status.ALREADY_CONNECTED)
                .exceptionally(error -> {
                    logger.warn("Failed to return player {} to {}", player.getUsername(), config.returnServer(), error);
                    return false;
                });
    }

    private void removeInstance(Instance instance) {
        byServerName.remove(instance.serverName, instance);
        byPort.remove(instance.port, instance);
        for (Slot slot : instance.slots) {
            if (slot.runId != null) {
                slotByRunId.remove(slot.runId, slot);
            }
        }
        try {
            server.unregisterServer(instance.serverInfo);
        } catch (Exception e) {
            logger.warn("Failed to unregister dynamic dungeon server {}", instance.serverName, e);
        }
    }

    private void deleteInstanceDirectory(Instance instance) {
        if (!config.deleteInstanceOnStop()) {
            return;
        }
        deleteInstanceDirectory(instance.instanceDir);
    }

    private void deleteInstanceDirectory(Path instanceDir) {
        if (!config.deleteInstanceOnStop() || instanceDir == null) {
            return;
        }
        Path dir = instanceDir.normalize();
        if (!dir.startsWith(workDir) || dir.equals(workDir) || dir.getFileName() == null
                || !dir.getFileName().toString().startsWith("dungeon_pool_")) {
            logger.warn("Refusing to delete unsafe dynamic instance directory: {}", dir);
            return;
        }
        try {
            if (Files.notExists(dir)) {
                return;
            }
            try (var stream = Files.walk(dir)) {
                List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
                for (Path path : paths) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to delete dynamic dungeon instance directory {}", dir, e);
        }
    }

    private Slot findSlot(UUID runId, String slotId, String worldName) {
        if (runId != null) {
            Slot slot = slotByRunId.get(runId);
            if (slot != null) {
                return slot;
            }
        }
        for (Instance instance : byServerName.values()) {
            for (Slot slot : instance.slots) {
                if (worldName != null && !worldName.isBlank() && slot.worldName.equals(worldName)) {
                    return slot;
                }
                if (slotId != null && !slotId.isBlank() && slot.slotId.equals(slotId)) {
                    return slot;
                }
            }
        }
        return null;
    }

    private void addMembers(Instance instance, List<PartyMember> members) {
        if (instance == null || members == null) {
            return;
        }
        for (PartyMember member : members) {
            if (member != null && member.uuid() != null) {
                instance.memberIds.add(member.uuid());
            }
        }
    }

    private static Path resolveProcessPath(String raw) {
        Path path = Path.of(raw == null || raw.isBlank() ? "." : raw);
        if (!path.isAbsolute()) {
            path = Path.of("").toAbsolutePath().resolve(path);
        }
        return path.normalize();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InstanceStartupException("动态副本实例启动等待被中断。");
        }
    }

    private static String normalizeFloor(String raw) {
        if (raw == null) {
            return "F1";
        }
        String floor = raw.trim().toUpperCase(Locale.ROOT);
        return switch (floor) {
            case "F1", "F2", "F3" -> floor;
            default -> "F1";
        };
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String shortId(UUID uuid) {
        if (uuid == null) {
            return "";
        }
        String text = uuid.toString();
        return text.length() <= 8 ? text : text.substring(0, 8);
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }

    public record SlotHandle(
            UUID runId,
            String serverName,
            RegisteredServer registeredServer,
            String slotId,
            String worldName,
            String floor
    ) {
    }

    public record AdminResult(boolean success, String message) {
        public static AdminResult ok(String message) {
            return new AdminResult(true, message);
        }

        public static AdminResult fail(String message) {
            return new AdminResult(false, message);
        }
    }

    public static class InstanceStartupException extends RuntimeException {
        public InstanceStartupException(String message) {
            super(message);
        }
    }

    public static final class PoolFullException extends InstanceStartupException {
        public PoolFullException(String message) {
            super(message);
        }
    }

    private enum InstanceState {
        STARTING,
        READY,
        DRAINING,
        STOPPING
    }

    private enum SlotState {
        BUILDING,
        IDLE,
        RESERVED,
        ACTIVE,
        WAITING_RESET,
        RESETTING,
        FAILED
    }

    private static final class Instance {
        private final String serverName;
        private final int port;
        private final Path instanceDir;
        private final ServerInfo serverInfo;
        private final RegisteredServer registeredServer;
        private final Process process;
        private final List<Slot> slots;
        private final Set<UUID> memberIds = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean stopping = new AtomicBoolean(false);
        private volatile InstanceState state = InstanceState.STARTING;

        private Instance(
                String serverName,
                int port,
                Path instanceDir,
                ServerInfo serverInfo,
                RegisteredServer registeredServer,
                Process process,
                List<Slot> slots
        ) {
            this.serverName = serverName;
            this.port = port;
            this.instanceDir = instanceDir;
            this.serverInfo = serverInfo;
            this.registeredServer = registeredServer;
            this.process = process;
            this.slots = slots;
        }

        private static Instance reserved(int port) {
            return new Instance("reserved_" + port, port, Path.of("."), null, null, null, List.of());
        }

        private int freeSlotCountFor(String floor) {
            int count = 0;
            for (Slot slot : slots) {
                if (slot.state == SlotState.IDLE && slot.floor.equalsIgnoreCase(floor)) {
                    count++;
                }
            }
            return count;
        }

        private Slot findIdleSlotFor(String floor) {
            for (Slot slot : slots) {
                if (slot.state == SlotState.IDLE && slot.floor.equalsIgnoreCase(floor)) {
                    return slot;
                }
            }
            return null;
        }

        private Slot anyIdleSlot() {
            for (Slot slot : slots) {
                if (slot.state == SlotState.IDLE) {
                    return slot;
                }
            }
            return null;
        }
    }

    private static final class Slot {
        private Instance instance;
        private final String slotId;
        private String floor;
        private String worldName;
        private SlotState state = SlotState.IDLE;
        private UUID runId;
        private final Set<UUID> members = ConcurrentHashMap.newKeySet();
        private final Map<UUID, Long> disconnectedAt = new ConcurrentHashMap<>();

        private Slot(Instance instance, String slotId, String floor, String worldName) {
            this.instance = instance;
            this.slotId = slotId;
            this.floor = floor;
            this.worldName = worldName;
        }

        private SlotHandle handle() {
            return new SlotHandle(runId, instance.serverName, instance.registeredServer, slotId, worldName, floor);
        }
    }
}
