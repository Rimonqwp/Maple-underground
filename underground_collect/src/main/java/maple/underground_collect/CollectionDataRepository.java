package maple.underground_collect;

import maple.underground_state.db.MySqlConnectionFactory;
import maple.underground_state.gui.CollectionInfoGui;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

final class CollectionDataRepository {

    private static final String TABLE = "player_collection_reward_claims";

    private final Logger logger;

    CollectionDataRepository(@NotNull Logger logger) {
        this.logger = logger;
        MySqlConnectionFactory.logTarget(logger);
        init();
    }

    @NotNull
    Map<String, Integer> loadClaims(@NotNull UUID uuid) {
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

    void saveClaim(@NotNull UUID uuid, @NotNull String collectionKey, int claimedTier) {
        String sql = "INSERT INTO " + TABLE + " (uuid, collection_key, claimed_tier) VALUES (?,?,?) " +
                "ON DUPLICATE KEY UPDATE claimed_tier=VALUES(claimed_tier)";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, collectionKey);
            ps.setInt(3, Math.max(0, claimedTier));
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "CollectionDataRepository save claim failed for " + uuid + " / " + collectionKey, e);
        }
    }

    @NotNull
    List<LeaderboardEntry> loadLeaderboard(@Nullable CollectionInfoGui.Category category, int limit) {
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

    record LeaderboardEntry(@NotNull UUID uuid, long totalAmount) {
    }
}
