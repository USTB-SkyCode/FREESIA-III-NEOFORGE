package com.nguyendevs.freesia.neoforge;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import com.google.inject.Inject;
import com.nguyendevs.freesia.neoforge.mapper.MapperManager;
import com.nguyendevs.freesia.neoforge.mapper.MapperSession;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Freesia II-like YSM proxy for NeoForge 1.21.1.
 * <p>
 * Intercepts {@code yes_steve_model:2.6.0} packets and routes them through a dedicated Worker
 * node (a NeoForge 1.21.1 server running YSM 2.6.5) instead of the backend, translating entity
 * ids between the Worker and the backend.
 */
public class FreesiaNeoForge implements PacketListener {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private FreesiaConfig config;
    private MapperManager mapperManager;

    @Inject
    public FreesiaNeoForge(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        try {
            FreesiaConfig.saveDefault(this.dataDirectory);
            this.config = FreesiaConfig.load(this.dataDirectory);
        } catch (IOException e) {
            this.logger.error("[Freesia] Failed to read config, using defaults.", e);
            this.config = new FreesiaConfig();
        }

        this.mapperManager = new MapperManager(
                this.server, this.config.getWorkerHost(), this.config.getWorkerPort(), this.logger, this.config.isDebug());

        this.server.getChannelRegistrar().register(YsmConstants.CHANNEL_KEY);

        PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.HIGHEST);

        this.logger.info("[Freesia] Enabled. Worker = {}:{}", this.config.getWorkerHost(), this.config.getWorkerPort());
    }

    /** Creates the Worker connection asynchronously once the player reaches a backend. */
    @Subscribe
    public EventTask onServerConnected(ServerConnectedEvent event) {
        final Player player = event.getPlayer();
        return EventTask.async(() -> {
            this.mapperManager.createSession(player);
            this.logger.info("[Freesia] Mapper session created for {}", player.getUsername());
        });
    }

    @Subscribe
    public void onChannelMsg(PluginMessageEvent event) {
        final ChannelIdentifier identifier = event.getIdentifier();
        if (!(identifier instanceof MinecraftChannelIdentifier mineId)) {
            return;
        }
        if (!mineId.getId().equals(YsmConstants.CHANNEL)) {
            return;
        }

        // The Worker owns YSM, not the backend. Consume both directions.
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if (event.getSource() instanceof Player player) {
            final MapperSession session = this.mapperManager.getSession(player.getUniqueId());
            if (session == null) {
                this.logger.warn("[Freesia] No mapper session for {} yet, dropping YSM packet",
                        player.getUsername());
                return;
            }
            session.relayC2S(event.getData());
        }
        // ServerConnection (backend -> client): dropped — the backend must not emit YSM.
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.JOIN_GAME) {
            final WrapperPlayServerJoinGame join = new WrapperPlayServerJoinGame(event);
            final Player target = event.getPlayer();
            if (target != null) {
                this.mapperManager.onBackendEntityId(target.getUniqueId(), join.getEntityId());
                this.logger.info("[Freesia] Backend entity id for {} = {}",
                        target.getUsername(), join.getEntityId());
            }
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_PLAYER) {
            // A player entered this client's view (dimension switch / chunk load). Re-send the
            // cached YSM model so it shows up without a manual re-select.
            final WrapperPlayServerSpawnPlayer spawn = new WrapperPlayServerSpawnPlayer(event);
            final Player target = event.getPlayer();
            final byte[] modelData = this.mapperManager.getCachedModelData(spawn.getEntityId());
            if (target != null && modelData != null) {
                final byte[] data = modelData;
                this.server.getScheduler().buildTask(this, () ->
                        target.sendPluginMessage(YsmConstants.CHANNEL_KEY, data))
                        .delay(50, TimeUnit.MILLISECONDS)
                        .schedule();
                this.logger.info("[Freesia] Re-broadcast model for spawned entity {} -> {}",
                        spawn.getEntityId(), target.getUsername());
            }
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        this.mapperManager.onPlayerDisconnect(event.getPlayer());
    }
}
