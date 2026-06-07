package maple.underground_caves.dungeon;

import maple.underground_state.db.MySqlConnectionFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stores dungeon and class experience in the underground_state MySQL database.
 * Existing data from the old underground_caves MySQL table is migrated lazily
 * on read so players do not lose previously earned progress.
 *
 * <p>所有连线（含 legacy 迁移读取）皆经主插件集中工厂 {@link MySqlConnectionFactory} 取得：
 * 目前进度走 connect()（state 库），legacy 旧表走 connect("caves")。</p>
 */
public final class ProfessionExperienceRemote {
    private static final String PROGRESS_TABLE = "player_skill_progress";
    private static final String OLD_TABLE = "player_profession_experience";
    private static final int EXP_COUNT = 6;

    private static final String[] EXP_COLUMNS = {
            "exp_underground_caves", "exp_profession_1", "exp_profession_2",
            "exp_profession_3", "exp_profession_4", "exp_profession_5"
    };

    private final Logger logger;
    private volatile boolean progressSchemaReady;
    private final java.util.Map<UUID, long[]> cache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * @param dataFolder 保留參數以相容呼叫端；凭据已集中至主插件，不再讀取本地設定檔。
     */
    public ProfessionExperienceRemote(File dataFolder, Logger logger) {
        this.logger = logger != null ? logger : Logger.getAnonymousLogger();
        MySqlConnectionFactory.logTarget(this.logger);
    }

    public void ensureRemoteTable() {
        ensureProgressSchema();
    }

