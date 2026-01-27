package h2ph;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

@Plugin(id = "prismchatvelocity", name = "PrismChat", version = "1.0-SNAPSHOT", description = "Global Chat Plugin for Velocity", authors = {
        "User" })
public class PrismChatVelocity {

    private final ProxyServer server;
    private final Logger logger;

    @Inject
    public PrismChatVelocity(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
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
}
