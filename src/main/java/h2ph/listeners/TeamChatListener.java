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
import h2ph.util.ChatFormatUtil;
import net.kyori.adventure.text.Component;

import java.util.UUID;

public class TeamChatListener {

    private final DatabaseManager databaseManager;
    private final RedisManager redisManager;
    private final ProxyServer server;
    private final PlayerCache playerCache;
    private final Gson gson = new Gson();
    private final h2ph.chat.ChatFilter chatFilter;
    private final String instanceId;

    public TeamChatListener(ProxyServer server, DatabaseManager databaseManager, RedisManager redisManager, PlayerCache playerCache, String instanceId, h2ph.chat.ChatFilter chatFilter) {
        this.server = server;
        this.databaseManager = databaseManager;
        this.redisManager = redisManager;
        this.playerCache = playerCache;
        this.instanceId = instanceId != null ? instanceId : "";
        this.chatFilter = chatFilter;
        startSubscriber();
    }

    @Subscribe
    public void onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String message = event.getMessage();

        // Use cache for instant team chat check
        ProxyPlayerData cached = playerCache != null ? playerCache.get(uuid) : null;

        if (cached == null || !cached.teamChatEnabled) {
            // Not team chat — allow other handlers/global chat to proceed.
            return;
        }
        if (chatFilter != null && !chatFilter.canSend(player, message)) {
            if (!h2ph.util.ChatEventSignUtil.isSigned(event)) {
                event.setResult(PlayerChatEvent.ChatResult.message(""));
            }
            return;
        }

        // Team chat is enabled -> cancel the chat event and handle publish/delivery instantly.
        if (!h2ph.util.ChatEventSignUtil.isSigned(event)) {
            event.setResult(PlayerChatEvent.ChatResult.message(""));
        }

        final String senderDisplay = ChatFormatUtil.getDisplayNameLegacy(player);
        final String teamId = cached.teamId;
        final String teamName = cached.teamName;

        // Publish to Redis instantly (non-blocking)
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                String payload = redisManager.makeTeamChatPayload(senderDisplay, teamId, teamName, message, instanceId);
                redisManager.publishTeamChat("prism:team_chat", payload);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Immediate local delivery to reduce perceived latency (check cache for team membership)
        String label = String.format("&7[%s&7] &5", teamName != null ? teamName : "Team");
        Component labelComponent = ChatFormatUtil.deserializeLegacy(label);
        Component senderComponent = ChatFormatUtil.deserializeLegacy(senderDisplay);
        Component messagePrefix = ChatFormatUtil.deserializeLegacy(": &f");
        Component formattedMessage = Component.text()
                .append(labelComponent)
                .append(senderComponent)
                .append(messagePrefix)
                .append(Component.text(message))
                .build();
        for (Player p : server.getAllPlayers()) {
            try {
                ProxyPlayerData pData = playerCache != null ? playerCache.get(p.getUniqueId()) : null;
                if (pData != null && teamId != null && teamId.equals(pData.teamId)) {
                    p.sendMessage(formattedMessage);
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

                String label = String.format("&7[%s&7] &5", teamName != null ? teamName : "Team");
                Component labelComponent = ChatFormatUtil.deserializeLegacy(label);
                Component senderComponent = ChatFormatUtil.deserializeLegacy(sender != null ? sender : "");
                Component messagePrefix = ChatFormatUtil.deserializeLegacy(": &f");
                Component formattedMessage = Component.text()
                        .append(labelComponent)
                        .append(senderComponent)
                        .append(messagePrefix)
                        .append(Component.text(content != null ? content : ""))
                        .build();

                // Use cache for team membership check (instant, no DB query)
                for (Player p : server.getAllPlayers()) {
                    try {
                        ProxyPlayerData pData = playerCache != null ? playerCache.get(p.getUniqueId()) : null;
                        if (pData != null && teamId != null && teamId.equals(pData.teamId)) {
                            p.sendMessage(formattedMessage);
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


