package h2ph.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import h2ph.db.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PlayerDataListener {

    private final h2ph.db.DatabaseManager databaseManager;
    private final h2ph.redis.RedisManager redisManager;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<UUID, ScheduledFuture<?>> pendingSaves = new ConcurrentHashMap<>();

    public PlayerDataListener(DatabaseManager databaseManager, h2ph.redis.RedisManager redisManager) {
        this.databaseManager = databaseManager;
        this.redisManager = redisManager;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String serverName = event.getServer().getServerInfo().getName();

        // Save to Redis (active session)
        if (redisManager != null) {
            redisManager.setPlayerUuid(player.getUsername(), player.getUniqueId());
            redisManager.setPlayerGamertag(player.getUniqueId(), player.getUsername());
            redisManager.setPlayerServer(player.getUniqueId(), serverName);
            System.out.println("[PrismChat-Debug] Saved player session to Redis for " + player.getUsername());
        }

        // Save to player_data (persistent info) in MySQL
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO player_data (uuid, gamertag, last_region, last_location) VALUES (?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE gamertag = VALUES(gamertag), last_region = VALUES(last_region), last_location = VALUES(last_location)")) {
                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, player.getUsername());
                statement.setString(3, serverName);
                statement.setString(4, serverName); // Location is server name for now
                statement.executeUpdate();
            }

            // If the player was previously marked offline, remove that record now they're online
            try {
                databaseManager.removeOfflinePlayer(player.getUniqueId().toString());
            } catch (Exception ignored) {
            }

            // Cancel any pending offline-save for this player (server transfer race)
            try {
                ScheduledFuture<?> future = pendingSaves.remove(player.getUniqueId());
                if (future != null) future.cancel(false);
            } catch (Exception ignored) {
            }

            System.out
                    .println("[PrismChat-Debug] Saved persistent player data for " + player.getUsername() + " (Server: "
                            + serverName + ")");

        } catch (SQLException e) {
            System.err.println("[PrismChat-Debug] ERROR: Could not save persistent data for " + player.getUsername());
            e.printStackTrace();
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();

        // Remove from Redis
        if (redisManager != null) {
            redisManager.removePlayerServer(player.getUniqueId());
            redisManager.removePlayerPing(player.getUniqueId());
            System.out.println("[PrismChat-Debug] Removed player session from Redis for " + player.getUsername());
        }
        // Schedule saving offline record after a short delay to avoid transient server-transfer entries
        try {
            UUID id = player.getUniqueId();
            String gamertag = player.getUsername();
            String lastLocation = player.getCurrentServer().isPresent() ?
                    player.getCurrentServer().get().getServerInfo().getName() : "unknown";

            ScheduledFuture<?> future = scheduler.schedule(() -> {
                try {
                    databaseManager.saveOfflinePlayer(id.toString(), gamertag, lastLocation);
                    System.out.println("[PrismChat-Debug] Saved offline player record for " + gamertag);
                } catch (Exception e) {
                    System.err.println("[PrismChat-Debug] ERROR: Could not save offline record for " + gamertag);
                    e.printStackTrace();
                } finally {
                    pendingSaves.remove(id);
                }
            }, 5, TimeUnit.SECONDS);

            ScheduledFuture<?> existing = pendingSaves.put(id, future);
            if (existing != null) existing.cancel(false);
        } catch (Exception e) {
            System.err.println("[PrismChat-Debug] ERROR: Could not schedule offline record save for " + player.getUsername());
            e.printStackTrace();
        }
    }
}
