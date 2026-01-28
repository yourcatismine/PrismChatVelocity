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

        try (Connection connection = databaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "REPLACE INTO active_players (uuid, gamertag, region) VALUES (?, ?, ?)")) {

            statement.setString(1, player.getUniqueId().toString());
            statement.setString(2, player.getUsername());
            statement.setString(3, serverName);
            statement.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();

        try (Connection connection = databaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM active_players WHERE uuid = ?")) {

            statement.setString(1, player.getUniqueId().toString());
            statement.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
