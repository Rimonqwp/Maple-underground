package maple.underground_state.professions;

import maple.underground_state.db.MySqlConnectionFactory;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.*;

/** 缓存计数器与图鉴，采集热路径不连接数据库。加载/定期落盘由服务安排。 */
public class ProfessionStore {
    private final Logger logger;
    @FunctionalInterface interface Connections { Connection connect() throws SQLException; }
    private final Connections connections;
    private final Map<UUID, Map<String, Long>> cache = new ConcurrentHashMap<>();
    public ProfessionStore(Logger logger) {
        this(logger,MySqlConnectionFactory::connect);
    }
    ProfessionStore(Logger logger,Connections connections) {
        this.logger = logger;
        this.connections=connections;
        try (Connection c = connections.connect(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS player_profession_progress (uuid VARCHAR(36) NOT NULL, progress_key VARCHAR(160) NOT NULL, amount BIGINT NOT NULL DEFAULT 0, PRIMARY KEY(uuid, progress_key))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS player_profession_equipment_collection (uuid VARCHAR(36) NOT NULL, item_id VARCHAR(120) NOT NULL, discovered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(uuid, item_id))");
        } catch (SQLException e) { throw new IllegalStateException("职业存档初始化失败", e); }
    }
    protected ProfessionStore() { logger = Logger.getLogger(ProfessionStore.class.getName());connections=MySqlConnectionFactory::connect; }
    protected void cacheLoaded(UUID uuid,Map<String,Long> data) { cache.put(uuid,new ConcurrentHashMap<>(data)); }
    public boolean ready(UUID uuid) { return cache.containsKey(uuid); }
    public synchronized void load(UUID uuid) {
        if (ready(uuid)) return;
        Map<String, Long> data = new ConcurrentHashMap<>();
        try (Connection c = connections.connect()) {
            try (PreparedStatement p = c.prepareStatement("SELECT progress_key,amount FROM player_profession_progress WHERE uuid=?")) {
                p.setString(1, uuid.toString());
                try (ResultSet r = p.executeQuery()) { while (r.next()) data.put(r.getString(1), r.getLong(2)); }
            }
            try (PreparedStatement p = c.prepareStatement("SELECT item_id FROM player_profession_equipment_collection WHERE uuid=?")) {
                p.setString(1, uuid.toString());
                try (ResultSet r = p.executeQuery()) { while (r.next()) data.put("book:" + r.getString(1), 1L); }
            }
            cacheLoaded(uuid,data);
        } catch (SQLException e) { logger.log(Level.WARNING, "职业存档加载失败 " + uuid, e); }
    }
    public long get(UUID uuid, String key) { return cache.getOrDefault(uuid, Map.of()).getOrDefault(key, 0L); }
    public void set(UUID uuid, String key, long value) {
        Map<String, Long> data = cache.get(uuid);
        if (data == null) throw new IllegalStateException("职业存档尚未加载");
        data.put(key, Math.max(0, value));
    }
    public long add(UUID uuid, String key, long delta) { long v = get(uuid, key) + delta; set(uuid, key, v); return v; }
    public Set<String> discoveries(UUID uuid) {
        Set<String> ids = new HashSet<>();
        cache.getOrDefault(uuid, Map.of()).forEach((k, v) -> { if (k.startsWith("book:") && v > 0) ids.add(k.substring(5)); });
        return Set.copyOf(ids);
    }
    public void saveAll() { for (UUID uuid : cache.keySet()) save(uuid); }
    public synchronized void save(UUID uuid) {
        Map<String, Long> data = cache.get(uuid);
        if (data == null) return;
        Map<String, Long> snapshot = new HashMap<>(data);
        try (Connection c = connections.connect()) {
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement("INSERT INTO player_profession_progress(uuid,progress_key,amount) VALUES(?,?,?) ON DUPLICATE KEY UPDATE amount=VALUES(amount)");
                 PreparedStatement b = c.prepareStatement("INSERT IGNORE INTO player_profession_equipment_collection(uuid,item_id) VALUES(?,?)")) {
                for (var entry : snapshot.entrySet()) {
                    if (entry.getKey().startsWith("book:")) {
                        b.setString(1, uuid.toString()); b.setString(2, entry.getKey().substring(5)); b.addBatch();
                    } else {
                        p.setString(1, uuid.toString()); p.setString(2, entry.getKey()); p.setLong(3, entry.getValue()); p.addBatch();
                    }
                }
                p.executeBatch(); b.executeBatch(); c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
        } catch (SQLException e) { logger.log(Level.WARNING, "职业存档保存失败 " + uuid, e); }
    }
}
