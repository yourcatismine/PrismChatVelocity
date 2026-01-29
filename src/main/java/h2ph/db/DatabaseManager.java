package h2ph.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

public class DatabaseManager {

    private HikariDataSource dataSource;

    public void initialize(String host, int port, String database, String username, String password) {
        HikariConfig config = new HikariConfig();
        // TODO: Load these from a config file
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setUsername(username);
        config.setPassword(password);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        try {
            dataSource = new HikariDataSource(config);
            createTable();
        } catch (Exception e) {
            System.err.println(
                    "[PrismChat] Failed to connect to MySQL database! Please check your config.yml and ensure MySQL is running.");
            System.err.println("[PrismChat] Error: " + e.getMessage());
            // We don't throw here to allow the proxy to start, but DB features won't work.
        }
    }

    private void createTable() {
        if (dataSource == null)
            return;
        try (Connection connection = getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS active_players (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "gamertag VARCHAR(16) NOT NULL, " +
                    "time_joined TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "region VARCHAR(32) NOT NULL" +
                    ");");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_data (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "gamertag VARCHAR(16) NOT NULL, " +
                    "first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "last_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                    "last_region VARCHAR(32) NOT NULL, " +
                    "last_location VARCHAR(128) NOT NULL" +
                    ");");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS offline_players (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "gamertag VARCHAR(16) NOT NULL, " +
                    "time_left TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "last_location VARCHAR(128) NOT NULL" +
                    ");");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveOfflinePlayer(String uuid, String gamertag, String lastLocation) {
        if (dataSource == null) return;
        String sql = "INSERT INTO offline_players (uuid, gamertag, time_left, last_location) VALUES (?, ?, CURRENT_TIMESTAMP, ?) " +
                "ON DUPLICATE KEY UPDATE gamertag = VALUES(gamertag), time_left = VALUES(time_left), last_location = VALUES(last_location)";
        try (Connection connection = getConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            stmt.setString(2, gamertag);
            stmt.setString(3, lastLocation);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public OfflinePlayerRecord getOfflinePlayer(String uuid) {
        if (dataSource == null) return null;
        String sql = "SELECT uuid, gamertag, time_left, last_location FROM offline_players WHERE uuid = ?";
        try (Connection connection = getConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String u = rs.getString("uuid");
                    String gamertag = rs.getString("gamertag");
                    Timestamp timeLeft = rs.getTimestamp("time_left");
                    String lastLocation = rs.getString("last_location");
                    return new OfflinePlayerRecord(u, gamertag, timeLeft, lastLocation);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void removeOfflinePlayer(String uuid) {
        if (dataSource == null) return;
        String sql = "DELETE FROM offline_players WHERE uuid = ?";
        try (Connection connection = getConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Database is not connected.");
        }
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
