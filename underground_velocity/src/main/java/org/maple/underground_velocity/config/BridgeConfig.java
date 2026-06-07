package org.maple.underground_velocity.config;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record BridgeConfig(
        String dungeonServer,
        String instanceMode,
        String returnServer,
        int maxPartySize,
        int inviteTimeoutSeconds,
        int startTimeoutSeconds,
        String bridgeToken,
        String instanceTemplateDir,
        String instanceWorkDir,
        int portMin,
        int portMax,
        String javaCommand,
        String paperJar,
        String instanceXms,
        String instanceXmx,
        int instanceStartupTimeoutSeconds,
        int emptyShutdownDelaySeconds,
        int shutdownGraceSeconds,
        boolean deleteInstanceOnStop,
        String forwardingSecretFile
) {
    private static final String DEFAULT_CONFIG = """
            dungeon-server: dungeon-server
            instance-mode: dynamic
            return-server: lobby
            max-party-size: 5
            invite-timeout-seconds: 60
            start-timeout-seconds: 30
            bridge-token: ""
            instance-template-dir: ../Maple_dungeon_template
            instance-work-dir: ../Maple_dungeon_instances
            port-min: 20010
            port-max: 20049
            java-command: C:\\Program Files\\Zulu\\zulu-21\\bin\\java.exe
            paper-jar: paper-1.21.11-127.jar
            instance-xms: 512M
            instance-xmx: 4G
            instance-startup-timeout-seconds: 90
            empty-shutdown-delay-seconds: 10
            shutdown-grace-seconds: 20
            delete-instance-on-stop: true
            forwarding-secret-file: forwarding.secret
            """;

    public BridgeConfig {
        instanceMode = stringOr(instanceMode, "dynamic").trim().toLowerCase(Locale.ROOT);
        returnServer = stringOr(returnServer, "lobby");
        bridgeToken = bridgeToken != null ? bridgeToken : "";
        instanceTemplateDir = stringOr(instanceTemplateDir, "../Maple_dungeon_template");
        instanceWorkDir = stringOr(instanceWorkDir, "../Maple_dungeon_instances");
        javaCommand = stringOr(javaCommand, "java");
        paperJar = stringOr(paperJar, "paper-1.21.11-127.jar");
        instanceXms = stringOr(instanceXms, "512M");
        instanceXmx = stringOr(instanceXmx, "4G");
        forwardingSecretFile = stringOr(forwardingSecretFile, "forwarding.secret");
        if (portMax < portMin) {
            int tmp = portMin;
            portMin = portMax;
            portMax = tmp;
        }
    }

    public static BridgeConfig load(Path dataDirectory, Logger logger) {
        Path configPath = dataDirectory.resolve("config.yml");
        try {
            Files.createDirectories(dataDirectory);
            if (Files.notExists(configPath)) {
                Files.writeString(configPath, DEFAULT_CONFIG, StandardCharsets.UTF_8);
            }
            Map<String, String> values = parse(Files.readAllLines(configPath, StandardCharsets.UTF_8));
            return new BridgeConfig(
                    string(values, "dungeon-server", "dungeon-server"),
                    string(values, "instance-mode", "dynamic"),
                    string(values, "return-server", "lobby"),
                    integer(values, "max-party-size", 5, 1, 5),
                    integer(values, "invite-timeout-seconds", 60, 5, 3600),
                    integer(values, "start-timeout-seconds", 30, 5, 300),
                    string(values, "bridge-token", ""),
                    string(values, "instance-template-dir", "../Maple_dungeon_template"),
                    string(values, "instance-work-dir", "../Maple_dungeon_instances"),
                    integer(values, "port-min", 20010, 1, 65535),
                    integer(values, "port-max", 20049, 1, 65535),
                    string(values, "java-command", "java"),
                    string(values, "paper-jar", "paper-1.21.11-127.jar"),
                    string(values, "instance-xms", "512M"),
                    string(values, "instance-xmx", "4G"),
                    integer(values, "instance-startup-timeout-seconds", 90, 5, 600),
                    integer(values, "empty-shutdown-delay-seconds", 10, 1, 300),
                    integer(values, "shutdown-grace-seconds", 20, 1, 300),
                    bool(values, "delete-instance-on-stop", true),
                    string(values, "forwarding-secret-file", "forwarding.secret")
            );
        } catch (IOException e) {
            logger.warn("Failed to load config.yml, using defaults.", e);
            return new BridgeConfig(
                    "dungeon-server",
                    "dynamic",
                    "lobby",
                    5,
                    60,
                    30,
                    "",
                    "../Maple_dungeon_template",
                    "../Maple_dungeon_instances",
                    20010,
                    20049,
                    "java",
                    "paper-1.21.11-127.jar",
                    "512M",
                    "4G",
                    90,
                    10,
                    20,
                    true,
                    "forwarding.secret"
            );
        }
    }

    public boolean dynamicInstancesEnabled() {
        return "dynamic".equalsIgnoreCase(instanceMode);
    }

    private static Map<String, String> parse(List<String> lines) {
        Map<String, String> values = new HashMap<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int idx = trimmed.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String key = trimmed.substring(0, idx).trim().toLowerCase(Locale.ROOT);
            String value = trimmed.substring(idx + 1).trim();
            values.put(key, unquote(value));
        }
        return values;
    }

    private static String unquote(String value) {
        if (value == null) {
            return "";
        }
        String out = value.trim();
        if ((out.startsWith("\"") && out.endsWith("\"")) || (out.startsWith("'") && out.endsWith("'"))) {
            return out.substring(1, out.length() - 1);
        }
        return out;
    }

    private static String string(Map<String, String> values, String key, String fallback) {
        String value = values.get(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String stringOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int integer(Map<String, String> values, String key, int fallback, int min, int max) {
        String raw = values.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean bool(Map<String, String> values, String key, boolean fallback) {
        String raw = values.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "y", "on", "1" -> true;
            case "false", "no", "n", "off", "0" -> false;
            default -> fallback;
        };
    }
}
