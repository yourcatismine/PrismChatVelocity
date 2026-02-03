package h2ph.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.concurrent.atomic.AtomicReference;

public class PingListener {

    private final AtomicReference<String> motdRawRef = new AtomicReference<>();

    public PingListener(String motdRaw) {
        this.motdRawRef.set(motdRaw);
    }

    public void setMotd(String motdRaw) {
        this.motdRawRef.set(motdRaw);
    }

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        String motd = motdRawRef.get();
        ServerPing ping = event.getPing();
        Component description = LegacyComponentSerializer.legacySection().deserialize(motd == null ? "" : motd);
        ServerPing.Builder builder = ping.asBuilder();
        builder.description(description);
        event.setPing(builder.build());
    }
}
