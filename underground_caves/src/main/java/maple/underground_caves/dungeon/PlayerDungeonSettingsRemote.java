package maple.underground_caves.dungeon;

import maple.underground_state.db.MySqlConnectionFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 遠端 MySQL（underground_caves schema）儲存：常用職業、是否開啟長期準備。
 * 常用職業為英文字：HEALER / MAGE / WARRIOR / ARCHER / TANK。
 * 長期準備（自動準備偏好）持久保存，開門後不清除；下一场地牢仍從 DB 讀取。
 * 凭据集中在主插件 underground_state，连线一律經 {@link MySqlConnectionFactory#connect(String)} 取得。
 */
public final class PlayerDungeonSettingsRemote {
    private static final String TABLE = "player_dungeon_settings";
    private static final String[] VALID_CLASSES = {"HEALER", "MAGE", "WARRIOR", "ARCHER", "TANK"};

    private final Logger logger;

    /**
     * @param dataFolder 保留參數以相容呼叫端；凭据已集中至主插件，不再讀取本地設定檔。
     */
    public PlayerDungeonSettingsRemote(File dataFolder, Logger logger) {
        this.logger = logger != null ? logger : Logger.getAnonymousLogger();
    }

    /** 確保遠端 MySQL 表存在 */
    public void ensureTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                "player_uuid VARCHAR(36) PRIMARY KEY," +
                "preferred_class VARCHAR(20) DEFAULT NULL," +
                "long_term_ready TINYINT(1) NOT NULL DEFAULT 0" +
                ")";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            logDatabaseError("PlayerDungeonSettingsRemote ensureTable failed", e);
        }
    }

    private Connection connect() throws SQLException {
        return MySqlConnectionFactory.connect("caves");
    }

    private void logDatabaseError(String context, SQLException e) {
        logger.log(Level.WARNING, context, e);
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("Access denied")) {
            logger.warning("[underground_caves] MySQL 連線被拒：請檢查主插件 underground_state 的集中凭据（build-secrets.cmd 中的 caves 帳密），以及 MySQL 是否允許該使用者從此主機連線（例如 GRANT ... TO 'user'@'%'）。");
        }
    }

    /** 取得常用職業（HEALER/MAGE/WARRIOR/ARCHER/TANK），無則 null */
    public String getPreferredClass(UUID playerUuid) {
        if (playerUuid == null) return null;
        String sql = "SELECT preferred_class FROM " + TABLE + " WHERE player_uuid=?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String s = rs.getString(1);
                    if (s != null && !s.isBlank() && isValidClass(s)) return s.trim().toUpperCase(java.util.Locale.ROOT);
                }
            }
        } catch (SQLException e) {
            logDatabaseError("PlayerDungeonSettingsRemote getPreferredClass failed", e);
        }
        return null;
    }

    /** 設定常用職業（HEALER/MAGE/WARRIOR/ARCHER/TANK） */
    public void setPreferredClass(UUID playerUuid, String role) {
        if (playerUuid == null) return;
        if (role != null && !isValidClass(role)) return;
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + TABLE + " (player_uuid, preferred_class, long_term_ready) VALUES (?,?,0) " +
                        "ON DUPLICATE KEY UPDATE preferred_class=?")) {
            String val = role != null ? role.trim().toUpperCase(java.util.Locale.ROOT) : null;
            ps.setString(1, playerUuid.toString());
            ps.setString(2, val);
            ps.setString(3, val);
            ps.executeUpdate();
        } catch (SQLException e) {
            logDatabaseError("PlayerDungeonSettingsRemote setPreferredClass failed", e);
        }
    }

    private static boolean isValidClass(String s) {
        if (s == null || s.isBlank()) return false;
        String u = s.trim().toUpperCase(java.util.Locale.ROOT);
        for (String v : VALID_CLASSES) if (v.equals(u)) return true;
        return false;
    }

    /** 是否開啟長期準備 */
    public boolean isLongTermReady(UUID playerUuid) {
        if (playerUuid == null) return false;
        String sql = "SELECT long_term_ready FROM " + TABLE + " WHERE player_uuid=?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) != 0;
            }
        } catch (SQLException e) {
            logDatabaseError("PlayerDungeonSettingsRemote isLongTermReady failed", e);
            return false;
        }
    }

    /** 設定長期準備（UI 切換時寫入 MySQL） */
    public void setLongTermReady(UUID playerUuid, boolean ready) {
        if (playerUuid == null) return;
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + TABLE + " (player_uuid, preferred_class, long_term_ready) VALUES (?, NULL, ?) " +
                        "ON DUPLICATE KEY UPDATE long_term_ready=VALUES(long_term_ready)")) {
            ps.setString(1, playerUuid.toString());
            ps.setInt(2, ready ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            logDatabaseError("PlayerDungeonSettingsRemote setLongTermReady failed", e);
        }
    }

    /**
     * 將多名玩家的長期準備設為關閉（寫入 DB）。遊戲流程已不再於開門時呼叫；
     * 保留供管理指令或未來需求使用。
     */
    public void clearLongTermReady(List<UUID> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) return;
        for (UUID id : playerIds) {
            setLongTermReady(id, false);
        }
    }
}
