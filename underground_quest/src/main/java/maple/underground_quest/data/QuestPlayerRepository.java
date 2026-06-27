package maple.underground_quest.data;

import maple.underground_state.db.MySqlConnectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 玩家任務進度資料庫讀寫層（純 DB CRUD）。
 *
 * <p>一律走 underground_state 的 {@link MySqlConnectionFactory}，使用 "caves" 資料源。
 * 表 player_quest_progress：uuid + quest_id + objective_id 為主鍵；claimed 欄位標記獎勵是否已領。
 * 表 player_quest_tracked：uuid 為主鍵，記錄玩家正在追蹤的單一任務。
 */
public final class QuestPlayerRepository {

    private static final String TABLE = "player_quest_progress";
    private static final String TRACKED_TABLE = "player_quest_tracked";

    private final Logger logger;

    public QuestPlayerRepository(@NotNull Logger logger) {
        this.logger = logger;
        MySqlConnectionFactory.logTarget(logger);
        init();
    }

    /** 載入某玩家所有任務的各目標進度。回傳 questId -> (objectiveId -> amount)。 */
    @NotNull
    public Map<String, Map<String, Integer>> loadProgress(@NotNull UUID uuid) {
        Map<String, Map<String, Integer>> result = new LinkedHashMap<>();
        String sql = "SELECT quest_id, objective_id, amount FROM " + TABLE + " WHERE uuid=? AND claimed=0";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String questId = rs.getString("quest_id");
                    String objId = rs.getString("objective_id");
                    int amount = Math.max(0, rs.getInt("amount"));
                    result.computeIfAbsent(questId, k -> new LinkedHashMap<>()).put(objId, amount);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "QuestPlayerRepository loadProgress failed for " + uuid, e);
        }
        return result;
    }

    /** 載入玩家已領取獎勵的任務清單。 */
    @NotNull
    public Set<String> loadClaimed(@NotNull UUID uuid) {
        Set<String> claimed = new HashSet<>();
        String sql = "SELECT DISTINCT quest_id FROM " + TABLE + " WHERE uuid=? AND claimed=1";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String questId = rs.getString("quest_id");
                    if (questId != null && !questId.isBlank()) {
                        claimed.add(questId);
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "QuestPlayerRepository loadClaimed failed for " + uuid, e);
        }
        return claimed;
    }

    /** 更新單一目標進度（upsert）。 */
    public void saveObjective(@NotNull UUID uuid, @NotNull String questId, @NotNull String objectiveId, int amount) {
        String sql = "INSERT INTO " + TABLE + " (uuid, quest_id, objective_id, amount, claimed) VALUES (?,?,?,?,0) " +
                "ON DUPLICATE KEY UPDATE amount=VALUES(amount)";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, questId);
            ps.setString(3, objectiveId);
            ps.setInt(4, Math.max(0, amount));
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "QuestPlayerRepository saveObjective failed for " + uuid + " / " + questId, e);
        }
    }

    /** 標記任務獎勵已領取。 */
    public void markClaimed(@NotNull UUID uuid, @NotNull String questId) {
        String sql = "UPDATE " + TABLE + " SET claimed=1 WHERE uuid=? AND quest_id=?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, questId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "QuestPlayerRepository markClaimed failed for " + uuid + " / " + questId, e);
        }
    }

    /** 判斷任務獎勵是否已領取。 */
    public boolean isClaimed(@NotNull UUID uuid, @NotNull String questId) {
        String sql = "SELECT 1 FROM " + TABLE + " WHERE uuid=? AND quest_id=? AND claimed=1 LIMIT 1";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, questId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "QuestPlayerRepository isClaimed failed for " + uuid + " / " + questId, e);
            return false;
        }
    }

    /** 載入玩家正在追蹤的任務 ID（可為 null）。 */
    @Nullable
    public String loadTracked(@NotNull UUID uuid) {
        String sql = "SELECT quest_id FROM " + TRACKED_TABLE + " WHERE uuid=? LIMIT 1";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("quest_id");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "QuestPlayerRepository loadTracked failed for " + uuid, e);
        }
        return null;
    }

    /** 設定玩家追蹤的任務；questId 為 null 時清除追蹤。 */
    public void saveTracked(@NotNull UUID uuid, @Nullable String questId) {
        if (questId == null || questId.isBlank()) {
            String sql = "DELETE FROM " + TRACKED_TABLE + " WHERE uuid=?";
            try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "QuestPlayerRepository clearTracked failed for " + uuid, e);
            }
            return;
        }
        String sql = "INSERT INTO " + TRACKED_TABLE + " (uuid, quest_id) VALUES (?,?) " +
                "ON DUPLICATE KEY UPDATE quest_id=VALUES(quest_id)";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, questId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "QuestPlayerRepository saveTracked failed for " + uuid, e);
        }
    }

    /** 重設玩家某任務的進度（管理用）。 */
    public void resetQuest(@NotNull UUID uuid, @NotNull String questId) {
        String sql = "DELETE FROM " + TABLE + " WHERE uuid=? AND quest_id=?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, questId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "QuestPlayerRepository resetQuest failed for " + uuid + " / " + questId, e);
        }
    }

    /** 重設玩家全部任務進度（管理用）。 */
    public void resetAll(@NotNull UUID uuid) {
        String sql = "DELETE FROM " + TABLE + " WHERE uuid=?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "QuestPlayerRepository resetAll failed for " + uuid, e);
        }
    }

    private void init() {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                "uuid VARCHAR(36) NOT NULL," +
                "quest_id VARCHAR(96) NOT NULL," +
                "objective_id VARCHAR(96) NOT NULL," +
                "amount INT NOT NULL DEFAULT 0," +
                "claimed TINYINT NOT NULL DEFAULT 0," +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "PRIMARY KEY (uuid, quest_id, objective_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "QuestPlayerRepository init table failed", e);
        }

        String trackedSql = "CREATE TABLE IF NOT EXISTS " + TRACKED_TABLE + " (" +
                "uuid VARCHAR(36) NOT NULL," +
                "quest_id VARCHAR(96) NOT NULL," +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "PRIMARY KEY (uuid)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(trackedSql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "QuestPlayerRepository init tracked table failed", e);
        }
    }

    private Connection connect() throws SQLException {
        return MySqlConnectionFactory.connect("caves");
    }
}