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

    /**
     * Check whether a given column exists on a table in the current database.
     */
    public boolean hasColumn(String tableName, String columnName) {
        if (dataSource == null) return false;
        String sql = "SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ? LIMIT 1";
        try (Connection connection = getConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, tableName);
            stmt.setString(2, columnName);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Ensure the offline_players table has the expected columns. If the
     * `last_region` column is missing (older installs), attempt a safe migration:
     *  - add nullable `last_region`
     *  - copy values from `last_location` where appropriate
     *  - make the column NOT NULL with default 'unknown'
     */
    // offline_players schema migration removed — offline storage no longer used

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

                    // offline_players table removed — backend handles offline storage now
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // offline player helper methods removed — backend handles offline storage

    public String getLastRegion(String uuid) {
        if (dataSource == null) return null;
        String sql = "SELECT last_region FROM player_data WHERE uuid = ?";
        try (Connection connection = getConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("last_region");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Team chat related queries
    public boolean isTeamChatEnabled(String uuid) {
        if (dataSource == null) return false;
        String sql = "SELECT team_chat_enabled FROM player_data WHERE uuid = ?";
        try (Connection connection = getConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("team_chat_enabled") == 1;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getTeamIdForPlayer(String uuid) {
        if (dataSource == null) return null;
        String sql = "SELECT team_id FROM player_data WHERE uuid = ?";
        try (Connection connection = getConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("team_id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getTeamName(String teamId) {
        if (dataSource == null || teamId == null) return null;
        String sql = "SELECT name FROM teams WHERE id = ?";
        try (Connection connection = getConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, teamId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean isPlayerInTeam(String uuid, String teamId) {
        if (dataSource == null || teamId == null) return false;
        String sql = "SELECT 1 FROM player_data WHERE uuid = ? AND team_id = ? LIMIT 1";
        try (Connection connection = getConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            stmt.setString(2, teamId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public java.util.Set<String> getTeamMembers(String teamId) {
        java.util.Set<String> result = new java.util.HashSet<>();
        if (dataSource == null || teamId == null) return result;
        String sql = "SELECT uuid FROM player_data WHERE team_id = ?";
        try (Connection connection = getConnection(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, teamId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String u = rs.getString("uuid");
                    if (u != null) result.add(u);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
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
