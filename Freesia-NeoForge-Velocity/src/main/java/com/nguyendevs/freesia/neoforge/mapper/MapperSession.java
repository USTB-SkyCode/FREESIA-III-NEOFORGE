package com.nguyendevs.freesia.neoforge.mapper;

import com.nguyendevs.freesia.neoforge.YsmConstants;
import com.nguyendevs.freesia.neoforge.proxy.YsmPacketProxy;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.BuiltinFlags;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.ConnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.DisconnectingEvent;
import org.geysermc.mcprotocollib.network.event.session.PacketErrorEvent;
import org.geysermc.mcprotocollib.network.event.session.PacketSendingEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.network.tcp.TcpClientSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;

/**
 * A fake vanilla client connection from the proxy to the Worker node (NeoForge 1.21.1 + YSM).
 * <p>
 * The Worker believes it is talking to a real player, so its YSM model-sync logic (server-side
 * Netty + connection-handler checks) works. This session relays YSM packets to/from the real
 * player, translating entity ids along the way.
 */
public class MapperSession implements SessionListener {

    private static final Key YSM_KEY = Key.key(YsmConstants.CHANNEL);

    private final Player player;
    private final MapperManager manager;
    private final YsmPacketProxy proxy;
    private final String host;
    private final int port;

    private volatile TcpClientSession session;
    private volatile int workerEntityId = -1;
    private volatile int backendEntityId = -1;

    public MapperSession(Player player, MapperManager manager, String host, int port) {
        this.player = player;
        this.manager = manager;
        this.proxy = new YsmPacketProxy(player, manager);
        this.host = host;
        this.port = port;
    }

    public void connect() {
        final MinecraftProtocol protocol = new MinecraftProtocol(
                new GameProfile(this.player.getUniqueId(), this.player.getUsername()), null);
        this.session = new TcpClientSession(this.host, this.port, protocol);
        this.session.addListener(this);
        this.session.setFlag(BuiltinFlags.READ_TIMEOUT, 30_000);
        this.session.setFlag(BuiltinFlags.WRITE_TIMEOUT, 30_000);
        this.session.connect(true, false);
    }

    @Override
    public void packetReceived(Session session, Packet packet) {
        if (packet instanceof ClientboundLoginPacket loginPacket) {
            this.workerEntityId = loginPacket.getEntityId();
            this.manager.onWorkerEntityId(this, this.workerEntityId);
            return;
        }

        // Keep the fake client's connection alive (PLAY-phase keep-alive + CONFIG-phase ping).
        if (packet instanceof ClientboundKeepAlivePacket keepAlive) {
            session.send(new ServerboundKeepAlivePacket(keepAlive.getPingId()));
            return;
        }

        if (packet instanceof ClientboundPingPacket ping) {
            session.send(new ServerboundPongPacket(ping.getId()));
            return;
        }

        if (packet instanceof ClientboundCustomPayloadPacket payloadPacket) {
            final String channel = payloadPacket.getChannel().asString();
            this.manager.getLogger().info("[Freesia] Received custom payload channel: " + channel);
            if (channel.equals(YsmConstants.CHANNEL)) {
                final byte[] data = payloadPacket.getData();
                final YsmPacketProxy.Result result = this.proxy.processS2C(data);
                switch (result.kind()) {
                    case MODIFY -> {
                        this.player.sendPluginMessage(YsmConstants.CHANNEL_KEY, result.data());
                        if (result.broadcast()) {
                            // Fallback: broadcast this entity-state packet to every other online player.
                            this.manager.broadcastToOthers(this.player.getUniqueId(), result.data());
                        }
                    }
                    case PASS -> this.player.sendPluginMessage(YsmConstants.CHANNEL_KEY, data);
                    case DROP -> {
                    }
                }
            }
        }
    }

    @Override
    public void packetSending(PacketSendingEvent event) {
    }

    @Override
    public void packetSent(Session session, Packet packet) {
    }

    @Override
    public void packetError(PacketErrorEvent event) {
    }

    @Override
    public void connected(ConnectedEvent event) {
    }

    @Override
    public void disconnecting(DisconnectingEvent event) {
    }

    @Override
    public void disconnected(DisconnectedEvent event) {
        this.manager.removeSession(this);
    }

    /** Relays a client -> Worker YSM packet. */
    public void relayC2S(byte[] data) {
        final YsmPacketProxy.Result result = this.proxy.processC2S(data);
        final TcpClientSession s = this.session;
        if (s == null) {
            return;
        }
        switch (result.kind()) {
            case MODIFY -> s.send(new ServerboundCustomPayloadPacket(YSM_KEY, result.data()));
            case PASS -> s.send(new ServerboundCustomPayloadPacket(YSM_KEY, data));
            case DROP -> {
            }
        }
    }

    public void disconnect() {
        final TcpClientSession s = this.session;
        if (s != null) {
            s.disconnect("mapper closed");
        }
    }

    public void setBackendEntityId(int id) {
        this.backendEntityId = id;
    }

    public void setWorkerEntityId(int id) {
        this.workerEntityId = id;
    }

    public int getBackendEntityId() {
        return this.backendEntityId;
    }

    public int getWorkerEntityId() {
        return this.workerEntityId;
    }

    public Player getPlayer() {
        return this.player;
    }
}
