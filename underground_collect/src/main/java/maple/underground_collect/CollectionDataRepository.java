package maple.underground_collect;

import maple.underground_state.db.MySqlConnectionFactory;
import maple.underground_state.gui.CollectionInfoGui;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

class CollectionDataRepository {

    private static final String TABLE = "player_collection_reward_claims";
    private static final long LEADERBOARD_CACHE_TTL_MS = 15_000L;
    private static final int LEADERBOARD_FETCH_LIMIT = 500;

    private final @Nullable Plugin plugin;
    private final Logger logger;
    private final Map<UUID, Map<String, Integer>> claimsCache = new ConcurrentHashMap<>();
    private final Set<UUID> loadedClaims = ConcurrentHashMap.newKeySet();
    private final Set<UUID> loadingClaims = ConcurrentHashMap.newKeySet();
    private final Map<String, CachedLeaderboard> leaderboardCache = new ConcurrentHashMap<>();

    CollectionDataRepository(@NotNull Logger logger) {
        this(null, logger, true);
    }

    CollectionDataRepository(@Nullable Plugin plugin, @NotNull Logger logger) {
        this(plugin, logger, true);
    }

    CollectionDataRepository(@Nullable Plugin plugin, @NotNull Logger logger, boolean initDatabase) {
        this.plugin = plugin;
        this.logger = logger;
        if (initDatabase) {
            MySqlConnectionFactory.logTarget(logger);
            init();
        }
    }

    boolean isClaimsLoaded(@NotNull UUID uuid) {
        return loadedClaims.contains(uuid);
    }

    @NotNull
    Map<String, Integer> getCachedClaims(@NotNull UUID uuid) {
        Map<String, Integer> cached = claimsCache.get(uuid);
        return cached == null ? Map.of() : new HashMap<>(cached);
    }

    int getClaimedTier(@NotNull UUID uuid, @NotNull String collectionKey) {
        Map<String, Integer> cached = claimsCache.get(uuid);
        return cached == null ? 0 : cached.getOrDefault(collectionKey, 0);
    }

