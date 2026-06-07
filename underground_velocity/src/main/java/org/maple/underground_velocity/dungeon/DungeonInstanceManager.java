package org.maple.underground_velocity.dungeon;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
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
    private final Path templateDir;
    private final Path workDir;
    private final Map<UUID, Instance> byRunId = new ConcurrentHashMap<>();
    private final Map<String, Instance> byServerName = new ConcurrentHashMap<>();
    private final Map<Integer, Instance> byPort = new ConcurrentHashMap<>();

    public DungeonInstanceManager(ProxyServer server, Object plugin, Logger logger, BridgeConfig config) {
        this.server = server;
        this.plugin = plugin;
        this.logger = logger;
        this.config = config;
        this.templateDir = resolveProcessPath(config.instanceTemplateDir());
        this.workDir = resolveProcessPath(config.instanceWorkDir());
    }

    public CompletableFuture<InstanceHandle> createInstance(UUID runId) {
        return CompletableFuture.supplyAsync(() -> {
            int port = -1;
            Instance instance = null;
            Path instanceDir = null;
            ServerInfo info = null;
            Process process = null;
            try {
                validateTemplate();
                Files.createDirectories(workDir);

                port = allocatePort();
                String serverName = uniqueServerName(runId);
                instanceDir = workDir.resolve(serverName).normalize();
                if (!instanceDir.startsWith(workDir)) {
                    throw new InstanceStartupException("实例路径不安全：" + instanceDir);
                }
                if (Files.exists(instanceDir)) {
                    throw new InstanceStartupException("实例目录已存在：" + instanceDir);
                }

                copyTemplate(instanceDir);
                patchServerProperties(instanceDir, port);
                writePaperForwardingConfig(instanceDir);
                writeBridgeConfig(instanceDir);

                info = new ServerInfo(serverName, new InetSocketAddress("127.0.0.1", port));
                process = startPaper(instanceDir);
                RegisteredServer registeredServer = server.registerServer(info);
                instance = new Instance(runId, serverName, port, instanceDir, info, registeredServer, process);
                byRunId.put(runId, instance);
                byServerName.put(serverName, instance);
                byPort.put(port, instance);
                watchProcess(instance);

                waitForPing(instance);
                logger.info("Dynamic dungeon instance ready: runId={} server={} port={} dir={}",
                        runId, serverName, port, instanceDir);
                return instance.handle();
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

    public void activateRun(UUID runId, List<PartyMember> members) {
        Instance instance = byRunId.get(runId);
        if (instance == null || members == null) {
            return;
        }
        for (PartyMember member : members) {
            if (member != null && member.uuid() != null) {
                instance.memberIds.add(member.uuid());
            }
        }
    }

    public void endRun(UUID runId, String reason) {
        Instance instance = byRunId.get(runId);
        if (instance == null) {
            return;
        }
        String message = reason == null || reason.isBlank() ? "副本已结束，正在返回大厅。" : reason;
        returnPlayersToLobby(instance, message);
        server.getScheduler().buildTask(plugin, () -> stopInstance(instance, false, "END_RUN"))
                .delay(2, TimeUnit.SECONDS)
                .schedule();
    }

    public void failRun(UUID runId, String reason, boolean returnPlayers) {
        Instance instance = byRunId.get(runId);
        if (instance == null) {
            return;
        }
        stopInstance(instance, returnPlayers, reason);
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
        Instance instance = byServerName.get(serverName);
        if (instance == null) {
            player.sendPlainMessage("你当前不在动态副本服务器中。");
            return;
        }
        connectToReturnServer(player, "正在返回大厅。").thenRun(() -> scheduleEmptyCheck(instance.serverName));
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
        if (instance == null || instance.stopping.get()) {
            return;
        }
        server.getScheduler().buildTask(plugin, () -> {
            Instance still = byServerName.get(serverName);
            if (still == null || still.stopping.get()) {
                return;
            }
            if (still.registeredServer.getPlayersConnected().isEmpty()) {
                stopInstance(still, false, "empty");
            }
        }).delay(config.emptyShutdownDelaySeconds(), TimeUnit.SECONDS).schedule();
    }

    public void stopAll() {
        for (Instance instance : List.copyOf(byRunId.values())) {
            stopInstanceSynchronously(instance, false, "proxy-shutdown");
        }
    }

    public static String userMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        if (current instanceof InstanceStartupException) {
            return current.getMessage();
        }
        String message = current != null ? current.getMessage() : null;
        return message == null || message.isBlank() ? "动态副本实例启动失败。" : message;
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

    private String uniqueServerName(UUID runId) {
        String compact = runId.toString().replace("-", "");
        for (int length = 8; length <= compact.length(); length += 4) {
            String name = "dungeon_" + compact.substring(0, length);
            if (server.getServer(name).isEmpty() && !byServerName.containsKey(name)) {
                return name;
            }
        }
        return "dungeon_" + compact;
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

    private void writeBridgeConfig(Path instanceDir) throws IOException {
        Path dir = instanceDir.resolve("plugins").resolve("underground_caves");
        Files.createDirectories(dir);
        Path file = dir.resolve("bridge.yml");
        Files.writeString(file, "bridge-token: \"" + yamlDoubleQuoted(config.bridgeToken()) + "\"\n", StandardCharsets.UTF_8);
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
        logger.info("Starting dynamic dungeon instance: dir={} portRange={}-{} command={}",
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

    private void watchProcess(Instance instance) {
        instance.process.onExit().thenRun(() -> {
            if (instance.stopping.get()) {
                return;
            }
            logger.warn("Dynamic dungeon instance crashed/exited unexpectedly: server={} runId={}",
                    instance.serverName, instance.runId);
            returnPlayersToLobby(instance, "副本服务器已关闭，正在返回大厅。");
            removeInstance(instance);
            deleteInstanceDirectory(instance);
        });
    }

    private void cleanupFailedInstance(Instance instance) {
        instance.stopping.set(true);
        stopProcess(instance);
        removeInstance(instance);
        deleteInstanceDirectory(instance);
    }

    private void stopInstance(Instance instance, boolean returnPlayers, String reason) {
        if (instance == null || !instance.stopping.compareAndSet(false, true)) {
            return;
        }
        if (returnPlayers) {
            returnPlayersToLobby(instance, reason == null || reason.isBlank() ? "副本已取消，正在返回大厅。" : reason);
        }
        CompletableFuture.runAsync(() -> {
            stopInstanceResources(instance, reason);
        });
    }

    private void stopInstanceSynchronously(Instance instance, boolean returnPlayers, String reason) {
        if (instance == null || !instance.stopping.compareAndSet(false, true)) {
            return;
        }
        if (returnPlayers) {
            returnPlayersToLobby(instance, reason == null || reason.isBlank() ? "副本已取消，正在返回大厅。" : reason);
        }
        stopInstanceResources(instance, reason);
    }

    private void stopInstanceResources(Instance instance, String reason) {
        stopProcess(instance);
        removeInstance(instance);
        deleteInstanceDirectory(instance);
        logger.info("Dynamic dungeon instance stopped: server={} runId={} reason={}",
                instance.serverName, instance.runId, reason);
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
        byRunId.remove(instance.runId, instance);
        byServerName.remove(instance.serverName, instance);
        byPort.remove(instance.port, instance);
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
                || !dir.getFileName().toString().startsWith("dungeon_")) {
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

    public record InstanceHandle(UUID runId, String serverName, RegisteredServer registeredServer) {
    }

    public static final class InstanceStartupException extends RuntimeException {
        public InstanceStartupException(String message) {
            super(message);
        }
    }

    private static final class Instance {
        private final UUID runId;
        private final String serverName;
        private final int port;
        private final Path instanceDir;
        private final ServerInfo serverInfo;
        private final RegisteredServer registeredServer;
        private final Process process;
        private final Set<UUID> memberIds = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean stopping = new AtomicBoolean(false);

        private Instance(
                UUID runId,
                String serverName,
                int port,
                Path instanceDir,
                ServerInfo serverInfo,
                RegisteredServer registeredServer,
                Process process
        ) {
            this.runId = runId;
            this.serverName = serverName;
            this.port = port;
            this.instanceDir = instanceDir;
            this.serverInfo = serverInfo;
            this.registeredServer = registeredServer;
            this.process = process;
        }

        private static Instance reserved(int port) {
            return new Instance(new UUID(0L, port), "reserved_" + port, port, Path.of("."), null, null, null);
        }

        private InstanceHandle handle() {
            return new InstanceHandle(runId, serverName, registeredServer);
        }
    }
}
