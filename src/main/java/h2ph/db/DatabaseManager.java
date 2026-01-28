package h2ph.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

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
