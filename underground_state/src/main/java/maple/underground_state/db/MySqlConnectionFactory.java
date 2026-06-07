package maple.underground_state.db;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * 全系列插件唯一的 MySQL 连线入口（集中凭据注册表）。
 *
 * <p>主插件 underground_state 是唯一持有数据库凭据的地方：凭据在编译期由 Maven 资源过滤
 * 写入 jar 内的 {@code database.properties}（真实值来自 build-secrets.cmd -> build-all.cmd）。
 * 副插件 <b>不得</b> 自带任何 {@code DriverManager.getConnection} 或硬编码帐密，一律呼叫本类。</p>
 *
 * <p>用法：</p>
 * <pre>
 *   MySqlConnectionFactory.connect();            // 预设数据源（state 库）
 *   MySqlConnectionFactory.connect("caves");     // 命名数据源
 *   MySqlConnectionFactory.connect("skyblock");
 * </pre>
 *
 * <p>新增数据源：在 database.properties 加一组 {@code db.<name>.*}，在 underground_state/pom.xml
 * 加同名默认值，在 build-secrets.cmd(.example) 与 build-all.cmd 加对应 {@code -D} 参数，然后
 * 呼叫 {@code connect("<name>")}。所有数据源共用同一台 host。</p>
 */
public final class MySqlConnectionFactory {

    /** 预设数据源键名（向后相容：connect() 等同 connect(DEFAULT_KEY)）。 */
    public static final String DEFAULT_KEY = "state";

    private static final String PROPERTIES_RESOURCE = "/database.properties";
    private static final int CONNECT_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 250L;

    // 资源缺失或未被过滤时的程式内兜底值（与 pom 预设保持一致）。
    private static final String FALLBACK_HOST = "api.maplenw.top";
    private static final String FALLBACK_PORT = "3306";
    private static final String FALLBACK_PARAMS =
            "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Hong_Kong&characterEncoding=utf8"
                    + "&connectTimeout=3000&socketTimeout=5000&tcpKeepAlive=true";

    private static final Map<String, DataSource> SOURCES = loadSources();

    private static volatile boolean driverLoaded;
    private static volatile boolean targetLogged;

    private MySqlConnectionFactory() {
    }

    /** 不可变的数据源定义。 */
    private static final class DataSource {
        final String key;
        final String host;
        final String port;
        final String url;
        final String username;
        final String password;

        DataSource(String key, String host, String port, String url, String username, String password) {
            this.key = key;
            this.host = host;
            this.port = port;
            this.url = url;
            this.username = username;
            this.password = password;
        }
    }

    private static Map<String, DataSource> loadSources() {
        Properties props = new Properties();
        try (InputStream in = MySqlConnectionFactory.class.getResourceAsStream(PROPERTIES_RESOURCE)) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception ignored) {
            // 读取失败时退回兜底值，不让插件因设定问题崩溃。
        }

        String host = value(props.getProperty("db.host"), FALLBACK_HOST);
        String port = value(props.getProperty("db.port"), FALLBACK_PORT);
        String params = value(props.getProperty("db.params"), FALLBACK_PARAMS);

        Map<String, DataSource> map = new HashMap<>();
        // 目前的三个 schema；新增数据源时在此追加一行 putSource(...)。
        putSource(map, props, host, port, params, "state", "underground_state", "underground_state");
        putSource(map, props, host, port, params, "caves", "underground_caves", "underground_caves");
        putSource(map, props, host, port, params, "skyblock", "skyblock", "skyblock");
        return Collections.unmodifiableMap(map);
    }

    private static void putSource(Map<String, DataSource> map, Properties props,
                                  String host, String port, String params,
                                  String key, String defaultDatabase, String defaultUser) {
        String database = value(props.getProperty("db." + key + ".database"), defaultDatabase);
        String username = value(props.getProperty("db." + key + ".user"), defaultUser);
        String password = resolvePassword(props.getProperty("db." + key + ".password"), key);
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + params;
        map.put(key, new DataSource(key, host, port, url, username, password));
    }

    /**
     * 解析密码：以编译期烧入的值为准；若为空则回退到环境变数
     * {@code UNDERGROUND_<KEY>_MYSQL_PASSWORD}（保留既有开发/运维习惯，不破坏在跑的服）。
     */
    private static String resolvePassword(String baked, String key) {
        String cleaned = clean(baked);
        if (!cleaned.isEmpty()) {
            return cleaned;
        }
        String env = System.getenv("UNDERGROUND_" + key.toUpperCase(Locale.ROOT) + "_MYSQL_PASSWORD");
        return env != null ? env.trim() : "";
    }

    /** 取设定值；空白或未被过滤的占位符（${...}）视为缺失，改用 fallback。 */
    private static String value(String raw, String fallback) {
        String cleaned = clean(raw);
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || (trimmed.startsWith("${") && trimmed.endsWith("}"))) {
            return "";
        }
        return trimmed;
    }

    /** 取得预设数据源（state 库）的连线。 */
    public static Connection connect() throws SQLException {
        return connect(DEFAULT_KEY);
    }

    /** 取得指定命名数据源的连线（如 "caves"、"skyblock"）。 */
    public static Connection connect(String key) throws SQLException {
        DataSource source = SOURCES.get(key);
        if (source == null) {
            throw new SQLException("Unknown MySQL data source: " + key
                    + " (known: " + SOURCES.keySet() + ")");
        }
        ensureDriverLoaded();
        SQLException last = null;
        for (int attempt = 1; attempt <= CONNECT_ATTEMPTS; attempt++) {
            try {
                return DriverManager.getConnection(source.url, source.username, source.password);
            } catch (SQLException e) {
                last = e;
                if (attempt >= CONNECT_ATTEMPTS || !isRetryable(e)) {
                    throw e;
                }
                sleepBeforeRetry();
            }
        }
        throw last != null ? last : new SQLException("Failed to connect to MySQL: " + key);
    }

    public static void logTarget(Logger logger) {
        if (logger == null || targetLogged) {
            return;
        }
        synchronized (MySqlConnectionFactory.class) {
            if (targetLogged) {
                return;
            }
            DataSource state = SOURCES.get(DEFAULT_KEY);
            if (state != null) {
                logger.info("Using MySQL host " + state.host + ":" + state.port
                        + " (data sources: " + SOURCES.keySet() + ")");
            }
            targetLogged = true;
        }
    }

    private static void ensureDriverLoaded() {
        if (driverLoaded) {
            return;
        }
        synchronized (MySqlConnectionFactory.class) {
            if (driverLoaded) {
                return;
            }
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                driverLoaded = true;
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("MySQL JDBC driver not found", e);
            }
        }
    }

    private static boolean isRetryable(SQLException exception) {
        if (exception == null) {
            return false;
        }
        String sqlState = exception.getSQLState();
        if (sqlState != null && sqlState.startsWith("08")) {
            return true;
        }
        String message = exception.getMessage();
        if (message != null) {
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("communications link failure")
                    || lower.contains("connect timed out")
                    || lower.contains("connection timed out")
                    || lower.contains("connection refused")) {
                return true;
            }
        }
        Throwable cause = exception.getCause();
        return cause instanceof SQLException sqlCause && isRetryable(sqlCause);
    }

    private static void sleepBeforeRetry() throws SQLException {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while retrying MySQL connection", e);
        }
    }
}