    public void addExperience(UUID playerUuid, int expIndex, long amount) {
        if (playerUuid == null || amount <= 0 || expIndex < 0 || expIndex >= EXP_COUNT) {
            return;
        }
        ensureProgressSchema();
        String col = EXP_COLUMNS[expIndex];
        String sql = "INSERT INTO " + PROGRESS_TABLE + " (uuid, " + col + ") VALUES (?,?) " +
                "ON DUPLICATE KEY UPDATE " + col + " = " + col + " + ?";
        try (Connection c = connectState(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setLong(2, amount);
            ps.setLong(3, amount);
            ps.executeUpdate();
            long[] updated = fetchProgressByPlayer(playerUuid);
            if (updated != null) {
                cache.put(playerUuid, updated.clone());
            }
        } catch (Exception e) {
            logDatabaseError("ProfessionExperienceRemote addExperience failed", e);
        }
    }

    public long[] fetchByPlayer(UUID playerUuid) {
        long[] empty = new long[EXP_COUNT];
        if (playerUuid == null) {
            return empty;
        }
        long[] cached = cache.get(playerUuid);
        if (cached != null) {
            return cached.clone();
        }

        long[] stateData = fetchProgressByPlayer(playerUuid);
        if (stateData != null && !isAllZero(stateData)) {
            cache.put(playerUuid, stateData.clone());
            return stateData;
        }

        long[] oldStateData = fetchOldStateByPlayer(playerUuid);
        if (oldStateData != null && !isAllZero(oldStateData)) {
            saveExact(playerUuid, oldStateData);
            cache.put(playerUuid, oldStateData.clone());
            return oldStateData;
        }

        long[] legacyData = fetchLegacyByPlayer(playerUuid);
        if (legacyData != null && !isAllZero(legacyData)) {
            saveExact(playerUuid, legacyData);
            cache.put(playerUuid, legacyData.clone());
            return legacyData;
        }

        long[] result = stateData != null ? stateData : empty;
        cache.put(playerUuid, result.clone());
        return result;
    }

    public long[] getCachedByPlayer(UUID playerUuid) {
        long[] empty = new long[EXP_COUNT];
        if (playerUuid == null) {
            return empty;
        }
        long[] cached = cache.get(playerUuid);
        return cached != null ? cached.clone() : empty;
    }

    public long[] refreshByPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return new long[EXP_COUNT];
        }
        cache.remove(playerUuid);
        return fetchByPlayer(playerUuid);
    }

    private Connection connectState() throws SQLException {
        return MySqlConnectionFactory.connect();
    }

    private Connection connectLegacy() throws SQLException {
        return MySqlConnectionFactory.connect("caves");
    }

    private long[] fetchProgressByPlayer(UUID playerUuid) {
        ensureProgressSchema();
        String sql = "SELECT exp_underground_caves, exp_profession_1, exp_profession_2, exp_profession_3, exp_profession_4, exp_profession_5 FROM "
                + PROGRESS_TABLE + " WHERE uuid=?";
        try (Connection c = connectState();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return readExperienceRow(rs);
            }
        } catch (Exception e) {
            logDatabaseError("ProfessionExperienceRemote fetchByPlayer failed from player_skill_progress for " + playerUuid, e);
            return null;
        }
    }

    private long[] fetchOldStateByPlayer(UUID playerUuid) {
        String sql = "SELECT exp_underground_caves, exp_profession_1, exp_profession_2, exp_profession_3, exp_profession_4, exp_profession_5 FROM "
                + OLD_TABLE + " WHERE player_uuid=?";
        try (Connection c = connectState();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return readExperienceRow(rs);
            }
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null || !message.toLowerCase(java.util.Locale.ROOT).contains("doesn't exist")) {
                logDatabaseError("ProfessionExperienceRemote fetchByPlayer failed from old " + OLD_TABLE + " for " + playerUuid, e);
            }
            return null;
        }
    }

    private long[] fetchLegacyByPlayer(UUID playerUuid) {
        String sql = "SELECT exp_underground_caves, exp_profession_1, exp_profession_2, exp_profession_3, exp_profession_4, exp_profession_5 FROM "
                + OLD_TABLE + " WHERE player_uuid=?";
        try (Connection c = connectLegacy();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return readExperienceRow(rs);
            }
        } catch (Exception e) {
            logDatabaseError("ProfessionExperienceRemote fetchByPlayer failed from legacy underground_caves for " + playerUuid, e);
            return null;
        }
    }

    private static long[] readExperienceRow(ResultSet rs) throws SQLException {
        if (!rs.next()) {
            return null;
        }
        long[] out = new long[EXP_COUNT];
        for (int i = 0; i < EXP_COUNT; i++) {
            out[i] = rs.getLong(EXP_COLUMNS[i]);
        }
        return out;
    }

    private void saveExact(UUID playerUuid, long[] values) {
        if (playerUuid == null || values == null || values.length < EXP_COUNT) {
            return;
        }
        ensureProgressSchema();
        String sql = "INSERT INTO " + PROGRESS_TABLE + " (" +
                "uuid, exp_underground_caves, exp_profession_1, exp_profession_2, exp_profession_3, exp_profession_4, exp_profession_5" +
                ") VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE " +
                "exp_underground_caves=VALUES(exp_underground_caves), " +
                "exp_profession_1=VALUES(exp_profession_1), " +
                "exp_profession_2=VALUES(exp_profession_2), " +
                "exp_profession_3=VALUES(exp_profession_3), " +
                "exp_profession_4=VALUES(exp_profession_4), " +
                "exp_profession_5=VALUES(exp_profession_5)";
        try (Connection c = connectState(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            for (int i = 0; i < EXP_COUNT; i++) {
                ps.setLong(2 + i, values[i]);
            }
            ps.executeUpdate();
            cache.put(playerUuid, values.clone());
        } catch (Exception e) {
            logDatabaseError("ProfessionExperienceRemote saveExact failed for " + playerUuid, e);
        }
    }

    private void ensureProgressSchema() {
        if (progressSchemaReady) {
            return;
        }
        synchronized (this) {
            if (progressSchemaReady) {
                return;
            }
            String sql = "CREATE TABLE IF NOT EXISTS " + PROGRESS_TABLE + " (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "exp_underground_caves BIGINT NOT NULL DEFAULT 0," +
                    "exp_profession_1 BIGINT NOT NULL DEFAULT 0," +
                    "exp_profession_2 BIGINT NOT NULL DEFAULT 0," +
                    "exp_profession_3 BIGINT NOT NULL DEFAULT 0," +
                    "exp_profession_4 BIGINT NOT NULL DEFAULT 0," +
                    "exp_profession_5 BIGINT NOT NULL DEFAULT 0" +
                    ")";
            try (Connection c = connectState(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.executeUpdate();
                for (String column : EXP_COLUMNS) {
                    addColumnIfMissing(c, column);
                }
                progressSchemaReady = true;
            } catch (Exception e) {
                logDatabaseError("ProfessionExperienceRemote ensure player_skill_progress columns failed", e);
            }
        }
    }

    private void addColumnIfMissing(Connection connection, String column) {
        try (PreparedStatement ps = connection.prepareStatement(
                "ALTER TABLE " + PROGRESS_TABLE + " ADD COLUMN " + column + " BIGINT NOT NULL DEFAULT 0")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            String message = e.getMessage();
            if (message == null || !message.toLowerCase(java.util.Locale.ROOT).contains("duplicate column")) {
                logDatabaseError("ProfessionExperienceRemote add column " + column + " failed", e);
            }
        }
    }

    private static boolean isAllZero(long[] values) {
        if (values == null) {
            return true;
        }
        for (long value : values) {
            if (value != 0L) {
                return false;
            }
        }
        return true;
    }

    private void logDatabaseError(String context, Exception e) {
        logger.log(Level.WARNING, context, e);
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("Access denied")) {
            logger.warning("[underground_caves] MySQL access denied. Check the centralized credentials in underground_state (build-secrets.cmd: state/caves accounts).");
        }
    }
}
