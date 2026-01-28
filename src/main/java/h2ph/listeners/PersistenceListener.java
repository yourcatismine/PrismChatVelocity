package h2ph.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import h2ph.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class PersistenceListener {

    private final ProxyServer server;
    private final DatabaseManager databaseManager;

    public PersistenceListener(ProxyServer server, DatabaseManager databaseManager) {
        this.server = server;
        this.databaseManager = databaseManager;
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        // Only trigger on initial connection (when no server is set yet)
        if (event.getPlayer().getCurrentServer().isPresent()) {
            return;
        }

        Player player = event.getPlayer();
        String lastRegion = getLastRegion(player.getUniqueId().toString());

        if (lastRegion != null) {
            Optional<RegisteredServer> targetServer = server.getServer(lastRegion);
            if (targetServer.isPresent()) {
                event.setResult(ServerPreConnectEvent.ServerResult.allowed(targetServer.get()));
                System.out.println(
                        "[PrismChat-Debug] Redirecting " + player.getUsername() + " to last server: " + lastRegion);
            }
        }
    }

    private String getLastRegion(String uuid) {
        try (Connection connection = databaseManager.getConnection();
                PreparedStatement statement = connection
                        .prepareStatement("SELECT last_region FROM player_data WHERE uuid = ?")) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("last_region");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
