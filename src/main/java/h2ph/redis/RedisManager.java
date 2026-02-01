package h2ph.redis;

import h2ph.config.ConfigManager;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;
import redis.clients.jedis.JedisPubSub;
import com.google.gson.Gson;

public class RedisManager {

    private final ConfigManager configManager;
    private JedisPool jedisPool;

    public RedisManager(ConfigManager configManager) {
        this.configManager = configManager;
        connect();
    }

    private void connect() {
        String host = configManager.getString("redis.host", "localhost");
        int port = configManager.getInt("redis.port", 6379);
        String username = configManager.getString("redis.username", "");
        String password = configManager.getString("redis.password", "");

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(16);
        poolConfig.setMaxIdle(8);
        poolConfig.setMinIdle(2);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setJmxEnabled(false);

        // Calculate timeout (e.g., 2 seconds)
        int timeout = 2000;

        if (!password.isEmpty()) {
            if (!username.isEmpty()) {
                // ACL style auth
                jedisPool = new JedisPool(poolConfig, host, port, timeout, username, password);
            } else {
                // Legacy auth
                jedisPool = new JedisPool(poolConfig, host, port, timeout, password);
            }
        } else {
            jedisPool = new JedisPool(poolConfig, host, port, timeout);
        }
    }

    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }

    public Jedis getResource() {
        return jedisPool.getResource();
    }

    // Key: prism:player:uuid:<gamertag_lowercase> -> <uuid>
    public void setPlayerUuid(String gamertag, UUID uuid) {
        try (Jedis jedis = getResource()) {
            String key = "prism:player:uuid:" + gamertag.toLowerCase();
            jedis.set(key, uuid.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Key: prism:player:gamertag:<uuid> -> <gamertag>
    public void setPlayerGamertag(UUID uuid, String gamertag) {
        try (Jedis jedis = getResource()) {
            String key = "prism:player:gamertag:" + uuid.toString();
            jedis.set(key, gamertag);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Key: prism:player:server:<uuid> -> <server_name>
    public void setPlayerServer(UUID uuid, String serverName) {
        try (Jedis jedis = getResource()) {
            String key = "prism:player:server:" + uuid.toString();
            jedis.set(key, serverName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Key: prism:player:ping:<uuid> -> <ping_ms> with TTL 10s
    public void setPlayerPing(UUID uuid, long ping) {
        try (Jedis jedis = getResource()) {
            String key = "prism:player:ping:" + uuid.toString();
            jedis.setex(key, 10, String.valueOf(ping));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void removePlayerServer(UUID uuid) {
        try (Jedis jedis = getResource()) {
            jedis.del("prism:player:server:" + uuid.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void removePlayerPing(UUID uuid) {
        try (Jedis jedis = getResource()) {
            jedis.del("prism:player:ping:" + uuid.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Publish team chat payload JSON to channel
    public void publishTeamChat(String channel, String jsonPayload) {
        try (Jedis jedis = getResource()) {
            jedis.publish(channel, jsonPayload);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Subscribe to a channel and forward messages to the provided consumer on a new thread
    public void subscribe(String channel, Consumer<String> onMessage) {
        Thread t = new Thread(() -> {
            try (Jedis jedis = getResource()) {
                JedisPubSub pubSub = new JedisPubSub() {
                    @Override
                    public void onMessage(String ch, String message) {
                        if (onMessage != null) {
                            try {
                                onMessage.accept(message);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    }
                };

                jedis.subscribe(pubSub, channel);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "prism-redis-sub-" + channel);

        t.setDaemon(true);
        t.start();
    }

    public String makeTeamChatPayload(String sender, String teamId, String teamName, String message, String origin) {
        Gson g = new Gson();
        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.put("sender", sender);
        map.put("teamId", teamId);
        map.put("teamName", teamName);
        map.put("message", message);
        map.put("origin", origin != null ? origin : "");
        return g.toJson(map);
    }
}
