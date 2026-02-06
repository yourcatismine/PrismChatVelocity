package h2ph;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import h2ph.db.DatabaseManager;
import h2ph.cache.PlayerCache;
import h2ph.cache.ProxyPlayerData;

@Plugin(id = "prismchatvelocity", name = "PrismChat", version = "1.0-SNAPSHOT", description = "Global Chat Plugin for Velocity", authors = {
        "User" })
public class PrismChatVelocity {

    private final ProxyServer server;
    private final Logger logger;
    private final java.nio.file.Path dataDirectory; // Inject data directory
    private DatabaseManager databaseManager;
    private h2ph.redis.RedisManager redisManager;
    private h2ph.config.ConfigManager configManager;
    private PlayerCache playerCache;
    private h2ph.chat.ChatFilter chatFilter;
    private h2ph.listeners.PingListener pingListener;

    @Inject
    public PrismChatVelocity(ProxyServer server, Logger logger,
            @com.velocitypowered.api.plugin.annotation.DataDirectory java.nio.file.Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Initialize Config
        configManager = new h2ph.config.ConfigManager(dataDirectory);
        configManager.loadConfig();

        // Initialize Database
        databaseManager = new DatabaseManager();
        databaseManager.initialize(
            configManager.getDatabaseHost("localhost"),
            configManager.getDatabasePort(3306),
            configManager.getDatabaseName("minecraft"),
            configManager.getDatabaseUsername("root"),
            configManager.getDatabasePassword("password"));

        // Initialize Redis
        redisManager = new h2ph.redis.RedisManager(configManager);

        // Initialize Player Cache
        playerCache = new PlayerCache(databaseManager);
        chatFilter = new h2ph.chat.ChatFilter(configManager);

        // Register Team Chat Listener (handles intercepting chat and redis subscription)
        String instanceId = java.util.UUID.randomUUID().toString();
        server.getEventManager().register(this, new h2ph.listeners.TeamChatListener(server, databaseManager, redisManager, playerCache, instanceId, chatFilter));

        // Register Listeners
        server.getEventManager().register(this, new h2ph.listeners.PlayerDataListener(databaseManager, redisManager, playerCache));
        server.getEventManager().register(this, new h2ph.listeners.CommandBlockListener());

        // Register Ping/MOTD Listener with configured MOTD
        String initialMotd = configManager.getMotd("§5§lprismsmp.net§r\n           §3§lɴᴏʀᴛʜ ᴀᴍᴇʀɪᴄᴀ ᴇᴀѕᴛ ʀᴇʟᴇᴀѕᴇᴅ");
        pingListener = new h2ph.listeners.PingListener(initialMotd);
        server.getEventManager().register(this, pingListener);

        // Subscribe to prism:player_update for cache invalidation
        redisManager.subscribe("prism:player_update", msg -> {
            try {
                java.util.UUID uuid = java.util.UUID.fromString(msg);
                playerCache.invalidate(uuid);
                System.out.println("[PrismChat-Debug] Invalidated cache for " + uuid);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        server.getEventManager().register(this, new h2ph.listeners.PersistenceListener(server, databaseManager));

        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("prismvoid").build(),
                (com.velocitypowered.api.command.SimpleCommand) invocation -> {
                    // Do nothing
                });

        // Command to reload MOTD from config: /prismmotd reload
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("prismmotd").build(),
                (com.velocitypowered.api.command.SimpleCommand) invocation -> {
                    String[] args = invocation.arguments();
                    com.velocitypowered.api.command.CommandSource src = invocation.source();
                    if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
                        configManager.loadConfig();
                        String newMotd = configManager.getMotd(initialMotd);
                        if (pingListener != null) {
                            pingListener.setMotd(newMotd);
                        }
                        src.sendMessage(Component.text("PrismMOTD: reloaded MOTD."));
                    } else {
                        src.sendMessage(Component.text("Usage: /prismmotd reload"));
                    }
                });

        // Schedule Ping Update Task (every 10 seconds)
        server.getScheduler().buildTask(this, () -> {
            if (redisManager != null) {
                for (Player player : server.getAllPlayers()) {
                    long ping = player.getPing();
                    redisManager.setPlayerPing(player.getUniqueId(), ping);
                }
            }
        })
                .repeat(java.time.Duration.ofSeconds(10))
                .schedule();

        logger.info("PrismChat has been enabled!");
    }

    @Subscribe
    public void onChat(PlayerChatEvent event) {
        // If player has team chat enabled, do not perform the global broadcast here.
        Player player = event.getPlayer();
        String message = event.getMessage();

        if (chatFilter != null && !chatFilter.canSend(player, message)) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            return;
        }

        // Use cache for instant check
        ProxyPlayerData cached = playerCache != null ? playerCache.get(player.getUniqueId()) : null;
        if (cached != null && cached.teamChatEnabled) {
            // Team chat will be handled by TeamChatListener/Redis instead.
            return;
        }

        String displayNameLegacy = h2ph.util.ChatFormatUtil.getDisplayNameLegacy(player);
        Component displayNameComponent = h2ph.util.ChatFormatUtil.deserializeLegacy(displayNameLegacy);

        Component formattedMessage = Component.text()
                .append(Component.text("<"))
                .append(displayNameComponent)
                .append(Component.text("> "))
                .append(Component.text(message))
                .build();

        // Broadcast to players on OTHER servers to make it "Global"
        for (Player p : server.getAllPlayers()) {
            if (p.getCurrentServer().isPresent() && player.getCurrentServer().isPresent()) {
                String pServer = p.getCurrentServer().get().getServerInfo().getName();
                String myServer = player.getCurrentServer().get().getServerInfo().getName();

                if (!pServer.equals(myServer)) {
                    p.sendMessage(formattedMessage);
                }
            }
        }
    }

    // Cleanup on disable/shutdown if needed, though Velocity doesn't have a direct
    // onDisable counterpart in the same way.
    // Usually handled via ProxyShutdownEvent if critical.
}
