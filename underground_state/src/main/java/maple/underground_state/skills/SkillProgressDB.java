package maple.underground_state.skills;

import maple.underground_state.db.MySqlConnectionFactory;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SkillProgressDB {

    private static final String PROGRESS_TABLE = "player_skill_progress";
    private static final String MAPLE_XP_COLUMN = "maple_xp";
    private static final String COMBAT_XP_REMAINDER_COLUMN = "combat_xp_remainder_tenths";
    private static final String MINING_XP_REMAINDER_COLUMN = "mining_xp_remainder_tenths";
    private static final String FORAGING_XP_REMAINDER_COLUMN = "foraging_xp_remainder_tenths";
    private static final String FARMING_XP_REMAINDER_COLUMN = "farming_xp_remainder_tenths";
    private static final String FISHING_XP_REMAINDER_COLUMN = "fishing_xp_remainder_tenths";
    private static final String ALCHEMY_XP_REMAINDER_COLUMN = "alchemy_xp_remainder_tenths";
    private static final String CRAFTING_XP_REMAINDER_COLUMN = "crafting_xp_remainder_tenths";
    private static final String EXPLORATION_DISTANCE_REMAINDER_COLUMN = "exploration_distance_remainder_millis";
    private final Logger logger;

    public SkillProgressDB(File dataFolder, Logger logger) {
        this.logger = logger != null ? logger : Logger.getAnonymousLogger();
        MySqlConnectionFactory.logTarget(this.logger);
        init();
    }

    private Connection connect() throws SQLException {
        return MySqlConnectionFactory.connect();
    }

    private void init() {
        try (Connection connection = connect()) {
            try (PreparedStatement ps = connection.prepareStatement(createProgressTableSql())) {
                ps.executeUpdate();
            }
            addProgressColumnsIfMissing(connection);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SkillProgressDB init failed", e);
        }
    }

    public @NotNull SkillProgressData load(UUID uuid) {
        SkillProgressData data = new SkillProgressData();
        if (uuid == null) {
            return data;
        }

        String sql = "SELECT * FROM " + PROGRESS_TABLE + " WHERE uuid=?";
        try (Connection connection = connect()) {
            ensurePlayerRow(connection, uuid);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        for (SkillType skillType : SkillType.values()) {
                            data.setXp(skillType, getLongOr(rs, skillType.columnName(), 0L));
                        }
                        data.setMapleXp(getLongOr(rs, MAPLE_XP_COLUMN, 0L));
                        data.setCombatXpRemainderTenths(getIntOr(rs, COMBAT_XP_REMAINDER_COLUMN, 0));
                        data.setMiningXpRemainderTenths(getIntOr(rs, MINING_XP_REMAINDER_COLUMN, 0));
                        data.setForagingXpRemainderTenths(getIntOr(rs, FORAGING_XP_REMAINDER_COLUMN, 0));
                        data.setFarmingXpRemainderTenths(getIntOr(rs, FARMING_XP_REMAINDER_COLUMN, 0));
                        data.setFishingXpRemainderTenths(getIntOr(rs, FISHING_XP_REMAINDER_COLUMN, 0));
                        data.setAlchemyXpRemainderTenths(getIntOr(rs, ALCHEMY_XP_REMAINDER_COLUMN, 0));
                        data.setCraftingXpRemainderTenths(getIntOr(rs, CRAFTING_XP_REMAINDER_COLUMN, 0));
                        data.setExplorationDistanceRemainderMillis(getIntOr(rs, EXPLORATION_DISTANCE_REMAINDER_COLUMN, 0));
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "SkillProgressDB load failed for " + uuid, e);
        }
        return data;
    }

    public void save(UUID uuid, @NotNull SkillProgressData data) {
        if (uuid == null) {
            return;
        }

        StringBuilder columns = new StringBuilder("uuid");
        StringBuilder placeholders = new StringBuilder("?");
        for (SkillType skillType : SkillType.values()) {
            columns.append(", ").append(skillType.columnName());
            placeholders.append(", ?");
        }
        columns.append(", ").append(MAPLE_XP_COLUMN);
        placeholders.append(", ?");
        columns.append(", ").append(COMBAT_XP_REMAINDER_COLUMN);
        placeholders.append(", ?");
        columns.append(", ").append(MINING_XP_REMAINDER_COLUMN);
        placeholders.append(", ?");
        columns.append(", ").append(FORAGING_XP_REMAINDER_COLUMN);
        placeholders.append(", ?");
        columns.append(", ").append(FARMING_XP_REMAINDER_COLUMN);
        placeholders.append(", ?");
        columns.append(", ").append(FISHING_XP_REMAINDER_COLUMN);
        placeholders.append(", ?");
        columns.append(", ").append(ALCHEMY_XP_REMAINDER_COLUMN);
        placeholders.append(", ?");
        columns.append(", ").append(CRAFTING_XP_REMAINDER_COLUMN);
        placeholders.append(", ?");
        columns.append(", ").append(EXPLORATION_DISTANCE_REMAINDER_COLUMN);
        placeholders.append(", ?");

        StringBuilder updates = new StringBuilder();
        for (SkillType skillType : SkillType.values()) {
            appendUpdate(updates, skillType.columnName());
        }
        appendUpdate(updates, MAPLE_XP_COLUMN);
        appendUpdate(updates, COMBAT_XP_REMAINDER_COLUMN);
        appendUpdate(updates, MINING_XP_REMAINDER_COLUMN);
        appendUpdate(updates, FORAGING_XP_REMAINDER_COLUMN);
        appendUpdate(updates, FARMING_XP_REMAINDER_COLUMN);
        appendUpdate(updates, FISHING_XP_REMAINDER_COLUMN);
        appendUpdate(updates, ALCHEMY_XP_REMAINDER_COLUMN);
        appendUpdate(updates, CRAFTING_XP_REMAINDER_COLUMN);
        appendUpdate(updates, EXPLORATION_DISTANCE_REMAINDER_COLUMN);

        String sql = "INSERT INTO " + PROGRESS_TABLE + " (" + columns + ") VALUES (" + placeholders + ") " +
                "ON DUPLICATE KEY UPDATE " + updates;
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            int index = 2;
            for (SkillType skillType : SkillType.values()) {
                ps.setLong(index++, data.getXp(skillType));
            }
            ps.setLong(index++, data.getMapleXp());
            ps.setInt(index++, data.getCombatXpRemainderTenths());
            ps.setInt(index++, data.getMiningXpRemainderTenths());
            ps.setInt(index++, data.getForagingXpRemainderTenths());
            ps.setInt(index++, data.getFarmingXpRemainderTenths());
            ps.setInt(index++, data.getFishingXpRemainderTenths());
            ps.setInt(index++, data.getAlchemyXpRemainderTenths());
            ps.setInt(index++, data.getCraftingXpRemainderTenths());
            ps.setInt(index, data.getExplorationDistanceRemainderMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "SkillProgressDB save failed for " + uuid, e);
        }
    }

    private static void appendUpdate(StringBuilder updates, String columnName) {
        if (!updates.isEmpty()) {
            updates.append(", ");
        }
        updates.append(columnName).append("=VALUES(").append(columnName).append(')');
    }

    private void ensurePlayerRow(Connection connection, UUID uuid) throws SQLException {
        String sql = "INSERT IGNORE INTO " + PROGRESS_TABLE + " (uuid) VALUES (?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    private String createProgressTableSql() {
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                .append(PROGRESS_TABLE)
                .append(" (uuid VARCHAR(36) PRIMARY KEY");
        for (SkillType skillType : SkillType.values()) {
            sql.append(", ").append(skillType.columnName()).append(" BIGINT NOT NULL DEFAULT 0");
        }
        sql.append(", ").append(MAPLE_XP_COLUMN).append(" BIGINT NOT NULL DEFAULT 0");
        sql.append(", ").append(COMBAT_XP_REMAINDER_COLUMN).append(" INT NOT NULL DEFAULT 0");
        sql.append(", ").append(MINING_XP_REMAINDER_COLUMN).append(" INT NOT NULL DEFAULT 0");
        sql.append(", ").append(FORAGING_XP_REMAINDER_COLUMN).append(" INT NOT NULL DEFAULT 0");
        sql.append(", ").append(FARMING_XP_REMAINDER_COLUMN).append(" INT NOT NULL DEFAULT 0");
        sql.append(", ").append(FISHING_XP_REMAINDER_COLUMN).append(" INT NOT NULL DEFAULT 0");
        sql.append(", ").append(ALCHEMY_XP_REMAINDER_COLUMN).append(" INT NOT NULL DEFAULT 0");
        sql.append(", ").append(CRAFTING_XP_REMAINDER_COLUMN).append(" INT NOT NULL DEFAULT 0");
        sql.append(", ").append(EXPLORATION_DISTANCE_REMAINDER_COLUMN).append(" INT NOT NULL DEFAULT 0");
        sql.append(')');
        return sql.toString();
    }

    private void addProgressColumnsIfMissing(Connection connection) {
        for (SkillType skillType : SkillType.values()) {
            addColumnIfMissing(connection, skillType.columnName(), "BIGINT NOT NULL DEFAULT 0");
        }
        addColumnIfMissing(connection, MAPLE_XP_COLUMN, "BIGINT NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, COMBAT_XP_REMAINDER_COLUMN, "INT NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, MINING_XP_REMAINDER_COLUMN, "INT NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, FORAGING_XP_REMAINDER_COLUMN, "INT NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, FARMING_XP_REMAINDER_COLUMN, "INT NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, FISHING_XP_REMAINDER_COLUMN, "INT NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, ALCHEMY_XP_REMAINDER_COLUMN, "INT NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, CRAFTING_XP_REMAINDER_COLUMN, "INT NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, EXPLORATION_DISTANCE_REMAINDER_COLUMN, "INT NOT NULL DEFAULT 0");
    }

    private void addColumnIfMissing(Connection connection, String name, String typeSql) {
        try (PreparedStatement ps = connection.prepareStatement(
                "ALTER TABLE " + PROGRESS_TABLE + " ADD COLUMN " + name + " " + typeSql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            String message = e.getMessage();
            if (message == null || !message.toLowerCase().contains("duplicate column")) {
                logger.log(Level.WARNING, "SkillProgressDB add " + name, e);
            }
        }
    }

    private static long getLongOr(ResultSet rs, String column, long defaultValue) {
        try {
            Object value = rs.getObject(column);
            if (value == null) {
                return defaultValue;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            return rs.getLong(column);
        } catch (SQLException e) {
            return defaultValue;
        }
    }

    private static int getIntOr(ResultSet rs, String column, int defaultValue) {
        try {
            Object value = rs.getObject(column);
            if (value == null) {
                return defaultValue;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
            return rs.getInt(column);
        } catch (SQLException e) {
            return defaultValue;
        }
    }
}
