package maple.underground_state.collection;

import maple.underground_state.db.MySqlConnectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CollectionProgressDB {

    private static final String TABLE = "player_collection_progress";

    private final Logger logger;

    public CollectionProgressDB(File dataFolder, Logger logger) {
        this.logger = logger != null ? logger : Logger.getAnonymousLogger();
        MySqlConnectionFactory.logTarget(this.logger);
        init();
    }

    private Connection connect() throws SQLException {
        return MySqlConnectionFactory.connect();
    }

    private void init() {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                "uuid VARCHAR(36) NOT NULL," +
                "collection_key VARCHAR(96) NOT NULL," +
                "amount BIGINT NOT NULL DEFAULT 0," +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "PRIMARY KEY (uuid, collection_key)" +
                ")";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "CollectionProgressDB init failed", e);
        }
    }

    public @NotNull Map<String, Long> load(@NotNull UUID uuid) {
        Map<String, Long> data = new HashMap<>();
        String sql = "SELECT collection_key, amount FROM " + TABLE + " WHERE uuid=?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("collection_key");
                    long amount = Math.max(0L, rs.getLong("amount"));
                    if (key != null && !key.isBlank() && amount > 0L) {
                        data.put(key, amount);
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "CollectionProgressDB load failed for " + uuid, e);
        }
        return data;
    }

    public void save(@NotNull UUID uuid, @NotNull Map<String, Long> data) {
        String upsertSql = "INSERT INTO " + TABLE + " (uuid, collection_key, amount) VALUES (?,?,?) " +
                "ON DUPLICATE KEY UPDATE amount=VALUES(amount)";
        try (Connection connection = connect(); PreparedStatement upsert = connection.prepareStatement(upsertSql)) {
            for (Map.Entry<String, Long> entry : data.entrySet()) {
                String key = entry.getKey();
                long amount = entry.getValue() == null ? 0L : entry.getValue();
                if (key == null || key.isBlank() || amount <= 0L) {
                    continue;
                }
                upsert.setString(1, uuid.toString());
                upsert.setString(2, key);
                upsert.setLong(3, amount);
                upsert.addBatch();
            }
            if (!data.isEmpty()) {
                upsert.executeBatch();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "CollectionProgressDB save failed for " + uuid, e);
        }
    }

    public void deleteKey(@NotNull UUID uuid, @NotNull String collectionKey) {
        String sql = "DELETE FROM " + TABLE + " WHERE uuid=? AND collection_key=?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, collectionKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "CollectionProgressDB deleteKey failed for " + uuid + " / " + collectionKey, e);
        }
    }

    public void delete(@NotNull UUID uuid, @Nullable String keyPrefix) {
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
            logger.log(Level.WARNING, "CollectionProgressDB delete failed for " + uuid, e);
        }
    }

    private static @NotNull String escapeLike(@NotNull String raw) {
        return raw
                .replace("\\", "\\\\")
                .replace("_", "\\_")
                .replace("%", "\\%");
    }
}
