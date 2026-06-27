package maple.underground_island;

import maple.underground_state.db.MySqlConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

final class IslandRepository {
    private static final String ISLAND_TABLE = "underground_islands";
    private static final String MEMBER_TABLE = "underground_island_members";

    private final Logger logger;

    IslandRepository(Logger logger) {
        this.logger = logger != null ? logger : Logger.getAnonymousLogger();
    }

    void init() {
        MySqlConnectionFactory.logTarget(logger);
        try (Connection connection = connect()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + ISLAND_TABLE + " (" +
                            "island_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                            "owner_uuid VARCHAR(36) NOT NULL UNIQUE," +
                            "world_name VARCHAR(96) NOT NULL UNIQUE," +
                            "center_x INT NOT NULL DEFAULT 0," +
                            "center_y INT NOT NULL DEFAULT 64," +
                            "center_z INT NOT NULL DEFAULT 0," +
                            "blocks_mined INT NOT NULL DEFAULT 0," +
                            "portal_rewarded TINYINT(1) NOT NULL DEFAULT 0," +
                            "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                            ")")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + MEMBER_TABLE + " (" +
                            "player_uuid VARCHAR(36) NOT NULL PRIMARY KEY," +
                            "island_id BIGINT NOT NULL," +
                            "member_role VARCHAR(24) NOT NULL DEFAULT 'MEMBER'," +
                            "active_world VARCHAR(96) NOT NULL," +
                            "joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "INDEX idx_island_id (island_id)," +
                            "INDEX idx_active_world (active_world)" +
                            ")")) {
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "IslandRepository init failed", e);
            throw new IllegalStateException("Failed to initialize island tables", e);
        }
    }

    EnsureIslandResult ensureIsland(UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId is null");
        }
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                IslandRecord existing = findActiveIsland(connection, playerId);
                if (existing != null) {
                    connection.commit();
                    return new EnsureIslandResult(existing, false);
                }

                String worldName = IslandWorldService.worldNameFor(playerId);
                long islandId = ensureOwnedIsland(connection, playerId, worldName);
                upsertMember(connection, playerId, islandId, "OWNER", worldName);
                IslandRecord created = findActiveIsland(connection, playerId);
                if (created == null) {
                    throw new SQLException("Created island could not be loaded for " + playerId);
                }
                connection.commit();
                return new EnsureIslandResult(created, true);
            } catch (SQLException | RuntimeException e) {
                rollback(connection);
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to ensure island for " + playerId, e);
        }
    }

    IslandRecord getActiveIsland(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        try (Connection connection = connect()) {
            return findActiveIsland(connection, playerId);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load active island for " + playerId, e);
        }
    }

    IslandRecord getOwnerIsland(UUID ownerId) {
        if (ownerId == null) {
            return null;
        }
        try (Connection connection = connect()) {
            return findOwnedIsland(connection, ownerId);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load owner island for " + ownerId, e);
        }
    }

    CoopJoinResult joinCoop(UUID playerId, UUID ownerId) {
        if (playerId == null || ownerId == null) {
            throw new IllegalArgumentException("playerId/ownerId is null");
        }
        if (playerId.equals(ownerId)) {
            throw new IllegalArgumentException("Cannot join own island");
        }
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                IslandRecord ownerIsland = findOwnedIsland(connection, ownerId);
                if (ownerIsland == null) {
                    long islandId = ensureOwnedIsland(connection, ownerId, IslandWorldService.worldNameFor(ownerId));
                    upsertMember(connection, ownerId, islandId, "OWNER", IslandWorldService.worldNameFor(ownerId));
                    ownerIsland = findOwnedIsland(connection, ownerId);
                }
                if (ownerIsland == null) {
                    throw new SQLException("Owner island could not be loaded for " + ownerId);
                }

                IslandRecord active = findActiveIsland(connection, playerId);
                if (active != null && active.islandId() == ownerIsland.islandId()) {
                    connection.commit();
                    return new CoopJoinResult(ownerIsland, null, List.of(), true);
                }

                IslandRecord ownedByPlayer = findOwnedIsland(connection, playerId);
                String deletedWorldName = null;
                List<UUID> evictedMembers = List.of();
                if (ownedByPlayer != null && ownedByPlayer.islandId() != ownerIsland.islandId()) {
                    deletedWorldName = ownedByPlayer.worldName();
                    evictedMembers = listMembers(connection, ownedByPlayer.islandId());
                    deleteIslandRows(connection, ownedByPlayer.islandId());
                }

                upsertMember(connection, playerId, ownerIsland.islandId(), "MEMBER", ownerIsland.worldName());
                IslandRecord joined = findActiveIsland(connection, playerId);
                if (joined == null) {
                    throw new SQLException("Joined island could not be loaded for " + playerId);
                }
                connection.commit();
                return new CoopJoinResult(joined, deletedWorldName, evictedMembers, false);
            } catch (SQLException | RuntimeException e) {
                rollback(connection);
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to join coop island", e);
        }
    }

    MineResult recordOneBlockMined(long islandId) {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                int currentBlocks;
                boolean rewarded;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT blocks_mined, portal_rewarded FROM " + ISLAND_TABLE + " WHERE island_id=? FOR UPDATE")) {
                    ps.setLong(1, islandId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Unknown island_id: " + islandId);
                        }
                        currentBlocks = rs.getInt("blocks_mined");
                        rewarded = rs.getInt("portal_rewarded") != 0;
                    }
                }
                int newBlocks = currentBlocks + 1;
                boolean shouldReward = !rewarded && newBlocks >= 201;
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE " + ISLAND_TABLE + " SET blocks_mined=?, portal_rewarded=? WHERE island_id=?")) {
                    ps.setInt(1, newBlocks);
                    ps.setInt(2, rewarded || shouldReward ? 1 : 0);
                    ps.setLong(3, islandId);
                    ps.executeUpdate();
                }
                connection.commit();
                return new MineResult(newBlocks, rewarded || shouldReward, shouldReward);
            } catch (SQLException | RuntimeException e) {
                rollback(connection);
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to record island block mine", e);
        }
    }

    private Connection connect() throws SQLException {
        return MySqlConnectionFactory.connect();
    }

    private long ensureOwnedIsland(Connection connection, UUID ownerId, String worldName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO " + ISLAND_TABLE + " (owner_uuid, world_name, center_x, center_y, center_z) " +
                        "VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE world_name=VALUES(world_name)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, ownerId.toString());
            ps.setString(2, worldName);
            ps.setInt(3, 0);
            ps.setInt(4, 64);
            ps.setInt(5, 0);
            ps.executeUpdate();
        }
        IslandRecord island = findOwnedIsland(connection, ownerId);
        if (island == null) {
            throw new SQLException("Owned island insert failed for " + ownerId);
        }
        return island.islandId();
    }

    private IslandRecord findActiveIsland(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT i.island_id, i.owner_uuid, i.world_name, i.center_x, i.center_y, i.center_z, " +
                        "i.blocks_mined, i.portal_rewarded, m.member_role " +
                        "FROM " + MEMBER_TABLE + " m JOIN " + ISLAND_TABLE + " i ON m.island_id=i.island_id " +
                        "WHERE m.player_uuid=?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readIsland(rs) : null;
            }
        }
    }

    private IslandRecord findOwnedIsland(Connection connection, UUID ownerId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT island_id, owner_uuid, world_name, center_x, center_y, center_z, blocks_mined, portal_rewarded, " +
                        "'OWNER' AS member_role FROM " + ISLAND_TABLE + " WHERE owner_uuid=?")) {
            ps.setString(1, ownerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readIsland(rs) : null;
            }
        }
    }

    private IslandRecord readIsland(ResultSet rs) throws SQLException {
        return new IslandRecord(
                rs.getLong("island_id"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("world_name"),
                rs.getString("member_role"),
                rs.getInt("blocks_mined"),
                rs.getInt("portal_rewarded") != 0,
                rs.getInt("center_x"),
                rs.getInt("center_y"),
                rs.getInt("center_z")
        );
    }

    private void upsertMember(Connection connection, UUID playerId, long islandId, String role, String activeWorld) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO " + MEMBER_TABLE + " (player_uuid, island_id, member_role, active_world) VALUES (?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE island_id=VALUES(island_id), member_role=VALUES(member_role), " +
                        "active_world=VALUES(active_world)")) {
            ps.setString(1, playerId.toString());
            ps.setLong(2, islandId);
            ps.setString(3, role);
            ps.setString(4, activeWorld);
            ps.executeUpdate();
        }
    }

    private List<UUID> listMembers(Connection connection, long islandId) throws SQLException {
        List<UUID> members = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT player_uuid FROM " + MEMBER_TABLE + " WHERE island_id=?")) {
            ps.setLong(1, islandId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    members.add(UUID.fromString(rs.getString("player_uuid")));
                }
            }
        }
        return members;
    }

    private void deleteIslandRows(Connection connection, long islandId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM " + MEMBER_TABLE + " WHERE island_id=?")) {
            ps.setLong(1, islandId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM " + ISLAND_TABLE + " WHERE island_id=?")) {
            ps.setLong(1, islandId);
            ps.executeUpdate();
        }
    }

    private void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "IslandRepository rollback failed", e);
        }
    }

    record EnsureIslandResult(IslandRecord island, boolean created) {
    }

    record CoopJoinResult(IslandRecord island, String deletedWorldName, List<UUID> evictedMembers, boolean alreadyMember) {
    }

    record MineResult(int blocksMined, boolean portalRewarded, boolean spawnPortalReward) {
    }
}
