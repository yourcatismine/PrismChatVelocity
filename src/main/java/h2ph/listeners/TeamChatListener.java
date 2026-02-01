package h2ph.listeners;

import com.google.gson.Gson;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import h2ph.db.DatabaseManager;
import h2ph.redis.RedisManager;
import h2ph.cache.PlayerCache;
import h2ph.cache.ProxyPlayerData;
import net.kyori.adventure.text.Component;

import java.util.UUID;

public class TeamChatListener {

    private final DatabaseManager databaseManager;
    private final RedisManager redisManager;
    private final ProxyServer server;
    private final PlayerCache playerCache;
    private final Gson gson = new Gson();
    private final String instanceId;

    public TeamChatListener(ProxyServer server, DatabaseManager databaseManager, RedisManager redisManager, PlayerCache playerCache, String instanceId) {
        this.server = server;
        this.databaseManager = databaseManager;
        this.redisManager = redisManager;
        this.playerCache = playerCache;
        this.instanceId = instanceId != null ? instanceId : "";
        startSubscriber();
    }

    @Subscribe
    public void onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Use cache for instant team chat check
        ProxyPlayerData cached = playerCache != null ? playerCache.get(uuid) : null;

        if (cached == null || !cached.teamChatEnabled) {
            // Not team chat — allow other handlers/global chat to proceed.
            return;
        }

        // Team chat is enabled -> cancel the chat event and handle publish/delivery instantly.
        event.setResult(PlayerChatEvent.ChatResult.denied());

        final String sender = player.getUsername();
        final String message = event.getMessage();
        final String teamId = cached.teamId;
        final String teamName = cached.teamName;

        // Publish to Redis instantly (non-blocking)
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                String payload = redisManager.makeTeamChatPayload(sender, teamId, teamName, message, instanceId);
                redisManager.publishTeamChat("prism:team_chat", payload);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Immediate local delivery to reduce perceived latency (check cache for team membership)
        String format = String.format("§7[%s§7] §5%s: §f%s", teamName != null ? teamName : "Team", sender, message);
        for (Player p : server.getAllPlayers()) {
            try {
                ProxyPlayerData pData = playerCache != null ? playerCache.get(p.getUniqueId()) : null;
                if (pData != null && teamId != null && teamId.equals(pData.teamId)) {
                    p.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(format));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void startSubscriber() {
        if (redisManager == null) return;

        redisManager.subscribe("prism:team_chat", raw -> {
            try {
                java.util.Map data = gson.fromJson(raw, java.util.Map.class);
                if (data == null) return;
                String origin = data.get("origin") != null ? (String) data.get("origin") : "";

                // Ignore messages originating from this instance because we already delivered them locally.
                if (origin.equals(this.instanceId)) return;

                String sender = (String) data.get("sender");
                String teamId = (String) data.get("teamId");
                String teamName = (String) data.get("teamName");
                String content = (String) data.get("message");

                String format = String.format("§7[%s§7] §5%s: §f%s", teamName != null ? teamName : "Team", sender, content);

                // Use cache for team membership check (instant, no DB query)
                for (Player p : server.getAllPlayers()) {
                    try {
                        ProxyPlayerData pData = playerCache != null ? playerCache.get(p.getUniqueId()) : null;
                        if (pData != null && teamId != null && teamId.equals(pData.teamId)) {
                            p.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(format));
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
