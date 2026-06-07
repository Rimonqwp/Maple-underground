package maple.underground_caves.dungeon;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 遠端 MySQL（underground_caves）儲存：常用職業、是否開啟長期準備。
 * 常用職業為英文字：HEALER / MAGE / WARRIOR / ARCHER / TANK。
 * 長期準備（自動準備偏好）持久保存，開門後不清除；下一场地牢仍從 DB 讀取。
 */
public final class PlayerDungeonSettingsRemote {
    private static final String TABLE = "player_dungeon_settings";
    private static final String[] VALID_CLASSES = {"HEALER", "MAGE", "WARRIOR", "ARCHER", "TANK"};

    private final File configFile;
    private final Logger logger;
    private String url;
    private String username;
    private String password;
    private boolean configLoaded;

    public PlayerDungeonSettingsRemote(File dataFolder, Logger logger) {
        this.configFile = new File(dataFolder, "database.yml");
        this.logger = logger != null ? logger : Logger.getAnonymousLogger();
        loadConfig();
    }

    private void loadConfig() {
        url = "jdbc:mysql://mysql.rimon12.top:3306/underground_caves";
        username = "Underground_caves";
        password = envOrDefault("UNDERGROUND_CAVES_MYSQL_PASSWORD", "");
        if (configFile != null && configFile.isFile()) {
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
                if (cfg.contains("mysql.url")) url = cfg.getString("mysql.url", url);
                if (cfg.contains("mysql.username")) username = cfg.getString("mysql.username", username);
                if (cfg.contains("mysql.password")) password = cfg.getString("mysql.password", password);
            } catch (Exception e) {
                logger.log(Level.WARNING, "PlayerDungeonSettingsRemote load config failed", e);
            }
        }
        configLoaded = true;
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    /** 確保遠端 MySQL 表存在 */
    public void ensureTable() {
        if (!configLoaded) loadConfig();
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
        return DriverManager.getConnection(url, username, password);
    }

    private void logDatabaseError(String context, SQLException e) {
        logger.log(Level.WARNING, context, e);
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("Access denied")) {
            logger.warning("[underground_caves] MySQL 連線被拒：請檢查 plugins/underground_caves/database.yml 的帳號密碼，以及 MySQL 是否允許該使用者從此主機連線（例如 GRANT ... TO 'user'@'%'）。");
        }
    }

    /** 取得常用職業（HEALER/MAGE/WARRIOR/ARCHER/TANK），無則 null */
    public String getPreferredClass(UUID playerUuid) {
        if (playerUuid == null) return null;
        if (!configLoaded) loadConfig();
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
        if (!configLoaded) loadConfig();
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
        if (!configLoaded) loadConfig();
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
        if (!configLoaded) loadConfig();
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
        if (!configLoaded) loadConfig();
        for (UUID id : playerIds) {
            setLongTermReady(id, false);
        }
    }
}
