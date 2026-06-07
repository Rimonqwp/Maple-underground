package maple.underground_state.backpack;

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

public final class BackpackDataDB {

    private static final String TABLE = "player_backpack_slots";
    private static final String BAG_TABLE = "player_backpack_bags";

    private final Logger logger;

    public BackpackDataDB(File dataFolder, Logger logger) {
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
                "box_index INT NOT NULL," +
                "slot_index INT NOT NULL," +
                "item MEDIUMBLOB NULL," +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "PRIMARY KEY (uuid, box_index, slot_index)" +
                ")";
        String bagSql = "CREATE TABLE IF NOT EXISTS " + BAG_TABLE + " (" +
                "uuid VARCHAR(36) NOT NULL," +
                "overview_slot INT NOT NULL," +
                "bag_rows TINYINT NOT NULL," +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "PRIMARY KEY (uuid, overview_slot)" +
                ")";
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement(sql);
             PreparedStatement bagPs = connection.prepareStatement(bagSql)) {
            ps.executeUpdate();
            bagPs.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "BackpackDataDB init failed", e);
        }
    }

    public @NotNull ItemStack[] load(@NotNull UUID uuid, int boxIndex, int slotCount) {
        int safeSlotCount = Math.max(0, slotCount);
        ItemStack[] items = new ItemStack[safeSlotCount];
        if (safeSlotCount == 0) {
            return items;
        }

        String sql = "SELECT slot_index, item FROM " + TABLE + " WHERE uuid=? AND box_index=? AND slot_index<?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, boxIndex);
            ps.setInt(3, safeSlotCount);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt("slot_index");
                    if (slot < 0 || slot >= safeSlotCount) {
                        continue;
                    }
                    items[slot] = deserialize(rs.getBytes("item"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "BackpackDataDB load failed for " + uuid + " box " + boxIndex, e);
        }
        return items;
    }

    public void save(@NotNull UUID uuid, int boxIndex, @NotNull ItemStack[] items) {
        String upsertSql = "INSERT INTO " + TABLE + " (uuid, box_index, slot_index, item) VALUES (?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE item=VALUES(item)";
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM " + TABLE + " WHERE uuid=? AND box_index=?");
                 PreparedStatement upsert = connection.prepareStatement(upsertSql)) {
                delete.setString(1, uuid.toString());
                delete.setInt(2, boxIndex);
                delete.executeUpdate();

                for (int slot = 0; slot < items.length; slot++) {
                    ItemStack item = items[slot];
                    if (isEmpty(item)) {
                        continue;
                    }
                    byte[] serialized = serialize(item);
                    if (serialized == null || serialized.length == 0) {
                        continue;
                    }
                    upsert.setString(1, uuid.toString());
                    upsert.setInt(2, boxIndex);
                    upsert.setInt(3, slot);
                    upsert.setBytes(4, serialized);
                    upsert.executeUpdate();
                }
            }
            connection.commit();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "BackpackDataDB save failed for " + uuid + " box " + boxIndex, e);
        }
    }

    public @NotNull BagSlot[] loadBagSlots(@NotNull UUID uuid, int minSlot, int maxSlot) {
        int safeMin = Math.min(minSlot, maxSlot);
        int safeMax = Math.max(minSlot, maxSlot);
        BagSlot[] slots = new BagSlot[safeMax - safeMin + 1];
        String sql = "SELECT overview_slot, bag_rows FROM " + BAG_TABLE +
                " WHERE uuid=? AND overview_slot>=? AND overview_slot<=?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, safeMin);
            ps.setInt(3, safeMax);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int overviewSlot = rs.getInt("overview_slot");
                    int rows = rs.getInt("bag_rows");
                    if (overviewSlot < safeMin || overviewSlot > safeMax || rows < 1 || rows > 5) {
                        continue;
                    }
                    slots[overviewSlot - safeMin] = new BagSlot(overviewSlot, rows);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "BackpackDataDB load bag slots failed for " + uuid, e);
        }
        return slots;
    }

    public @Nullable BagSlot loadBagSlot(@NotNull UUID uuid, int overviewSlot) {
        String sql = "SELECT bag_rows FROM " + BAG_TABLE + " WHERE uuid=? AND overview_slot=?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, overviewSlot);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int rows = rs.getInt("bag_rows");
                    if (rows >= 1 && rows <= 5) {
                        return new BagSlot(overviewSlot, rows);
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "BackpackDataDB load bag slot failed for " + uuid + " slot " + overviewSlot, e);
        }
        return null;
    }

    public boolean saveBagSlot(@NotNull UUID uuid, int overviewSlot, int rows) {
        if (rows < 1 || rows > 5) {
            return false;
        }
        String sql = "INSERT INTO " + BAG_TABLE + " (uuid, overview_slot, bag_rows) VALUES (?,?,?) " +
                "ON DUPLICATE KEY UPDATE bag_rows=VALUES(bag_rows)";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, overviewSlot);
            ps.setInt(3, rows);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "BackpackDataDB save bag slot failed for " + uuid + " slot " + overviewSlot, e);
            return false;
        }
    }

    public boolean deleteBagSlot(@NotNull UUID uuid, int overviewSlot) {
        String sql = "DELETE FROM " + BAG_TABLE + " WHERE uuid=? AND overview_slot=?";
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, overviewSlot);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "BackpackDataDB delete bag slot failed for " + uuid + " slot " + overviewSlot, e);
            return false;
        }
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
            logger.log(Level.WARNING, "BackpackDataDB item deserialize failed", e);
            return null;
        }
    }

    private static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    public record BagSlot(int overviewSlot, int rows) {
    }
}
