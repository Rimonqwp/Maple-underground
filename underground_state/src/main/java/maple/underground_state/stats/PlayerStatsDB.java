package maple.underground_state.stats;

import maple.underground_state.db.MySqlConnectionFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlayerStatsDB {

    private static final String TABLE = "player_stats";

    private final Logger logger;

    public PlayerStatsDB(File dataFolder, Logger logger) {
        this.logger = logger != null ? logger : Logger.getAnonymousLogger();
        MySqlConnectionFactory.logTarget(this.logger);
        init();
    }

    private Connection connect() throws SQLException {
        return MySqlConnectionFactory.connect();
    }

    private void init() {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "health DOUBLE NOT NULL DEFAULT 20," +
                "current_health DOUBLE NOT NULL DEFAULT 20," +
                "defense DOUBLE NOT NULL DEFAULT 0," +
                "strength DOUBLE NOT NULL DEFAULT 0," +
                "intelligence DOUBLE NOT NULL DEFAULT 100," +
                "current_intelligence DOUBLE NOT NULL DEFAULT 100," +
                "crit_damage DOUBLE NOT NULL DEFAULT 50," +
                "bonus_attack_speed DOUBLE NOT NULL DEFAULT 0," +
                "true_defense DOUBLE NOT NULL DEFAULT 0," +
                "health_regen DOUBLE NOT NULL DEFAULT 100," +
                "ferocity DOUBLE NOT NULL DEFAULT 0," +
                "ability_damage DOUBLE NOT NULL DEFAULT 0," +
                "speed DOUBLE NOT NULL DEFAULT 100," +
                "vitality DOUBLE NOT NULL DEFAULT 100," +
                "mending DOUBLE NOT NULL DEFAULT 100," +
                "monster_damage_percent DOUBLE NOT NULL DEFAULT 0," +
                "mining_fortune DOUBLE NOT NULL DEFAULT 0," +
                "foraging_fortune DOUBLE NOT NULL DEFAULT 0," +
                "farming_fortune DOUBLE NOT NULL DEFAULT 0," +
                "treasure_chance DOUBLE NOT NULL DEFAULT 0," +
                "bonus_exp_percent DOUBLE NOT NULL DEFAULT 0," +
                "potion_duration_percent DOUBLE NOT NULL DEFAULT 0," +
                "pet_luck DOUBLE NOT NULL DEFAULT 0," +
                "pet_xp_gain_percent DOUBLE NOT NULL DEFAULT 0," +
                "exp_share_rate_percent DOUBLE NOT NULL DEFAULT 0," +
                "crit_chance DOUBLE NOT NULL DEFAULT 30)";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "PlayerStatsDB init failed", e);
        }
        addColumnsIfMissing();
    }

    private void addColumnsIfMissing() {
        try (Connection c = connect()) {
            addColumnIfMissing(c, "health", "DOUBLE NOT NULL DEFAULT 20");
            addColumnIfMissing(c, "current_health", "DOUBLE NOT NULL DEFAULT 20");
            addColumnIfMissing(c, "intelligence", "DOUBLE NOT NULL DEFAULT 100");
            addColumnIfMissing(c, "current_intelligence", "DOUBLE NOT NULL DEFAULT 100");
            addColumnIfMissing(c, "ability_damage", "DOUBLE NOT NULL DEFAULT 0");
            addColumnIfMissing(c, "speed", "DOUBLE NOT NULL DEFAULT 100");
            addColumnIfMissing(c, "crit_chance", "DOUBLE NOT NULL DEFAULT 30");
            addColumnIfMissing(c, "vitality", "DOUBLE NOT NULL DEFAULT 100");
            addColumnIfMissing(c, "mending", "DOUBLE NOT NULL DEFAULT 100");
            addColumnIfMissing(c, "monster_damage_percent", "DOUBLE NOT NULL DEFAULT 0");
            addColumnIfMissing(c, "mining_fortune", "DOUBLE NOT NULL DEFAULT 0");
            addColumnIfMissing(c, "foraging_fortune", "DOUBLE NOT NULL DEFAULT 0");
            addColumnIfMissing(c, "farming_fortune", "DOUBLE NOT NULL DEFAULT 0");
            addColumnIfMissing(c, "treasure_chance", "DOUBLE NOT NULL DEFAULT 0");
            addColumnIfMissing(c, "bonus_exp_percent", "DOUBLE NOT NULL DEFAULT 0");
            addColumnIfMissing(c, "potion_duration_percent", "DOUBLE NOT NULL DEFAULT 0");
            addColumnIfMissing(c, "pet_luck", "DOUBLE NOT NULL DEFAULT 0");
            addColumnIfMissing(c, "pet_xp_gain_percent", "DOUBLE NOT NULL DEFAULT 0");
            addColumnIfMissing(c, "exp_share_rate_percent", "DOUBLE NOT NULL DEFAULT 0");
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + TABLE + " SET health_regen = 100 WHERE health_regen = 0")) {
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "PlayerStatsDB migrate health_regen default", e);
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "PlayerStatsDB addColumnsIfMissing", e);
        }
    }

    private void addColumnIfMissing(Connection connection, String name, String typeSql) {
        try (PreparedStatement ps = connection.prepareStatement("ALTER TABLE " + TABLE + " ADD COLUMN " + name + " " + typeSql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            String message = e.getMessage();
            if (message == null || !message.toLowerCase().contains("duplicate column")) {
                logger.log(Level.WARNING, "PlayerStatsDB add " + name, e);
            }
        }
    }

    public PlayerStatsData load(UUID uuid) {
        PlayerStatsData data = new PlayerStatsData();
        if (uuid == null) return data;

        String sql = "SELECT * FROM " + TABLE + " WHERE uuid=?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    data.setHealth(getDoubleOr(rs, "health", PlayerStatsData.DEFAULT_HEALTH));
                    data.setDefense(rs.getDouble("defense"));
                    data.setStrength(rs.getDouble("strength"));
                    data.setIntelligence(getDoubleOr(rs, "intelligence", PlayerStatsData.DEFAULT_INTELLIGENCE));
                    data.setCritDamage(rs.getDouble("crit_damage"));
                    data.setBonusAttackSpeed(rs.getDouble("bonus_attack_speed"));
                    data.setTrueDefense(rs.getDouble("true_defense"));
                    data.setHealthRegen(getDoubleOr(rs, "health_regen", PlayerStatsData.DEFAULT_HEALTH_REGEN));
                    data.setFerocity(rs.getDouble("ferocity"));
                    data.setAbilityDamage(getDoubleOr(rs, "ability_damage", PlayerStatsData.DEFAULT_ABILITY_DAMAGE));
                    data.setSpeed(getDoubleOr(rs, "speed", PlayerStatsData.DEFAULT_SPEED));
                    data.setCritChance(getDoubleOr(rs, "crit_chance", PlayerStatsData.DEFAULT_CRIT_CHANCE));
                    data.setMonsterDamagePercent(getDoubleOr(rs, "monster_damage_percent", PlayerStatsData.DEFAULT_MONSTER_DAMAGE_PERCENT));
                    data.setMiningFortune(getDoubleOr(rs, "mining_fortune", PlayerStatsData.DEFAULT_MINING_FORTUNE));
                    data.setForagingFortune(getDoubleOr(rs, "foraging_fortune", PlayerStatsData.DEFAULT_FORAGING_FORTUNE));
                    data.setFarmingFortune(getDoubleOr(rs, "farming_fortune", PlayerStatsData.DEFAULT_FARMING_FORTUNE));
                    data.setTreasureChance(getDoubleOr(rs, "treasure_chance", PlayerStatsData.DEFAULT_TREASURE_CHANCE));
                    data.setBonusExpPercent(getDoubleOr(rs, "bonus_exp_percent", PlayerStatsData.DEFAULT_BONUS_EXP_PERCENT));
                    data.setPotionDurationPercent(getDoubleOr(rs, "potion_duration_percent", PlayerStatsData.DEFAULT_POTION_DURATION_PERCENT));
                    data.setPetLuck(getDoubleOr(rs, "pet_luck", PlayerStatsData.DEFAULT_PET_LUCK));
                    data.setPetXpGainPercent(getDoubleOr(rs, "pet_xp_gain_percent", PlayerStatsData.DEFAULT_PET_XP_GAIN_PERCENT));
                    data.setExpShareRatePercent(getDoubleOr(rs, "exp_share_rate_percent", PlayerStatsData.DEFAULT_EXP_SHARE_RATE_PERCENT));
                    double currentHealth = getDoubleOr(rs, "current_health", PlayerStatsData.DEFAULT_HEALTH);
                    double currentIntelligence = getDoubleOr(rs, "current_intelligence", PlayerStatsData.DEFAULT_INTELLIGENCE);
                    data.setCurrentHealthWithCap(currentHealth, Math.max(data.getHealth(), currentHealth));
                    data.setCurrentIntelligenceWithCap(currentIntelligence, Math.max(data.getIntelligence(), currentIntelligence));
                    data.setVitality(getDoubleOr(rs, "vitality", PlayerStatsData.DEFAULT_VITALITY));
                    data.setMending(getDoubleOr(rs, "mending", PlayerStatsData.DEFAULT_MENDING));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "PlayerStatsDB load failed for " + uuid, e);
        }
        return data;
    }

    public PlayerStatsData loadOrCreate(UUID uuid) {
        PlayerStatsData data = new PlayerStatsData();
        if (uuid == null) return data;

        String ensureSql = "INSERT INTO " + TABLE + " (uuid) VALUES (?) ON DUPLICATE KEY UPDATE uuid = uuid";
        String loadSql = "SELECT * FROM " + TABLE + " WHERE uuid=?";
        try (Connection c = connect()) {
            try (PreparedStatement ensure = c.prepareStatement(ensureSql)) {
                ensure.setString(1, uuid.toString());
                ensure.executeUpdate();
            }
            try (PreparedStatement load = c.prepareStatement(loadSql)) {
                load.setString(1, uuid.toString());
                try (ResultSet rs = load.executeQuery()) {
                    if (rs.next()) {
                        data.setHealth(getDoubleOr(rs, "health", PlayerStatsData.DEFAULT_HEALTH));
                        data.setDefense(rs.getDouble("defense"));
                        data.setStrength(rs.getDouble("strength"));
                        data.setIntelligence(getDoubleOr(rs, "intelligence", PlayerStatsData.DEFAULT_INTELLIGENCE));
                        data.setCritDamage(rs.getDouble("crit_damage"));
                        data.setBonusAttackSpeed(rs.getDouble("bonus_attack_speed"));
                        data.setTrueDefense(rs.getDouble("true_defense"));
                        data.setHealthRegen(getDoubleOr(rs, "health_regen", PlayerStatsData.DEFAULT_HEALTH_REGEN));
                        data.setFerocity(rs.getDouble("ferocity"));
                        data.setAbilityDamage(getDoubleOr(rs, "ability_damage", PlayerStatsData.DEFAULT_ABILITY_DAMAGE));
                        data.setSpeed(getDoubleOr(rs, "speed", PlayerStatsData.DEFAULT_SPEED));
                        data.setCritChance(getDoubleOr(rs, "crit_chance", PlayerStatsData.DEFAULT_CRIT_CHANCE));
                        data.setMonsterDamagePercent(getDoubleOr(rs, "monster_damage_percent", PlayerStatsData.DEFAULT_MONSTER_DAMAGE_PERCENT));
                        data.setMiningFortune(getDoubleOr(rs, "mining_fortune", PlayerStatsData.DEFAULT_MINING_FORTUNE));
                        data.setForagingFortune(getDoubleOr(rs, "foraging_fortune", PlayerStatsData.DEFAULT_FORAGING_FORTUNE));
                        data.setFarmingFortune(getDoubleOr(rs, "farming_fortune", PlayerStatsData.DEFAULT_FARMING_FORTUNE));
                        data.setTreasureChance(getDoubleOr(rs, "treasure_chance", PlayerStatsData.DEFAULT_TREASURE_CHANCE));
                        data.setBonusExpPercent(getDoubleOr(rs, "bonus_exp_percent", PlayerStatsData.DEFAULT_BONUS_EXP_PERCENT));
                        data.setPotionDurationPercent(getDoubleOr(rs, "potion_duration_percent", PlayerStatsData.DEFAULT_POTION_DURATION_PERCENT));
                        data.setPetLuck(getDoubleOr(rs, "pet_luck", PlayerStatsData.DEFAULT_PET_LUCK));
                        data.setPetXpGainPercent(getDoubleOr(rs, "pet_xp_gain_percent", PlayerStatsData.DEFAULT_PET_XP_GAIN_PERCENT));
                        data.setExpShareRatePercent(getDoubleOr(rs, "exp_share_rate_percent", PlayerStatsData.DEFAULT_EXP_SHARE_RATE_PERCENT));
                        double currentHealth = getDoubleOr(rs, "current_health", PlayerStatsData.DEFAULT_HEALTH);
                        double currentIntelligence = getDoubleOr(rs, "current_intelligence", PlayerStatsData.DEFAULT_INTELLIGENCE);
                        data.setCurrentHealthWithCap(currentHealth, Math.max(data.getHealth(), currentHealth));
                        data.setCurrentIntelligenceWithCap(currentIntelligence, Math.max(data.getIntelligence(), currentIntelligence));
                        data.setVitality(getDoubleOr(rs, "vitality", PlayerStatsData.DEFAULT_VITALITY));
                        data.setMending(getDoubleOr(rs, "mending", PlayerStatsData.DEFAULT_MENDING));
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "PlayerStatsDB loadOrCreate failed for " + uuid, e);
        }
        return data;
    }

    public void ensureExists(UUID uuid) {
        if (uuid == null) return;

        String sql = "INSERT INTO " + TABLE + " (uuid) VALUES (?) " +
                "ON DUPLICATE KEY UPDATE uuid = uuid";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "PlayerStatsDB ensureExists failed for " + uuid, e);
        }
    }

    public void save(UUID uuid, PlayerStatsData data) {
        if (uuid == null || data == null) return;

        String sql = "REPLACE INTO " + TABLE +
                " (uuid, health, current_health, defense, strength, intelligence, current_intelligence, crit_damage, crit_chance, bonus_attack_speed, true_defense, health_regen, ferocity, ability_damage, speed, vitality, mending, monster_damage_percent, mining_fortune, foraging_fortune, farming_fortune, treasure_chance, bonus_exp_percent, potion_duration_percent, pet_luck, pet_xp_gain_percent, exp_share_rate_percent)" +
                " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setDouble(2, data.getHealth());
            ps.setDouble(3, data.getCurrentHealth());
            ps.setDouble(4, data.getDefense());
            ps.setDouble(5, data.getStrength());
            ps.setDouble(6, data.getIntelligence());
            ps.setDouble(7, data.getCurrentIntelligence());
            ps.setDouble(8, data.getCritDamage());
            ps.setDouble(9, data.getCritChance());
            ps.setDouble(10, data.getBonusAttackSpeed());
            ps.setDouble(11, data.getTrueDefense());
            ps.setDouble(12, data.getHealthRegen());
            ps.setDouble(13, data.getFerocity());
            ps.setDouble(14, data.getAbilityDamage());
            ps.setDouble(15, data.getSpeed());
            ps.setDouble(16, data.getVitality());
            ps.setDouble(17, data.getMending());
            ps.setDouble(18, data.getMonsterDamagePercent());
            ps.setDouble(19, data.getMiningFortune());
            ps.setDouble(20, data.getForagingFortune());
            ps.setDouble(21, data.getFarmingFortune());
            ps.setDouble(22, data.getTreasureChance());
            ps.setDouble(23, data.getBonusExpPercent());
            ps.setDouble(24, data.getPotionDurationPercent());
            ps.setDouble(25, data.getPetLuck());
            ps.setDouble(26, data.getPetXpGainPercent());
            ps.setDouble(27, data.getExpShareRatePercent());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "PlayerStatsDB save failed for " + uuid, e);
        }
    }

    private static double getDoubleOr(ResultSet rs, String column, double defaultValue) {
        try {
            Object value = rs.getObject(column);
            if (value == null) return defaultValue;
            if (value instanceof Number number) return number.doubleValue();
            return rs.getDouble(column);
        } catch (SQLException e) {
            return defaultValue;
        }
    }
}