    void preloadClaimsAsync(@NotNull UUID uuid, @Nullable Runnable onReadyMainThread) {
        if (loadedClaims.contains(uuid)) {
            if (onReadyMainThread != null) {
                runSync(onReadyMainThread);
            }
            return;
        }
        runAsync(() -> {
            if (loadingClaims.add(uuid)) {
                try {
                    Map<String, Integer> loaded = loadClaimsFromDb(uuid);
                    Map<String, Integer> target = claimsCache.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>());
                    for (Map.Entry<String, Integer> entry : loaded.entrySet()) {
                        Integer tier = entry.getValue();
                        if (tier != null && tier > 0) {
                            target.merge(entry.getKey(), tier, Math::max);
                        }
                    }
                    loadedClaims.add(uuid);
                } finally {
                    loadingClaims.remove(uuid);
                }
            }
            if (onReadyMainThread != null) {
                runSync(onReadyMainThread);
            }
        });
    }

    void updateCachedClaims(@NotNull UUID uuid, @NotNull Map<String, Integer> newClaimStates) {
        Map<String, Integer> target = claimsCache.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>());
        for (Map.Entry<String, Integer> entry : newClaimStates.entrySet()) {
            Integer tier = entry.getValue();
            if (entry.getKey() != null && !entry.getKey().isBlank() && tier != null && tier > 0) {
                target.merge(entry.getKey(), tier, Math::max);
            }
        }
        loadedClaims.add(uuid);
    }

    void saveClaimsBatchAsync(@NotNull UUID uuid, @NotNull Map<String, Integer> claimsToSave) {
        if (claimsToSave.isEmpty()) {
            return;
        }
        Map<String, Integer> copy = new HashMap<>(claimsToSave);
        runAsync(() -> saveClaimsBatchToDb(uuid, copy));
    }

    void resetClaimsAsync(@NotNull UUID uuid, @Nullable CollectionInfoGui.Category category) {
        Map<String, Integer> cached = claimsCache.get(uuid);
        if (category == null) {
            if (cached != null) {
                cached.clear();
            }
        } else if (cached != null) {
            String prefix = category.pageId() + ":";
            cached.keySet().removeIf(key -> key.startsWith(prefix));
        }
        loadedClaims.add(uuid);
        runAsync(() -> deleteClaimsFromDb(uuid, category == null ? null : category.pageId()));
    }

    void evictPlayer(@NotNull UUID uuid) {
        claimsCache.remove(uuid);
        loadedClaims.remove(uuid);
        loadingClaims.remove(uuid);
    }

    void invalidateLeaderboardCache() {
        leaderboardCache.clear();
    }

    void loadLeaderboardAsync(@Nullable CollectionInfoGui.Category category,
                              @NotNull Map<UUID, Map<String, Long>> onlineSnapshots,
                              @NotNull Consumer<List<LeaderboardEntry>> callbackMainThread) {
        runAsync(() -> {
            List<LeaderboardEntry> merged = buildRealtimeLeaderboard(category, onlineSnapshots);
            runSync(() -> callbackMainThread.accept(merged));
        });
    }

    @NotNull
    List<LeaderboardEntry> buildRealtimeLeaderboard(@Nullable CollectionInfoGui.Category category,
                                                    @NotNull Map<UUID, Map<String, Long>> onlineSnapshots) {
        String cacheKey = category == null ? "overall" : category.pageId();
        long now = System.currentTimeMillis();
        CachedLeaderboard cached = leaderboardCache.get(cacheKey);
        Map<UUID, Long> totals = new HashMap<>();

        List<LeaderboardEntry> dbEntries;
        if (cached != null && now - cached.loadedAtMs() < LEADERBOARD_CACHE_TTL_MS) {
            dbEntries = cached.entries();
        } else {
            dbEntries = loadLeaderboardFromDb(category, LEADERBOARD_FETCH_LIMIT);
            leaderboardCache.put(cacheKey, new CachedLeaderboard(dbEntries, now));
        }

        for (LeaderboardEntry entry : dbEntries) {
            totals.put(entry.uuid(), entry.totalAmount());
        }

        for (Map.Entry<UUID, Map<String, Long>> onlineEntry : onlineSnapshots.entrySet()) {
            long onlineTotal = sumCategoryProgress(onlineEntry.getValue(), category);
            if (onlineTotal > 0L) {
                totals.put(onlineEntry.getKey(), onlineTotal);
            } else {
                totals.remove(onlineEntry.getKey());
            }
        }

        List<LeaderboardEntry> merged = new ArrayList<>(totals.size());
        for (Map.Entry<UUID, Long> entry : totals.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0L) {
                merged.add(new LeaderboardEntry(entry.getKey(), entry.getValue()));
            }
        }
        merged.sort(Comparator.comparingLong(LeaderboardEntry::totalAmount).reversed()
                .thenComparing(entry -> entry.uuid().toString()));
        return merged;
    }

    @NotNull
    Map<String, Integer> loadClaims(@NotNull UUID uuid) {
        if (loadedClaims.contains(uuid)) {
            return getCachedClaims(uuid);
        }
        Map<String, Integer> loaded = loadClaimsFromDb(uuid);
        claimsCache.put(uuid, new ConcurrentHashMap<>(loaded));
        loadedClaims.add(uuid);
        return new HashMap<>(loaded);
    }

    void saveClaim(@NotNull UUID uuid, @NotNull String collectionKey, int claimedTier) {
        updateCachedClaims(uuid, Map.of(collectionKey, claimedTier));
        saveClaimsBatchAsync(uuid, Map.of(collectionKey, claimedTier));
    }

    @NotNull
    List<LeaderboardEntry> loadLeaderboard(@Nullable CollectionInfoGui.Category category, int limit) {
        List<LeaderboardEntry> entries = loadLeaderboardFromDb(category, limit);
        return entries.size() <= limit ? entries : entries.subList(0, limit);
    }

    void shutdown() {
        claimsCache.clear();
        loadedClaims.clear();
        loadingClaims.clear();
        leaderboardCache.clear();
    }

    protected @NotNull Map<String, Integer> loadClaimsFromDb(@NotNull UUID uuid) {
        Map<String, Integer> claims = new HashMap<>();
        String sql = "SELECT collection_key, claimed_tier FROM " + TABLE + " WHERE uuid=?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("collection_key");
                    int tier = Math.max(0, rs.getInt("claimed_tier"));
                    if (key != null && !key.isBlank() && tier > 0) {
                        claims.put(key, tier);
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "CollectionDataRepository load claims failed for " + uuid, e);
        }
        return claims;
    }

    protected void saveClaimsBatchToDb(@NotNull UUID uuid, @NotNull Map<String, Integer> claimsToSave) {
        String sql = "INSERT INTO " + TABLE + " (uuid, collection_key, claimed_tier) VALUES (?,?,?) " +
                "ON DUPLICATE KEY UPDATE claimed_tier=GREATEST(claimed_tier, VALUES(claimed_tier))";
        try (Connection connection = connect()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (Map.Entry<String, Integer> entry : claimsToSave.entrySet()) {
                    String key = entry.getKey();
                    int tier = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
                    if (key == null || key.isBlank() || tier <= 0) {
                        continue;
                    }
                    ps.setString(1, uuid.toString());
                    ps.setString(2, key);
                    ps.setInt(3, tier);
                    ps.addBatch();
                }
                ps.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "CollectionDataRepository batch save claims failed for " + uuid, e);
        }
    }

    protected void deleteClaimsFromDb(@NotNull UUID uuid, @Nullable String keyPrefix) {
        boolean filtered = keyPrefix != null && !keyPrefix.isBlank();
        String sql = filtered
                ? "DELETE FROM " + TABLE + " WHERE uuid=? AND collection_key LIKE ? ESCAPE '\\\\'"
                : "DELETE FROM " + TABLE + " WHERE uuid=?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            if (filtered) {
                ps.setString(2, escapeLike(keyPrefix) + ":%");
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "CollectionDataRepository delete claims failed for " + uuid, e);
        }
    }

    protected @NotNull List<LeaderboardEntry> loadLeaderboardFromDb(@Nullable CollectionInfoGui.Category category, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT uuid, SUM(amount) AS total_amount FROM player_collection_progress");
        List<Object> params = new ArrayList<>();
        if (category != null) {
            sql.append(" WHERE collection_key LIKE ? ESCAPE '\\\\'");
            params.add(escapeLike(category.pageId()) + ":%");
        }
        sql.append(" GROUP BY uuid ORDER BY total_amount DESC, uuid ASC LIMIT ?");
        params.add(Math.max(1, limit));

        List<LeaderboardEntry> entries = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < params.size(); index++) {
                Object value = params.get(index);
                if (value instanceof String text) {
                    ps.setString(index + 1, text);
                } else if (value instanceof Integer number) {
                    ps.setInt(index + 1, number);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = parseUuid(rs.getString("uuid"));
                    long total = Math.max(0L, rs.getLong("total_amount"));
                    if (uuid != null && total > 0L) {
                        entries.add(new LeaderboardEntry(uuid, total));
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "CollectionDataRepository load leaderboard failed", e);
        }
        return entries;
    }

    private static long sumCategoryProgress(@Nullable Map<String, Long> progress,
                                            @Nullable CollectionInfoGui.Category category) {
        if (progress == null || progress.isEmpty()) {
            return 0L;
        }
        if (category == null) {
            long sum = 0L;
            for (Long value : progress.values()) {
                if (value != null && value > 0L) {
                    sum += value;
                }
            }
            return sum;
        }
        long sum = 0L;
        for (CollectionInfoGui.CollectionItem item : category.items()) {
            long value = progress.getOrDefault(CollectionInfoGui.collectionKey(category, item.material()), 0L);
            if (value > 0L) {
                sum += value;
            }
        }
        return sum;
    }

    private void runAsync(@NotNull Runnable task) {
        if (plugin != null && plugin.isEnabled() && Bukkit.getServer() != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        } else {
            task.run();
        }
    }

    private void runSync(@NotNull Runnable task) {
        if (plugin != null && plugin.isEnabled() && Bukkit.getServer() != null) {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        } else {
            task.run();
        }
    }

    private Connection connect() throws SQLException {
        return MySqlConnectionFactory.connect();
    }

    private void init() {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                "uuid VARCHAR(36) NOT NULL," +
                "collection_key VARCHAR(96) NOT NULL," +
                "claimed_tier INT NOT NULL DEFAULT 0," +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "PRIMARY KEY (uuid, collection_key)" +
                ")";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "CollectionDataRepository init failed", e);
        }
    }

    @Nullable
    private static UUID parseUuid(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @NotNull
    private static String escapeLike(@NotNull String raw) {
        return raw
                .replace("\\", "\\\\")
                .replace("_", "\\_")
                .replace("%", "\\%");
    }

    private record CachedLeaderboard(@NotNull List<LeaderboardEntry> entries, long loadedAtMs) {
    }

    record LeaderboardEntry(@NotNull UUID uuid, long totalAmount) {
    }
}
