package maple.underground_caves.dungeon;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite 儲存地牢組隊的門鑰匙數量與組隊經驗（地底洞窟 + 5 職業）。
 * 門鑰匙與 (world_name, leader_uuid) 綁定；經驗按隊伍內每人存 (world_name, player_uuid)。
 */
public final class DungeonRoomRewardDB {
    private static final String TABLE = "dungeon_party_door_keys";
    private static final String TABLE_EXPERIENCE = "dungeon_party_experience";
    private static final int EXP_COUNT = 6;

    private final File dbFile;
    private final Logger logger;

    public DungeonRoomRewardDB(File dataFolder, Logger logger) {
        this.dbFile = new File(dataFolder, "dungeon_rewards.db");
        this.logger = logger != null ? logger : Logger.getAnonymousLogger();
        init();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
    }

    private void init() {
        try (Connection c = connect()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                            "world_name TEXT NOT NULL," +
                            "leader_uuid TEXT NOT NULL," +
                            "door_keys INTEGER NOT NULL DEFAULT 0," +
                            "PRIMARY KEY (world_name, leader_uuid))")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + TABLE_EXPERIENCE + " (" +
                            "world_name TEXT NOT NULL," +
                            "player_uuid TEXT NOT NULL," +
                            "exp_underground_caves INTEGER NOT NULL DEFAULT 0," +
                            "exp_profession_1 INTEGER NOT NULL DEFAULT 0," +
                            "exp_profession_2 INTEGER NOT NULL DEFAULT 0," +
                            "exp_profession_3 INTEGER NOT NULL DEFAULT 0," +
                            "exp_profession_4 INTEGER NOT NULL DEFAULT 0," +
                            "exp_profession_5 INTEGER NOT NULL DEFAULT 0," +
                            "PRIMARY KEY (world_name, player_uuid))")) {
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "DungeonRoomRewardDB init failed", e);
        }
    }

    /** 取得該組隊在該地牢世界的門鑰匙數量 */
    public int getDoorKeys(String worldName, UUID partyLeader) {
        if (worldName == null || worldName.isBlank() || partyLeader == null) return 0;
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT door_keys FROM " + TABLE + " WHERE world_name=? AND leader_uuid=?")) {
            ps.setString(1, worldName);
            ps.setString(2, partyLeader.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "DungeonRoomRewardDB getDoorKeys failed", e);
            return 0;
        }
    }

    /** 增加該組隊的門鑰匙數量 */
    void addDoorKey(String worldName, UUID partyLeader) {
        if (worldName == null || worldName.isBlank() || partyLeader == null) return;
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + TABLE + " (world_name, leader_uuid, door_keys) VALUES (?,?,1) " +
                             "ON CONFLICT(world_name, leader_uuid) DO UPDATE SET door_keys=door_keys+1")) {
            ps.setString(1, worldName);
            ps.setString(2, partyLeader.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "DungeonRoomRewardDB addDoorKey failed", e);
        }
    }

    /** 消耗 1 把門鑰匙；若鑰匙為 0 則不扣除，回傳 false */
    public boolean consumeDoorKey(String worldName, UUID partyLeader) {
        if (worldName == null || worldName.isBlank() || partyLeader == null) return false;
        if (getDoorKeys(worldName, partyLeader) <= 0) return false;
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE " + TABLE + " SET door_keys = door_keys - 1 WHERE world_name=? AND leader_uuid=? AND door_keys > 0")) {
            ps.setString(1, worldName);
            ps.setString(2, partyLeader.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "DungeonRoomRewardDB consumeDoorKey failed", e);
            return false;
        }
    }

    /** 地牢結束時可呼叫以清除該世界的組隊資料（可選） */
    void clearWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) return;
        try (Connection c = connect()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + TABLE + " WHERE world_name=?")) {
                ps.setString(1, worldName);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + TABLE_EXPERIENCE + " WHERE world_name=?")) {
                ps.setString(1, worldName);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "DungeonRoomRewardDB clearWorld failed", e);
        }
    }

    // ---------- 組隊經驗（地底洞窟 + 5 職業）：按隊伍內每人存 (world_name, player_uuid) ----------

    /** 儲存該玩家在該地牢世界的 6 項經驗（地底洞窟, 職業1..5）。exp 長度至少為 6。 */
    public void savePlayerExperience(String worldName, UUID playerUuid, long[] exp) {
        if (worldName == null || worldName.isBlank() || playerUuid == null || exp == null || exp.length < EXP_COUNT) return;
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR REPLACE INTO " + TABLE_EXPERIENCE + " (world_name, player_uuid, exp_underground_caves, exp_profession_1, exp_profession_2, exp_profession_3, exp_profession_4, exp_profession_5) VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setString(1, worldName);
            ps.setString(2, playerUuid.toString());
            for (int i = 0; i < EXP_COUNT; i++) ps.setLong(3 + i, exp[i]);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "DungeonRoomRewardDB savePlayerExperience failed", e);
        }
    }

    /** 取得該玩家在該地牢世界的 6 項經驗 [地底洞窟, 職業1..5]，無資料則回傳全 0。 */
    public long[] getPlayerExperience(String worldName, UUID playerUuid) {
        long[] out = new long[EXP_COUNT];
        if (worldName == null || worldName.isBlank() || playerUuid == null) return out;
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT exp_underground_caves, exp_profession_1, exp_profession_2, exp_profession_3, exp_profession_4, exp_profession_5 FROM " + TABLE_EXPERIENCE + " WHERE world_name=? AND player_uuid=?")) {
            ps.setString(1, worldName);
            ps.setString(2, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    out[0] = rs.getLong("exp_underground_caves");
                    out[1] = rs.getLong("exp_profession_1");
                    out[2] = rs.getLong("exp_profession_2");
                    out[3] = rs.getLong("exp_profession_3");
                    out[4] = rs.getLong("exp_profession_4");
                    out[5] = rs.getLong("exp_profession_5");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "DungeonRoomRewardDB getPlayerExperience failed", e);
        }
        return out;
    }
}
