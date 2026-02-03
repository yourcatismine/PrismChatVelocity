package h2ph.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import h2ph.db.DatabaseManager;
import h2ph.cache.PlayerCache;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class PlayerDataListener {

    private final h2ph.db.DatabaseManager databaseManager;
    private final h2ph.redis.RedisManager redisManager;
    private final PlayerCache playerCache;

    public PlayerDataListener(DatabaseManager databaseManager, h2ph.redis.RedisManager redisManager, PlayerCache playerCache) {
        this.databaseManager = databaseManager;
        this.redisManager = redisManager;
        this.playerCache = playerCache;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String serverName = event.getServer().getServerInfo().getName();

        // Load player cache asynchronously on login
        if (playerCache != null) {
            playerCache.loadAsync(player.getUniqueId());
        }
        
        if (redisManager != null) {
            redisManager.setPlayerUuid(player.getUsername(), player.getUniqueId());
            redisManager.setPlayerGamertag(player.getUniqueId(), player.getUsername());
            redisManager.setPlayerServer(player.getUniqueId(), serverName);
            System.out.println("[PrismChat-Debug] Saved player session to Redis for " + player.getUsername());
        }

        
        // Clear any previous last_region / last_location when a player connects (they just joined)
        try (Connection connection = databaseManager.getConnection()) {
            boolean hasGamertag = databaseManager.hasColumn("player_data", "gamertag");

            if (hasGamertag) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO player_data (uuid, gamertag, last_region, last_location) VALUES (?, ?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE gamertag = VALUES(gamertag), last_region = VALUES(last_region), last_location = VALUES(last_location)")) {
                    statement.setString(1, player.getUniqueId().toString());
                    statement.setString(2, player.getUsername());
                    // Clear stored region because player has re-joined
                    statement.setString(3, "");
                    statement.setString(4, "");
                    statement.executeUpdate();
                }
            } else {
                // Fallback for older schema without `gamertag` column
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO player_data (uuid, last_region, last_location) VALUES (?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE last_region = VALUES(last_region), last_location = VALUES(last_location)")) {
                    statement.setString(1, player.getUniqueId().toString());
                    // Clear stored region because player has re-joined
                    statement.setString(2, "");
                    statement.setString(3, "");
                    statement.executeUpdate();
                }
            }

            System.out.println("[PrismChat-Debug] Cleared persistent last_region for " + player.getUsername() + " (Server connect: " + serverName + ")");

        } catch (SQLException e) {
            System.err.println("[PrismChat-Debug] ERROR: Could not clear persistent data for " + player.getUsername());
            e.printStackTrace();
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();

        // Remove from cache on disconnect
        if (playerCache != null) {
            playerCache.remove(player.getUniqueId());
        }
        
        if (redisManager != null) {
            redisManager.removePlayerServer(player.getUniqueId());
            redisManager.removePlayerPing(player.getUniqueId());
            System.out.println("[PrismChat-Debug] Removed player session from Redis for " + player.getUsername());
        }
        // Save last region on disconnect so we know where they were when they left
        try (Connection connection = databaseManager.getConnection()) {
            boolean hasGamertag = databaseManager.hasColumn("player_data", "gamertag");
            String serverName = player.getCurrentServer().isPresent() ? player.getCurrentServer().get().getServerInfo().getName() : "";

            if (hasGamertag) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO player_data (uuid, gamertag, last_region, last_location) VALUES (?, ?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE last_region = VALUES(last_region), last_location = VALUES(last_location)")) {
                    statement.setString(1, player.getUniqueId().toString());
                    statement.setString(2, player.getUsername());
                    statement.setString(3, serverName);
                    statement.setString(4, "");
                    statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO player_data (uuid, last_region, last_location) VALUES (?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE last_region = VALUES(last_region), last_location = VALUES(last_location)")) {
                    statement.setString(1, player.getUniqueId().toString());
                    statement.setString(2, serverName);
                    statement.setString(3, "");
                    statement.executeUpdate();
                }
            }

            System.out.println("[PrismChat-Debug] Saved persistent last_region for " + player.getUsername() + " (Server: " + serverName + ")");

        } catch (SQLException e) {
            System.err.println("[PrismChat-Debug] ERROR: Could not save last_region for " + player.getUsername());
            e.printStackTrace();
        }

        
    }
}
