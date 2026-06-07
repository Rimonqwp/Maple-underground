package org.maple.untitled1.config;

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
        int maxPartySize,
        int inviteTimeoutSeconds,
        int startTimeoutSeconds,
        String bridgeToken
) {
    private static final String DEFAULT_CONFIG = """
            dungeon-server: dungeon-server
            max-party-size: 5
            invite-timeout-seconds: 60
            start-timeout-seconds: 30
            bridge-token: ""
            """;

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
                    integer(values, "max-party-size", 5, 1, 5),
                    integer(values, "invite-timeout-seconds", 60, 5, 3600),
                    integer(values, "start-timeout-seconds", 30, 5, 300),
                    string(values, "bridge-token", "")
            );
        } catch (IOException e) {
            logger.warn("Failed to load config.yml, using defaults.", e);
            return new BridgeConfig("dungeon-server", 5, 60, 30, "");
        }
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
}
