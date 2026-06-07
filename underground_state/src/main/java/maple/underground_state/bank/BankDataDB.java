package maple.underground_state.bank;

import maple.underground_state.db.MySqlConnectionFactory;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BankDataDB {

    private static final String ACCOUNT_TABLE = "player_bank_accounts";
    private static final String LOG_TABLE = "player_bank_logs";

    private final Logger logger;

    public BankDataDB(File dataFolder, Logger logger) {
        this.logger = logger != null ? logger : Logger.getAnonymousLogger();
        MySqlConnectionFactory.logTarget(this.logger);
        init();
    }

    private Connection connect() throws SQLException {
        return MySqlConnectionFactory.connect();
    }

    private void init() {
        try (Connection connection = connect()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + ACCOUNT_TABLE + " (" +
                            "uuid VARCHAR(36) PRIMARY KEY," +
                            "balance BIGINT NOT NULL DEFAULT 0," +
                            "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                            ")")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + LOG_TABLE + " (" +
                            "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                            "uuid VARCHAR(36) NOT NULL," +
                            "action VARCHAR(16) NOT NULL," +
                            "amount BIGINT NOT NULL," +
                            "balance_after BIGINT NOT NULL," +
                            "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "INDEX idx_player_time (uuid, created_at)" +
                            ")")) {
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "BankDataDB init failed", e);
        }
    }

    public long balance(@NotNull UUID uuid) {
        String sql = "SELECT balance FROM " + ACCOUNT_TABLE + " WHERE uuid=?";
        try (Connection connection = connect()) {
            ensureAccount(connection, uuid);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Math.max(0L, rs.getLong("balance"));
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "BankDataDB balance failed for " + uuid, e);
        }
        return 0L;
    }

    public @NotNull BankChangeResult changeBalance(@NotNull UUID uuid, long delta, @NotNull BankAction action) {
        if (delta == 0L) {
            return BankChangeResult.success(balance(uuid));
        }

        Connection connection = null;
        try {
            connection = connect();
            connection.setAutoCommit(false);
            ensureAccount(connection, uuid);

            long current;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT balance FROM " + ACCOUNT_TABLE + " WHERE uuid=? FOR UPDATE")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    current = rs.next() ? Math.max(0L, rs.getLong("balance")) : 0L;
                }
            }

            long updated = current + delta;
            if (updated < 0L) {
                connection.rollback();
                return BankChangeResult.insufficient(current);
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + ACCOUNT_TABLE + " SET balance=? WHERE uuid=?")) {
                ps.setLong(1, updated);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + LOG_TABLE + " (uuid, action, amount, balance_after) VALUES (?,?,?,?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, action.name());
                ps.setLong(3, Math.abs(delta));
                ps.setLong(4, updated);
                ps.executeUpdate();
            }

            connection.commit();
            return BankChangeResult.success(updated);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "BankDataDB changeBalance failed for " + uuid, e);
            rollbackQuietly(connection);
            return BankChangeResult.failure();
        } finally {
            closeQuietly(connection);
        }
    }

    public int countLogs(@NotNull UUID uuid) {
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + LOG_TABLE + " WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Math.max(0, rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "BankDataDB countLogs failed for " + uuid, e);
        }
        return 0;
    }

    public @NotNull List<BankLogEntry> loadLogs(@NotNull UUID uuid, int offset, int limit) {
        List<BankLogEntry> entries = new ArrayList<>();
        String sql = "SELECT action, amount, balance_after, created_at FROM " + LOG_TABLE +
                " WHERE uuid=? ORDER BY id DESC LIMIT ? OFFSET ?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, Math.max(0, limit));
            ps.setInt(3, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BankAction action = parseAction(rs.getString("action"));
                    long amount = Math.max(0L, rs.getLong("amount"));
                    long balanceAfter = Math.max(0L, rs.getLong("balance_after"));
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    entries.add(new BankLogEntry(
                            action,
                            amount,
                            balanceAfter,
                            createdAt == null ? Instant.EPOCH : createdAt.toInstant()
                    ));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "BankDataDB loadLogs failed for " + uuid, e);
        }
        return entries;
    }

    private static @NotNull BankAction parseAction(String raw) {
        if (raw == null) {
            return BankAction.DEPOSIT;
        }
        try {
            return BankAction.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return BankAction.DEPOSIT;
        }
    }

    private static void ensureAccount(@NotNull Connection connection, @NotNull UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT IGNORE INTO " + ACCOUNT_TABLE + " (uuid) VALUES (?)")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    private static void rollbackQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
