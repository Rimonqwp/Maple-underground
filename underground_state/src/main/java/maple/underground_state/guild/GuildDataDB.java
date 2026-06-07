package maple.underground_state.guild;

import maple.underground_state.db.MySqlConnectionFactory;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class GuildDataDB {

    private static final String GUILD_TABLE = "player_guilds";
    private static final String MEMBER_TABLE = "player_guild_members";
    private static final String ROLE_TABLE = "player_guild_roles";
    private static final String ROLE_PERMISSION_TABLE = "player_guild_role_permissions";
    private static final String ROLE_BANK_LIMIT_TABLE = "player_guild_role_bank_limits";
    private static final String GUILD_BANK_TABLE = "player_guild_bank_accounts";
    private static final String GUILD_BANK_LOG_TABLE = "player_guild_bank_logs";
    private static final String INVITE_TABLE = "player_guild_invites";

    private final Logger logger;

    public GuildDataDB(File dataFolder, Logger logger) {
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
                    "CREATE TABLE IF NOT EXISTS " + GUILD_TABLE + " (" +
                            "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                            "name VARCHAR(255) NOT NULL," +
                            "owner_uuid VARCHAR(36) NOT NULL," +
                            "owner_name VARCHAR(16) NOT NULL," +
                            "level INT NOT NULL DEFAULT 1," +
                            "bank_balance BIGINT NOT NULL DEFAULT 0," +
                            "default_role_id BIGINT NULL," +
                            "website_url VARCHAR(255) NULL," +
                            "free_join_enabled TINYINT(1) NOT NULL DEFAULT 0," +
                            "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "UNIQUE KEY uq_guild_owner (owner_uuid)," +
                            "INDEX idx_guild_name (name)" +
                            ")")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + MEMBER_TABLE + " (" +
                            "uuid VARCHAR(36) NOT NULL PRIMARY KEY," +
                            "guild_id BIGINT NOT NULL," +
                            "player_name VARCHAR(16) NOT NULL," +
                            "role VARCHAR(16) NOT NULL," +
                            "role_id BIGINT NULL," +
                            "discord_tag VARCHAR(64) NULL," +
                            "info_note VARCHAR(255) NULL," +
                            "joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "INDEX idx_guild_members_guild (guild_id)" +
                            ")")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + ROLE_TABLE + " (" +
                            "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                            "guild_id BIGINT NOT NULL," +
                            "name VARCHAR(64) NOT NULL," +
                            "system_role VARCHAR(16) NOT NULL DEFAULT 'CUSTOM'," +
                            "priority INT NOT NULL DEFAULT 100," +
                            "is_default TINYINT(1) NOT NULL DEFAULT 0," +
                            "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "INDEX idx_guild_roles_guild (guild_id)" +
                            ")")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + ROLE_PERMISSION_TABLE + " (" +
                            "role_id BIGINT NOT NULL," +
                            "permission VARCHAR(64) NOT NULL," +
                            "enabled TINYINT(1) NOT NULL DEFAULT 0," +
                            "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (role_id, permission)" +
                            ")")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + ROLE_BANK_LIMIT_TABLE + " (" +
                            "role_id BIGINT NOT NULL PRIMARY KEY," +
                            "deposit_daily BIGINT NOT NULL DEFAULT 0," +
                            "withdraw_daily BIGINT NOT NULL DEFAULT 0," +
                            "deposit_weekly BIGINT NOT NULL DEFAULT 0," +
                            "withdraw_weekly BIGINT NOT NULL DEFAULT 0," +
                            "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                            ")")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + GUILD_BANK_TABLE + " (" +
                            "guild_id BIGINT NOT NULL PRIMARY KEY," +
                            "balance BIGINT NOT NULL DEFAULT 0," +
                            "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                            ")")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + GUILD_BANK_LOG_TABLE + " (" +
                            "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                            "guild_id BIGINT NOT NULL," +
                            "actor_uuid VARCHAR(36) NOT NULL," +
                            "actor_name VARCHAR(16) NOT NULL," +
                            "action VARCHAR(16) NOT NULL," +
                            "amount BIGINT NOT NULL," +
                            "balance_after BIGINT NOT NULL," +
                            "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "INDEX idx_guild_bank_log (guild_id, created_at)" +
                            ")")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + INVITE_TABLE + " (" +
                            "guild_id BIGINT NOT NULL," +
                            "target_uuid VARCHAR(36) NOT NULL," +
                            "target_name VARCHAR(16) NOT NULL," +
                            "inviter_uuid VARCHAR(36) NOT NULL," +
                            "inviter_name VARCHAR(16) NOT NULL," +
                            "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (guild_id, target_uuid)," +
                            "INDEX idx_invite_target (target_uuid, created_at)" +
                            ")")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "ALTER TABLE " + GUILD_TABLE + " MODIFY name VARCHAR(255) NOT NULL")) {
                ps.executeUpdate();
            }
            migrateQuietly(connection, "ALTER TABLE " + GUILD_TABLE + " ADD COLUMN default_role_id BIGINT NULL");
            migrateQuietly(connection, "ALTER TABLE " + GUILD_TABLE + " ADD COLUMN free_join_enabled TINYINT(1) NOT NULL DEFAULT 0");
            migrateQuietly(connection, "ALTER TABLE " + MEMBER_TABLE + " ADD COLUMN role_id BIGINT NULL");
            migrateQuietly(connection, "ALTER TABLE " + MEMBER_TABLE + " ADD COLUMN discord_tag VARCHAR(64) NULL");
            migrateQuietly(connection, "ALTER TABLE " + MEMBER_TABLE + " ADD COLUMN info_note VARCHAR(255) NULL");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "GuildDataDB init failed", e);
        }
    }

    public @NotNull Optional<Guild> findGuildByMember(@NotNull UUID uuid) {
        String sql = "SELECT g.id, g.name, g.owner_uuid, g.owner_name, g.level, g.bank_balance, g.website_url, g.free_join_enabled, g.created_at " +
                "FROM " + MEMBER_TABLE + " m INNER JOIN " + GUILD_TABLE + " g ON g.id=m.guild_id WHERE m.uuid=?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(readGuild(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB findGuildByMember failed for " + uuid, e);
        }
        return Optional.empty();
    }

    public int countMembers(long guildId) {
        String sql = "SELECT COUNT(*) FROM " + MEMBER_TABLE + " WHERE guild_id=?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Math.max(0, rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB countMembers failed for guild " + guildId, e);
        }
        return 0;
    }

    public @NotNull List<GuildMember> loadMembers(long guildId, int offset, int limit) {
        List<GuildMember> members = new ArrayList<>();
        String sql = "SELECT m.guild_id, g.name AS guild_name, g.free_join_enabled AS guild_free_join_enabled, m.uuid, m.player_name, m.role, m.joined_at " +
                "FROM " + MEMBER_TABLE + " m INNER JOIN " + GUILD_TABLE + " g ON g.id=m.guild_id " +
                "WHERE m.guild_id=? ORDER BY " +
                "CASE m.role WHEN 'OWNER' THEN 0 WHEN 'OFFICER' THEN 1 ELSE 2 END, m.joined_at ASC LIMIT ? OFFSET ?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, guildId);
            ps.setInt(2, Math.max(0, limit));
            ps.setInt(3, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    members.add(readMember(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB loadMembers failed for guild " + guildId, e);
        }
        return members;
    }

    public @NotNull Optional<GuildMember> loadMember(long guildId, @NotNull UUID uuid) {
        String sql = "SELECT m.guild_id, g.name AS guild_name, g.free_join_enabled AS guild_free_join_enabled, " +
                "m.uuid, m.player_name, m.role, m.joined_at " +
                "FROM " + MEMBER_TABLE + " m INNER JOIN " + GUILD_TABLE + " g ON g.id=m.guild_id " +
                "WHERE m.guild_id=? AND m.uuid=?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, guildId);
            ps.setString(2, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(readMember(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB loadMember failed for guild " + guildId + " player " + uuid, e);
        }
        return Optional.empty();
    }

    public @NotNull Optional<GuildMemberProfile> loadMemberProfile(long guildId, @NotNull UUID uuid) {
        String sql = "SELECT m.discord_tag, m.info_note FROM " + MEMBER_TABLE + " m WHERE m.guild_id=? AND m.uuid=?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, guildId);
            ps.setString(2, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new GuildMemberProfile(
                            nullIfBlank(rs.getString("discord_tag")),
                            nullIfBlank(rs.getString("info_note"))
                    ));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB loadMemberProfile failed for guild " + guildId + " player " + uuid, e);
        }
        return Optional.empty();
    }

    public boolean setMemberDiscord(long guildId, @NotNull UUID uuid, @Nullable String discordTag) {
        String sql = "UPDATE " + MEMBER_TABLE + " SET discord_tag=? WHERE guild_id=? AND uuid=?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, nullIfBlank(discordTag));
            ps.setLong(2, guildId);
            ps.setString(3, uuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB setMemberDiscord failed for guild " + guildId + " player " + uuid, e);
            return false;
        }
    }

    public int countTransferCandidates(long guildId, @NotNull UUID currentOwnerUuid) {
        String sql = "SELECT COUNT(*) FROM " + MEMBER_TABLE + " WHERE guild_id=? AND uuid<>?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, guildId);
            ps.setString(2, currentOwnerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Math.max(0, rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB countTransferCandidates failed for guild " + guildId, e);
        }
        return 0;
    }

    public @NotNull List<GuildMember> loadTransferCandidates(long guildId,
                                                             @NotNull UUID currentOwnerUuid,
                                                             int offset,
                                                             int limit) {
        List<GuildMember> members = new ArrayList<>();
        String sql = "SELECT m.guild_id, g.name AS guild_name, g.free_join_enabled AS guild_free_join_enabled, m.uuid, m.player_name, m.role, m.joined_at " +
                "FROM " + MEMBER_TABLE + " m INNER JOIN " + GUILD_TABLE + " g ON g.id=m.guild_id " +
                "WHERE m.guild_id=? AND m.uuid<>? ORDER BY " +
                "CASE m.role WHEN 'OWNER' THEN 0 WHEN 'OFFICER' THEN 1 ELSE 2 END, m.joined_at ASC LIMIT ? OFFSET ?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, guildId);
            ps.setString(2, currentOwnerUuid.toString());
            ps.setInt(3, Math.max(0, limit));
            ps.setInt(4, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    members.add(readMember(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB loadTransferCandidates failed for guild " + guildId, e);
        }
        return members;
    }

    public int countOtherGuildMembers(@NotNull UUID viewer) {
        String sql = "SELECT COUNT(*) FROM " + MEMBER_TABLE + " WHERE uuid<>?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, viewer.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Math.max(0, rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB countOtherGuildMembers failed for " + viewer, e);
        }
        return 0;
    }

    public @NotNull List<GuildMember> loadOtherGuildMembers(@NotNull UUID viewer, int offset, int limit) {
        List<GuildMember> members = new ArrayList<>();
        String sql = "SELECT m.guild_id, g.name AS guild_name, g.free_join_enabled AS guild_free_join_enabled, m.uuid, m.player_name, m.role, m.joined_at " +
                "FROM " + MEMBER_TABLE + " m INNER JOIN " + GUILD_TABLE + " g ON g.id=m.guild_id " +
                "WHERE m.uuid<>? ORDER BY g.created_at DESC, m.joined_at ASC LIMIT ? OFFSET ?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, viewer.toString());
            ps.setInt(2, Math.max(0, limit));
            ps.setInt(3, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    members.add(readMember(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB loadOtherGuildMembers failed for " + viewer, e);
        }
        return members;
    }

    public @NotNull CreateGuildResult createGuild(@NotNull Player owner) {
        return createGuild(owner, defaultGuildName(owner.getName()));
    }

    public @NotNull CreateGuildResult createGuild(@NotNull Player owner, @NotNull String guildName) {
        Connection connection = null;
        UUID uuid = owner.getUniqueId();
        String name = guildName.trim();
        if (name.isBlank() || name.length() > 255) {
            return CreateGuildResult.INVALID_NAME;
        }
        try {
            connection = connect();
            connection.setAutoCommit(false);

            if (isMember(connection, uuid)) {
                connection.rollback();
                return CreateGuildResult.ALREADY_IN_GUILD;
            }

            long guildId;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + GUILD_TABLE + " (name, owner_uuid, owner_name) VALUES (?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, uuid.toString());
                ps.setString(3, owner.getName());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        connection.rollback();
                        return CreateGuildResult.STORAGE_ERROR;
                    }
                    guildId = keys.getLong(1);
                }
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + MEMBER_TABLE + " (uuid, guild_id, player_name, role) VALUES (?,?,?,?)")) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, guildId);
                ps.setString(3, owner.getName());
                ps.setString(4, "OWNER");
                ps.executeUpdate();
            }
            createDefaultRoles(connection, guildId, uuid);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT IGNORE INTO " + GUILD_BANK_TABLE + " (guild_id, balance) VALUES (?,0)")) {
                ps.setLong(1, guildId);
                ps.executeUpdate();
            }

            connection.commit();
            return CreateGuildResult.SUCCESS;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB createGuild failed for " + uuid, e);
            rollbackQuietly(connection);
            return CreateGuildResult.STORAGE_ERROR;
        } finally {
            closeQuietly(connection);
        }
    }

    public @NotNull List<GuildRole> loadRoles(long guildId) {
        ensureDefaultRoles(guildId);
        List<GuildRole> roles = new ArrayList<>();
        String sql = "SELECT id, guild_id, name, system_role, priority, is_default FROM " + ROLE_TABLE +
                " WHERE guild_id=? ORDER BY priority ASC, id ASC";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    roles.add(readRole(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB loadRoles failed for guild " + guildId, e);
        }
        return roles;
    }

    public @NotNull Optional<GuildRole> loadRole(long roleId) {
        String sql = "SELECT id, guild_id, name, system_role, priority, is_default FROM " + ROLE_TABLE + " WHERE id=?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(readRole(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB loadRole failed for role " + roleId, e);
        }
        return Optional.empty();
    }

    public @NotNull Optional<GuildRole> findMemberRole(@NotNull UUID uuid) {
        String sql = "SELECT m.guild_id, m.role_id, g.owner_uuid " +
                "FROM " + MEMBER_TABLE + " m INNER JOIN " + GUILD_TABLE + " g ON g.id=m.guild_id WHERE m.uuid=?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                long guildId = rs.getLong("guild_id");
                long roleId = rs.getLong("role_id");
                if (!rs.wasNull() && roleId > 0L) {
                    Optional<GuildRole> role = loadRole(roleId);
                    if (role.isPresent()) {
                        return role;
                    }
                }

                String ownerUuid = rs.getString("owner_uuid");
                if (ownerUuid != null && ownerUuid.equals(uuid.toString())) {
                    Optional<GuildRole> ownerRole = findRoleBySystemRole(connection, guildId, "OWNER");
                    if (ownerRole.isPresent()) {
                        return ownerRole;
                    }
                }

                Optional<Long> configuredDefaultRole = defaultRoleId(connection, guildId);
                if (configuredDefaultRole.isPresent()) {
                    Optional<GuildRole> role = loadRole(configuredDefaultRole.get());
                    if (role.isPresent()) {
                        return role;
                    }
                }

                long memberRoleId = firstMemberRoleId(connection, guildId);
                if (memberRoleId > 0L) {
                    Optional<GuildRole> role = loadRole(memberRoleId);
                    if (role.isPresent()) {
                        return role;
                    }
                }
                return findRoleBySystemRole(connection, guildId, "MEMBER");
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB findMemberRole failed for " + uuid, e);
        }
        return Optional.empty();
    }

    public boolean hasPermission(@NotNull GuildRole role, @NotNull String permission) {
        if ("OWNER".equals(role.systemRole())) {
            return true;
        }
        String sql = "SELECT enabled FROM " + ROLE_PERMISSION_TABLE + " WHERE role_id=? AND permission=?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, role.id());
            ps.setString(2, permission);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean("enabled");
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB hasPermission failed for role " + role.id(), e);
            return false;
        }
    }

    public @NotNull Set<String> loadEnabledPermissions(@NotNull GuildRole role) {
        Set<String> permissions = new HashSet<>();
        if ("OWNER".equals(role.systemRole())) {
            permissions.add("*");
            return permissions;
        }
        String sql = "SELECT permission FROM " + ROLE_PERMISSION_TABLE + " WHERE role_id=? AND enabled=1";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, role.id());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    permissions.add(rs.getString("permission"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB loadEnabledPermissions failed for role " + role.id(), e);
        }
        return permissions;
    }

    public boolean setPermission(@NotNull GuildRole role, @NotNull String permission, boolean enabled) {
        if ("OWNER".equals(role.systemRole())) {
            return false;
        }
        String sql = "INSERT INTO " + ROLE_PERMISSION_TABLE + " (role_id, permission, enabled) VALUES (?,?,?) " +
                "ON DUPLICATE KEY UPDATE enabled=VALUES(enabled)";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, role.id());
            ps.setString(2, permission);
            ps.setBoolean(3, enabled);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB setPermission failed for role " + role.id(), e);
            return false;
        }
    }

    public boolean setFreeJoinEnabled(@NotNull Guild guild, boolean enabled) {
        String sql = "UPDATE " + GUILD_TABLE + " SET free_join_enabled=? WHERE id=?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setBoolean(1, enabled);
            ps.setLong(2, guild.id());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB setFreeJoinEnabled failed for guild " + guild.id(), e);
            return false;
        }
    }

    public boolean createRole(long guildId, @NotNull String name) {
        ensureDefaultRoles(guildId);
        String safeName = name.trim();
        if (safeName.isBlank() || safeName.length() > 64) {
            return false;
        }
        int nextPriority = 100 + loadRoles(guildId).size();
        String sql = "INSERT INTO " + ROLE_TABLE + " (guild_id, name, system_role, priority, is_default) VALUES (?,?,?,?,0)";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, guildId);
            ps.setString(2, safeName);
            ps.setString(3, "CUSTOM");
            ps.setInt(4, nextPriority);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB createRole failed for guild " + guildId, e);
            return false;
        }
    }

    public boolean addMember(@NotNull Guild guild, @NotNull Player player) {
        UUID uuid = player.getUniqueId();
        Connection connection = null;
        try {
            connection = connect();
            connection.setAutoCommit(false);
            if (isMember(connection, uuid)) {
                connection.rollback();
                return false;
            }
            Optional<Long> configuredDefaultRole = defaultRoleId(connection, guild.id());
            long roleId = configuredDefaultRole.orElse(firstMemberRoleId(connection, guild.id()));
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + MEMBER_TABLE + " (uuid, guild_id, player_name, role, role_id) VALUES (?,?,?,?,?)")) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, guild.id());
                ps.setString(3, player.getName());
                ps.setString(4, "MEMBER");
                ps.setLong(5, roleId);
                ps.executeUpdate();
            }
            connection.commit();
            return true;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB addMember failed for guild " + guild.id() + " player " + uuid, e);
            rollbackQuietly(connection);
            return false;
        } finally {
            closeQuietly(connection);
        }
    }

    public boolean transferOwnership(@NotNull Guild guild,
                                     @NotNull Player currentOwner,
                                     @NotNull UUID targetUuid,
                                     @NotNull String targetName) {
        UUID currentOwnerUuid = currentOwner.getUniqueId();
        if (!guild.ownerUuid().equals(currentOwnerUuid) || currentOwnerUuid.equals(targetUuid)) {
            return false;
        }

        ensureDefaultRoles(guild.id());
        Connection connection = null;
        try {
            connection = connect();
            connection.setAutoCommit(false);

            Optional<GuildRole> ownerRole = findRoleBySystemRole(connection, guild.id(), "OWNER");
            Optional<Long> configuredDefaultRole = defaultRoleId(connection, guild.id());
            long memberRoleId = configuredDefaultRole.orElse(firstMemberRoleId(connection, guild.id()));
            if (ownerRole.isEmpty() || memberRoleId <= 0L) {
                connection.rollback();
                return false;
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT owner_uuid FROM " + GUILD_TABLE + " WHERE id=? FOR UPDATE")) {
                ps.setLong(1, guild.id());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || !currentOwnerUuid.toString().equals(rs.getString("owner_uuid"))) {
                        connection.rollback();
                        return false;
                    }
                }
            }

            String storedTargetName;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT player_name FROM " + MEMBER_TABLE + " WHERE guild_id=? AND uuid=? FOR UPDATE")) {
                ps.setLong(1, guild.id());
                ps.setString(2, targetUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        connection.rollback();
                        return false;
                    }
                    storedTargetName = rs.getString("player_name");
                }
            }
            String newOwnerName = storedTargetName == null || storedTargetName.isBlank() ? targetName : storedTargetName;

            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + GUILD_TABLE + " SET owner_uuid=?, owner_name=? WHERE id=?")) {
                ps.setString(1, targetUuid.toString());
                ps.setString(2, newOwnerName);
                ps.setLong(3, guild.id());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + MEMBER_TABLE + " SET role='OWNER', role_id=?, player_name=? WHERE guild_id=? AND uuid=?")) {
                ps.setLong(1, ownerRole.get().id());
                ps.setString(2, newOwnerName);
                ps.setLong(3, guild.id());
                ps.setString(4, targetUuid.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + MEMBER_TABLE + " SET role='MEMBER', role_id=?, player_name=? WHERE guild_id=? AND uuid=?")) {
                ps.setLong(1, memberRoleId);
                ps.setString(2, currentOwner.getName());
                ps.setLong(3, guild.id());
                ps.setString(4, currentOwnerUuid.toString());
                ps.executeUpdate();
            }

            connection.commit();
            return true;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB transferOwnership failed for guild " + guild.id()
                    + " owner " + currentOwnerUuid + " target " + targetUuid, e);
            rollbackQuietly(connection);
            return false;
        } finally {
            closeQuietly(connection);
        }
    }

    public boolean leaveGuild(@NotNull Guild guild, @NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (guild.ownerUuid().equals(uuid)) {
            return false;
        }

        String sql = "DELETE FROM " + MEMBER_TABLE + " WHERE guild_id=? AND uuid=? AND role<>'OWNER'";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, guild.id());
            ps.setString(2, uuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB leaveGuild failed for guild " + guild.id() + " player " + uuid, e);
            return false;
        }
    }

    public boolean kickMember(@NotNull Guild guild, @NotNull UUID targetUuid) {
        if (guild.ownerUuid().equals(targetUuid)) {
            return false;
        }

        String sql = "DELETE FROM " + MEMBER_TABLE + " WHERE guild_id=? AND uuid=? AND role<>'OWNER'";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, guild.id());
            ps.setString(2, targetUuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB kickMember failed for guild " + guild.id() + " player " + targetUuid, e);
            return false;
        }
    }

    public boolean setMemberRole(long guildId, @NotNull UUID targetUuid, @NotNull GuildRole role) {
        if (role.guildId() != guildId || "OWNER".equals(role.systemRole())) {
            return false;
        }

        String legacyRole = "MEMBER".equals(role.systemRole()) ? "MEMBER" : "OFFICER";
        String sql = "UPDATE " + MEMBER_TABLE + " SET role=?, role_id=? WHERE guild_id=? AND uuid=? AND role<>'OWNER'";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, legacyRole);
            ps.setLong(2, role.id());
            ps.setLong(3, guildId);
            ps.setString(4, targetUuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB setMemberRole failed for guild " + guildId
                    + " player " + targetUuid + " role " + role.id(), e);
            return false;
        }
    }

    public boolean disbandGuild(@NotNull Guild guild, @NotNull Player owner) {
        UUID ownerUuid = owner.getUniqueId();
        if (!guild.ownerUuid().equals(ownerUuid)) {
            return false;
        }

        Connection connection = null;
        try {
            connection = connect();
            connection.setAutoCommit(false);

            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT owner_uuid FROM " + GUILD_TABLE + " WHERE id=? FOR UPDATE")) {
                ps.setLong(1, guild.id());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || !ownerUuid.toString().equals(rs.getString("owner_uuid"))) {
                        connection.rollback();
                        return false;
                    }
                }
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM " + ROLE_PERMISSION_TABLE + " WHERE role_id IN " +
                            "(SELECT id FROM " + ROLE_TABLE + " WHERE guild_id=?)")) {
                ps.setLong(1, guild.id());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM " + ROLE_BANK_LIMIT_TABLE + " WHERE role_id IN " +
                            "(SELECT id FROM " + ROLE_TABLE + " WHERE guild_id=?)")) {
                ps.setLong(1, guild.id());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM " + GUILD_BANK_LOG_TABLE + " WHERE guild_id=?")) {
                ps.setLong(1, guild.id());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM " + GUILD_BANK_TABLE + " WHERE guild_id=?")) {
                ps.setLong(1, guild.id());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM " + INVITE_TABLE + " WHERE guild_id=?")) {
                ps.setLong(1, guild.id());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM " + MEMBER_TABLE + " WHERE guild_id=?")) {
                ps.setLong(1, guild.id());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM " + ROLE_TABLE + " WHERE guild_id=?")) {
                ps.setLong(1, guild.id());
                ps.executeUpdate();
            }
            int deleted;
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM " + GUILD_TABLE + " WHERE id=? AND owner_uuid=?")) {
                ps.setLong(1, guild.id());
                ps.setString(2, ownerUuid.toString());
                deleted = ps.executeUpdate();
            }

            if (deleted <= 0) {
                connection.rollback();
                return false;
            }
            connection.commit();
            return true;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB disbandGuild failed for guild " + guild.id()
                    + " owner " + ownerUuid, e);
            rollbackQuietly(connection);
            return false;
        } finally {
            closeQuietly(connection);
        }
    }

    public boolean createInvite(@NotNull Guild guild, @NotNull Player inviter, @NotNull Player target) {
        if (findGuildByMember(target.getUniqueId()).isPresent()) {
            return false;
        }
        String sql = "INSERT INTO " + INVITE_TABLE +
                " (guild_id, target_uuid, target_name, inviter_uuid, inviter_name) VALUES (?,?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE target_name=VALUES(target_name), inviter_uuid=VALUES(inviter_uuid), " +
                "inviter_name=VALUES(inviter_name), created_at=CURRENT_TIMESTAMP";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, guild.id());
            ps.setString(2, target.getUniqueId().toString());
            ps.setString(3, target.getName());
            ps.setString(4, inviter.getUniqueId().toString());
            ps.setString(5, inviter.getName());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB createInvite failed for guild " + guild.id() + " target " + target.getUniqueId(), e);
            return false;
        }
    }

    public @NotNull List<GuildInvite> loadInvites(@NotNull UUID targetUuid) {
        List<GuildInvite> invites = new ArrayList<>();
        String sql = "SELECT i.guild_id, g.name AS guild_name, i.inviter_uuid, i.inviter_name, i.created_at " +
                "FROM " + INVITE_TABLE + " i INNER JOIN " + GUILD_TABLE + " g ON g.id=i.guild_id " +
                "WHERE i.target_uuid=? ORDER BY i.created_at DESC";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, targetUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    invites.add(new GuildInvite(
                            rs.getLong("guild_id"),
                            rs.getString("guild_name"),
                            UUID.fromString(rs.getString("inviter_uuid")),
                            rs.getString("inviter_name"),
                            instant(rs.getTimestamp("created_at"))
                    ));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB loadInvites failed for " + targetUuid, e);
        }
        return invites;
    }

    public @NotNull Optional<Guild> findGuildById(long guildId) {
        String sql = "SELECT id, name, owner_uuid, owner_name, level, bank_balance, website_url, free_join_enabled, created_at FROM " + GUILD_TABLE + " WHERE id=?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(readGuild(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB findGuildById failed for " + guildId, e);
        }
        return Optional.empty();
    }

    public boolean consumeInvite(@NotNull UUID targetUuid, long guildId) {
        String sql = "DELETE FROM " + INVITE_TABLE + " WHERE target_uuid=? AND guild_id=?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, targetUuid.toString());
            ps.setLong(2, guildId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB consumeInvite failed for " + targetUuid + " guild " + guildId, e);
            return false;
        }
    }

    public long guildBankBalance(long guildId) {
        String sql = "SELECT balance FROM " + GUILD_BANK_TABLE + " WHERE guild_id=?";
        try (Connection connection = connect()) {
            ensureGuildBank(connection, guildId);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, guildId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Math.max(0L, rs.getLong("balance"));
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB guildBankBalance failed for guild " + guildId, e);
        }
        return 0L;
    }

    public @NotNull GuildBankChangeResult changeGuildBank(long guildId,
                                                          @NotNull Player actor,
                                                          long delta,
                                                          @NotNull String action) {
        Connection connection = null;
        try {
            connection = connect();
            connection.setAutoCommit(false);
            ensureGuildBank(connection, guildId);

            long current;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT balance FROM " + GUILD_BANK_TABLE + " WHERE guild_id=? FOR UPDATE")) {
                ps.setLong(1, guildId);
                try (ResultSet rs = ps.executeQuery()) {
                    current = rs.next() ? Math.max(0L, rs.getLong("balance")) : 0L;
                }
            }
            long updated = current + delta;
            if (updated < 0L) {
                connection.rollback();
                return new GuildBankChangeResult(false, true, current);
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + GUILD_BANK_TABLE + " SET balance=? WHERE guild_id=?")) {
                ps.setLong(1, updated);
                ps.setLong(2, guildId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + GUILD_BANK_LOG_TABLE +
                            " (guild_id, actor_uuid, actor_name, action, amount, balance_after) VALUES (?,?,?,?,?,?)")) {
                ps.setLong(1, guildId);
                ps.setString(2, actor.getUniqueId().toString());
                ps.setString(3, actor.getName());
                ps.setString(4, action);
                ps.setLong(5, Math.abs(delta));
                ps.setLong(6, updated);
                ps.executeUpdate();
            }
            connection.commit();
            return new GuildBankChangeResult(true, false, updated);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB changeGuildBank failed for guild " + guildId, e);
            rollbackQuietly(connection);
            return new GuildBankChangeResult(false, false, 0L);
        } finally {
            closeQuietly(connection);
        }
    }

    public @NotNull List<GuildBankLogEntry> loadGuildBankLogs(long guildId, int offset, int limit) {
        List<GuildBankLogEntry> entries = new ArrayList<>();
        String sql = "SELECT actor_name, action, amount, balance_after, created_at FROM " + GUILD_BANK_LOG_TABLE +
                " WHERE guild_id=? ORDER BY id DESC LIMIT ? OFFSET ?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, guildId);
            ps.setInt(2, Math.max(0, limit));
            ps.setInt(3, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new GuildBankLogEntry(
                            rs.getString("actor_name"),
                            rs.getString("action"),
                            Math.max(0L, rs.getLong("amount")),
                            Math.max(0L, rs.getLong("balance_after")),
                            instant(rs.getTimestamp("created_at"))
                    ));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB loadGuildBankLogs failed for guild " + guildId, e);
        }
        return entries;
    }

    public boolean setDefaultRole(long guildId, long roleId) {
        Connection connection = null;
        try {
            connection = connect();
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + ROLE_TABLE + " SET is_default=0 WHERE guild_id=?")) {
                ps.setLong(1, guildId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + ROLE_TABLE + " SET is_default=1 WHERE guild_id=? AND id=? AND system_role<>'OWNER'")) {
                ps.setLong(1, guildId);
                ps.setLong(2, roleId);
                if (ps.executeUpdate() <= 0) {
                    connection.rollback();
                    return false;
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + GUILD_TABLE + " SET default_role_id=? WHERE id=?")) {
                ps.setLong(1, roleId);
                ps.setLong(2, guildId);
                ps.executeUpdate();
            }
            connection.commit();
            return true;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB setDefaultRole failed for guild " + guildId + " role " + roleId, e);
            rollbackQuietly(connection);
            return false;
        } finally {
            closeQuietly(connection);
        }
    }

    private void ensureDefaultRoles(long guildId) {
        try (Connection connection = connect()) {
            ensureDefaultRoles(connection, guildId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "GuildDataDB ensureDefaultRoles failed for guild " + guildId, e);
        }
    }

    private static void ensureDefaultRoles(@NotNull Connection connection, long guildId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + ROLE_TABLE + " WHERE guild_id=?")) {
            ps.setLong(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            }
        }
        createDefaultRoles(connection, guildId, null);
    }

    private static void createDefaultRoles(@NotNull Connection connection, long guildId, @Nullable UUID ownerUuid) throws SQLException {
        long ownerRoleId;
        long memberRoleId;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO " + ROLE_TABLE + " (guild_id, name, system_role, priority, is_default) VALUES (?,?,?,?,0)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, guildId);
            ps.setString(2, "会长");
            ps.setString(3, "OWNER");
            ps.setInt(4, 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Missing owner role generated key");
                }
                ownerRoleId = keys.getLong(1);
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO " + ROLE_TABLE + " (guild_id, name, system_role, priority, is_default) VALUES (?,?,?,?,1)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, guildId);
            ps.setString(2, "成员");
            ps.setString(3, "MEMBER");
            ps.setInt(4, 100);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Missing member role generated key");
                }
                memberRoleId = keys.getLong(1);
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE " + GUILD_TABLE + " SET default_role_id=? WHERE id=?")) {
            ps.setLong(1, memberRoleId);
            ps.setLong(2, guildId);
            ps.executeUpdate();
        }
        if (ownerUuid != null) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + MEMBER_TABLE + " SET role_id=? WHERE guild_id=? AND uuid=?")) {
                ps.setLong(1, ownerRoleId);
                ps.setLong(2, guildId);
                ps.setString(3, ownerUuid.toString());
                ps.executeUpdate();
            }
        }
    }

    private static @NotNull Optional<GuildRole> findRoleBySystemRole(@NotNull Connection connection,
                                                                     long guildId,
                                                                     @NotNull String systemRole) throws SQLException {
        String sql = "SELECT id, guild_id, name, system_role, priority, is_default FROM " + ROLE_TABLE +
                " WHERE guild_id=? AND system_role=? ORDER BY id ASC LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, guildId);
            ps.setString(2, systemRole);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(readRole(rs));
                }
            }
        }
        return Optional.empty();
    }

    private static @NotNull Optional<Long> defaultRoleId(@NotNull Connection connection, long guildId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT default_role_id FROM " + GUILD_TABLE + " WHERE id=?")) {
            ps.setLong(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long roleId = rs.getLong("default_role_id");
                    if (!rs.wasNull() && roleId > 0L) {
                        return Optional.of(roleId);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static long firstMemberRoleId(@NotNull Connection connection, long guildId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM " + ROLE_TABLE + " WHERE guild_id=? AND system_role='MEMBER' ORDER BY id ASC LIMIT 1")) {
            ps.setLong(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        } catch (SQLException ignored) {
        }
        return 0L;
    }

    private static void ensureGuildBank(@NotNull Connection connection, long guildId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT IGNORE INTO " + GUILD_BANK_TABLE + " (guild_id, balance) VALUES (?,0)")) {
            ps.setLong(1, guildId);
            ps.executeUpdate();
        }
    }

    private static boolean isMember(@NotNull Connection connection, @NotNull UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT guild_id FROM " + MEMBER_TABLE + " WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static @NotNull Guild readGuild(@NotNull ResultSet rs) throws SQLException {
        return new Guild(
                rs.getLong("id"),
                rs.getString("name"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("owner_name"),
                Math.max(1, rs.getInt("level")),
                Math.max(0L, rs.getLong("bank_balance")),
                rs.getString("website_url"),
                rs.getBoolean("free_join_enabled"),
                instant(rs.getTimestamp("created_at"))
        );
    }

    private static @NotNull GuildMember readMember(@NotNull ResultSet rs) throws SQLException {
        return new GuildMember(
                rs.getLong("guild_id"),
                rs.getString("guild_name"),
                rs.getBoolean("guild_free_join_enabled"),
                UUID.fromString(rs.getString("uuid")),
                rs.getString("player_name"),
                rs.getString("role"),
                instant(rs.getTimestamp("joined_at"))
        );
    }

    private static @NotNull GuildRole readRole(@NotNull ResultSet rs) throws SQLException {
        return new GuildRole(
                rs.getLong("id"),
                rs.getLong("guild_id"),
                rs.getString("name"),
                rs.getString("system_role"),
                rs.getInt("priority"),
                rs.getBoolean("is_default")
        );
    }

    private static @NotNull Instant instant(@Nullable Timestamp timestamp) {
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    private static @Nullable String nullIfBlank(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static @NotNull String defaultGuildName(@NotNull String playerName) {
        String safeName = playerName.isBlank() ? "Player" : playerName;
        String name = safeName + "的公会";
        return name.length() <= 32 ? name : name.substring(0, 32);
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

    private static void migrateQuietly(@NotNull Connection connection, @NotNull String sql) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public enum CreateGuildResult {
        SUCCESS,
        ALREADY_IN_GUILD,
        INVALID_NAME,
        STORAGE_ERROR
    }

    public record Guild(long id,
                        @NotNull String name,
                        @NotNull UUID ownerUuid,
                        @NotNull String ownerName,
                        int level,
                        long bankBalance,
                        @Nullable String websiteUrl,
                        boolean freeJoinEnabled,
                        @NotNull Instant createdAt) {
    }

    public record GuildMember(long guildId,
                              @NotNull String guildName,
                              boolean guildFreeJoinEnabled,
                              @NotNull UUID uuid,
                              @NotNull String playerName,
                              @NotNull String role,
                              @NotNull Instant joinedAt) {
    }

    public record GuildMemberProfile(@Nullable String discordTag,
                                     @Nullable String infoNote) {
    }

    public record GuildRole(long id,
                            long guildId,
                            @NotNull String name,
                            @NotNull String systemRole,
                            int priority,
                            boolean defaultRole) {
    }

    public record GuildBankChangeResult(boolean success,
                                        boolean insufficientFunds,
                                        long balanceAfter) {
    }

    public record GuildBankLogEntry(@NotNull String actorName,
                                    @NotNull String action,
                                    long amount,
                                    long balanceAfter,
                                    @NotNull Instant createdAt) {
    }

    public record GuildInvite(long guildId,
                              @NotNull String guildName,
                              @NotNull UUID inviterUuid,
                              @NotNull String inviterName,
                              @NotNull Instant createdAt) {
    }
}
