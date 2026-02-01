package h2ph.cache;

import h2ph.db.DatabaseManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * In-memory cache for player team data to avoid DB queries on hot paths.
 */
public class PlayerCache {

    private final Map<UUID, ProxyPlayerData> cache = new ConcurrentHashMap<>();
    private final DatabaseManager databaseManager;

    public PlayerCache(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Get cached data for a player. Returns null if not cached.
     */
    public ProxyPlayerData get(UUID uuid) {
        return cache.get(uuid);
    }

    /**
     * Remove a player from the cache (e.g., on disconnect).
     */
    public void remove(UUID uuid) {
        cache.remove(uuid);
    }

    /**
     * Load player data from DB asynchronously and store in cache.
     */
    public CompletableFuture<ProxyPlayerData> loadAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String uuidStr = uuid.toString();
                boolean teamChatEnabled = databaseManager.isTeamChatEnabled(uuidStr);
                String teamId = databaseManager.getTeamIdForPlayer(uuidStr);
                String teamName = databaseManager.getTeamName(teamId);
                ProxyPlayerData data = new ProxyPlayerData(teamChatEnabled, teamId, teamName);
                cache.put(uuid, data);
                return data;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    /**
     * Invalidate and reload a player's cache entry.
     */
    public void invalidate(UUID uuid) {
        loadAsync(uuid);
    }
}
