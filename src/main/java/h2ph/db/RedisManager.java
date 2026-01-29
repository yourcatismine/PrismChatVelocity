package h2ph.db;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import java.util.Map;
import java.util.Set;

public class RedisManager {

    private JedisPool jedisPool;

    public void initialize(String host, int port, String username, String password) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);

        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            jedisPool = new JedisPool(poolConfig, host, port, 2000, username, password);
        } else if (password != null && !password.isEmpty()) {
            jedisPool = new JedisPool(poolConfig, host, port, 2000, password);
        } else {
            jedisPool = new JedisPool(poolConfig, host, port, 2000);
        }

        try (Jedis jedis = jedisPool.getResource()) {
            System.out.println("[PrismChat] Successfully connected to Redis!");
        } catch (Exception e) {
            System.err.println("[PrismChat] Failed to connect to Redis!");
            e.printStackTrace();
        }
    }

    public void setPlayerServer(String uuid, String username, String serverName) {
        if (jedisPool == null)
            return;
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset("player:" + uuid, "username", username);
            jedis.hset("player:" + uuid, "server", serverName);
            jedis.expire("player:" + uuid, 604800); // 1 week ttl just in case

            // Also store in a master list or just rely on keys?
            // For now, let's keep a set of online players if we want to list them easily
            jedis.sadd("online_players", uuid);
        }
    }

    public void removePlayer(String uuid) {
        if (jedisPool == null)
            return;
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del("player:" + uuid);
            jedis.srem("online_players", uuid);
        }
    }

    public String getPlayerServer(String uuid) {
        if (jedisPool == null)
            return null;
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hget("player:" + uuid, "server");
        }
    }

    public void close() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}
