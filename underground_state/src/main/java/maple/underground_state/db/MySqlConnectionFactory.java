package maple.underground_state.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.logging.Logger;

public final class MySqlConnectionFactory {

    private static final String HOST = "api.maplenw.top";
    private static final int PORT = 3306;
    private static final String DATABASE = "underground_state";
    private static final String USERNAME = "underground_state";
    private static final String PASSWORD = envOrDefault("UNDERGROUND_STATE_MYSQL_PASSWORD", "");
    private static final String URL = "jdbc:mysql://" + HOST + ":" + PORT + "/" + DATABASE
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Hong_Kong&characterEncoding=utf8"
            + "&connectTimeout=3000&socketTimeout=5000&tcpKeepAlive=true";
    private static final int CONNECT_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 250L;

    private static volatile boolean driverLoaded;
    private static volatile boolean targetLogged;

    private MySqlConnectionFactory() {
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    public static Connection connect() throws SQLException {
        ensureDriverLoaded();
        SQLException last = null;
        for (int attempt = 1; attempt <= CONNECT_ATTEMPTS; attempt++) {
            try {
                return DriverManager.getConnection(URL, USERNAME, PASSWORD);
            } catch (SQLException e) {
                last = e;
                if (attempt >= CONNECT_ATTEMPTS || !isRetryable(e)) {
                    throw e;
                }
                sleepBeforeRetry();
            }
        }
        throw last != null ? last : new SQLException("Failed to connect to MySQL");
    }

    public static void logTarget(Logger logger) {
        if (logger == null || targetLogged) {
            return;
        }
        synchronized (MySqlConnectionFactory.class) {
            if (targetLogged) {
                return;
            }
            logger.info("Using MySQL database " + DATABASE + " at " + HOST + ":" + PORT);
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
