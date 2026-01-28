package h2ph.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import h2ph.db.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class PlayerDataListener {

    private final DatabaseManager databaseManager;

    public PlayerDataListener(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String serverName = event.getServer().getServerInfo().getName();

        try (Connection connection = databaseManager.getConnection()) {
            // Save to active_players (session info)
            try (PreparedStatement statement = connection.prepareStatement(
                    "REPLACE INTO active_players (uuid, gamertag, region) VALUES (?, ?, ?)")) {
                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, player.getUsername());
                statement.setString(3, serverName);
                statement.executeUpdate();
            }

            // Save to player_data (persistent info)
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO player_data (uuid, gamertag, last_region, last_location) VALUES (?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE gamertag = VALUES(gamertag), last_region = VALUES(last_region), last_location = VALUES(last_location)")) {
                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, player.getUsername());
                statement.setString(3, serverName);
                statement.setString(4, serverName); // Location is server name for now
                statement.executeUpdate();
            }

            System.out.println("[PrismChat-Debug] Saved player data for " + player.getUsername() + " (Server: "
                    + serverName + ")");

        } catch (SQLException e) {
            System.err.println("[PrismChat-Debug] ERROR: Could not save data for " + player.getUsername());
            e.printStackTrace();
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();

        try (Connection connection = databaseManager.getConnection()) {
            // Remove from active_players
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM active_players WHERE uuid = ?")) {
                statement.setString(1, player.getUniqueId().toString());
                statement.executeUpdate();
            }

            System.out.println("[PrismChat-Debug] Deleted session data for " + player.getUsername());

        } catch (SQLException e) {
            System.err.println("[PrismChat-Debug] ERROR: Could not handle disconnect for " + player.getUsername());
            e.printStackTrace();
        }
    }
}
