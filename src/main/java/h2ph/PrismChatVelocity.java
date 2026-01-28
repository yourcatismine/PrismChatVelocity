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

@Plugin(id = "prismchatvelocity", name = "PrismChat", version = "1.0-SNAPSHOT", description = "Global Chat Plugin for Velocity", authors = {
        "User" })
public class PrismChatVelocity {

    private final ProxyServer server;
    private final Logger logger;
    private final java.nio.file.Path dataDirectory; // Inject data directory
    private DatabaseManager databaseManager;
    private h2ph.config.ConfigManager configManager;

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
                configManager.getString("host", "localhost"),
                configManager.getInt("port", 3306),
                configManager.getString("database", "minecraft"),
                configManager.getString("username", "root"),
                configManager.getString("password", "password"));

        // Register Listeners
        server.getEventManager().register(this, new h2ph.listeners.PlayerDataListener(databaseManager));
        server.getEventManager().register(this, new h2ph.listeners.PersistenceListener(server, databaseManager));

        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("prismvoid").build(),
                (com.velocitypowered.api.command.SimpleCommand) invocation -> {
                    // Do nothing
                });
        logger.info("PrismChat has been enabled!");
    }

    @Subscribe
    public void onChat(PlayerChatEvent event) {

        Player player = event.getPlayer();
        String message = event.getMessage();

        Component formattedMessage = Component.text()
                .append(Component.text("<"))
                .append(Component.text(player.getUsername()))
                .append(Component.text("> "))
                .append(Component.text(message))
                .build();

        // Broadcast to players on OTHER servers to make it "Global"
        // The local server will handle showing it to players on the same server.
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
