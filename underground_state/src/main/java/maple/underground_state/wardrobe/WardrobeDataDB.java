package maple.underground_state.wardrobe;

import maple.underground_state.db.MySqlConnectionFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WardrobeDataDB {

    private static final String TABLE = "player_wardrobes";
    private static final int SET_COUNT = 9;
    private static final int PIECE_COUNT = 4;

    private final Logger logger;

    public WardrobeDataDB(File dataFolder, Logger logger) {
        this.logger = logger != null ? logger : Logger.getAnonymousLogger();
        MySqlConnectionFactory.logTarget(this.logger);
        init();
    }

    private Connection connect() throws SQLException {
        return MySqlConnectionFactory.connect();
    }

    private void init() {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                "uuid VARCHAR(36) NOT NULL," +
                "wardrobe_slot TINYINT NOT NULL," +
                "active_slot TINYINT NOT NULL DEFAULT -1," +
                "helmet MEDIUMBLOB NULL," +
                "chestplate MEDIUMBLOB NULL," +
                "leggings MEDIUMBLOB NULL," +
                "boots MEDIUMBLOB NULL," +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "PRIMARY KEY (uuid, wardrobe_slot)" +
                ")";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "WardrobeDataDB init failed", e);
        }
    }

    public @NotNull WardrobeSnapshot load(@NotNull UUID uuid) {
        WardrobeSnapshot snapshot = new WardrobeSnapshot();
        String sql = "SELECT wardrobe_slot, active_slot, helmet, chestplate, leggings, boots FROM " + TABLE + " WHERE uuid=?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt("wardrobe_slot");
                    if (slot < 0 || slot >= SET_COUNT) {
                        continue;
                    }
                    snapshot.activeSlot = Math.max(-1, Math.min(SET_COUNT - 1, rs.getInt("active_slot")));
                    snapshot.sets[slot][0] = deserialize(rs.getBytes("helmet"));
                    snapshot.sets[slot][1] = deserialize(rs.getBytes("chestplate"));
                    snapshot.sets[slot][2] = deserialize(rs.getBytes("leggings"));
                    snapshot.sets[slot][3] = deserialize(rs.getBytes("boots"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "WardrobeDataDB load failed for " + uuid, e);
        }
        return snapshot;
    }

    public void save(@NotNull UUID uuid, @NotNull WardrobeSnapshot snapshot) {
        String sql = "INSERT INTO " + TABLE +
                " (uuid, wardrobe_slot, active_slot, helmet, chestplate, leggings, boots) VALUES (?,?,?,?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE active_slot=VALUES(active_slot), helmet=VALUES(helmet), " +
                "chestplate=VALUES(chestplate), leggings=VALUES(leggings), boots=VALUES(boots)";
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (PreparedStatement deleteEmpty = connection.prepareStatement(
                    "DELETE FROM " + TABLE + " WHERE uuid=? AND wardrobe_slot=?");
                 PreparedStatement upsert = connection.prepareStatement(sql)) {
                for (int slot = 0; slot < SET_COUNT; slot++) {
                    if (isEmptySet(snapshot.sets[slot])) {
                        deleteEmpty.setString(1, uuid.toString());
                        deleteEmpty.setInt(2, slot);
                        deleteEmpty.executeUpdate();
                        continue;
                    }
                    upsert.setString(1, uuid.toString());
                    upsert.setInt(2, slot);
                    upsert.setInt(3, snapshot.activeSlot);
                    upsert.setBytes(4, serialize(snapshot.sets[slot][0]));
                    upsert.setBytes(5, serialize(snapshot.sets[slot][1]));
                    upsert.setBytes(6, serialize(snapshot.sets[slot][2]));
                    upsert.setBytes(7, serialize(snapshot.sets[slot][3]));
                    upsert.executeUpdate();
                }
            }
            connection.commit();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "WardrobeDataDB save failed for " + uuid, e);
        }
    }

    private static boolean isEmptySet(@NotNull ItemStack[] items) {
        for (int index = 0; index < PIECE_COUNT; index++) {
            if (!isEmpty(items[index])) {
                return false;
            }
        }
        return true;
    }

    private static byte @Nullable [] serialize(@Nullable ItemStack item) {
        if (isEmpty(item)) {
            return null;
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(item);
            return bytes.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private @Nullable ItemStack deserialize(byte @Nullable [] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try (ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream input = new BukkitObjectInputStream(inputBytes)) {
            Object object = input.readObject();
            return object instanceof ItemStack item ? item : null;
        } catch (IOException | ClassNotFoundException e) {
            logger.log(Level.WARNING, "WardrobeDataDB item deserialize failed", e);
            return null;
        }
    }

    private static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    public static final class WardrobeSnapshot {
        private final ItemStack[][] sets = new ItemStack[SET_COUNT][PIECE_COUNT];
        private int activeSlot = -1;

        public @NotNull ItemStack[][] sets() {
            return sets;
        }

        public int activeSlot() {
            return activeSlot;
        }

        public void activeSlot(int activeSlot) {
            this.activeSlot = Math.max(-1, Math.min(SET_COUNT - 1, activeSlot));
        }
    }
}
