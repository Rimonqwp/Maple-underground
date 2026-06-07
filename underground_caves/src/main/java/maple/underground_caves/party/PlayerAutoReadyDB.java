package maple.underground_caves.party;

import maple.underground_state.db.MySqlConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MySQL 儲存玩家的自動準備設定。
 * 資料庫: skyblock, 表: dungeon_player_auto_ready。
 * 凭据集中在主插件 underground_state，连线一律经 {@link MySqlConnectionFactory#connect(String)} 取得。
 */
public final class PlayerAutoReadyDB {
    private static final String TABLE = "dungeon_player_auto_ready";

    private final Logger logger;

    public PlayerAutoReadyDB(Logger logger) {
        this.logger = logger != null ? logger : Logger.getAnonymousLogger();
        init();
    }

    private Connection connect() throws SQLException {
        return MySqlConnectionFactory.connect("skyblock");
    }

    private void init() {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                             "player_uuid VARCHAR(36) NOT NULL PRIMARY KEY," +
                             "auto_ready TINYINT(1) NOT NULL DEFAULT 0)")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "PlayerAutoReadyDB init failed", e);
        }
    }

    /** 取得玩家是否開啟自動準備 */
    public boolean isAutoReady(UUID playerId) {
        if (playerId == null) return false;
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT auto_ready FROM " + TABLE + " WHERE player_uuid=?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) != 0;
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "PlayerAutoReadyDB isAutoReady failed", e);
            return false;
        }
    }

    /** 設定玩家自動準備 */
    public void setAutoReady(UUID playerId, boolean autoReady) {
        if (playerId == null) return;
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + TABLE + " (player_uuid, auto_ready) VALUES (?,?) " +
                             "ON DUPLICATE KEY UPDATE auto_ready=?")) {
            ps.setString(1, playerId.toString());
            ps.setInt(2, autoReady ? 1 : 0);
            ps.setInt(3, autoReady ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "PlayerAutoReadyDB setAutoReady failed", e);
        }
    }
}
