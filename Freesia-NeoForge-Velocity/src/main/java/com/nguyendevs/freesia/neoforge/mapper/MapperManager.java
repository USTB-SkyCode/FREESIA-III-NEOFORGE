package com.nguyendevs.freesia.neoforge.mapper;

import com.nguyendevs.freesia.neoforge.YsmConstants;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns one {@link MapperSession} per player, plus the entity-id translation tables between the
 * Worker (fake client) and the backend (real player).
 */
public class MapperManager {

    private final ProxyServer proxy;
    private final String workerHost;
    private final int workerPort;
    private final Logger logger;
    private final boolean debug;

    private final Map<UUID, MapperSession> sessions = new ConcurrentHashMap<>();

    // Backend entity id may arrive (JOIN_GAME) before the session is created; keep it aside.
    private final Map<UUID, Integer> pendingBackendIds = new ConcurrentHashMap<>();

    // worker entity id -> backend entity id, and reverse.
    private final Map<Integer, Integer> workerToBackend = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> backendToWorker = new ConcurrentHashMap<>();

    // backend entity id -> latest S2C model/state payload (already id-remapped).
    // Kept so we can re-broadcast a player's model when the backend re-tracks it
    // (dimension switch / chunk load) instead of waiting for a manual re-select.
    private final Map<Integer, byte[]> modelCache = new ConcurrentHashMap<>();

    public MapperManager(ProxyServer proxy, String workerHost, int workerPort, Logger logger, boolean debug) {
        this.proxy = proxy;
        this.workerHost = workerHost;
        this.workerPort = workerPort;
        this.logger = logger;
        this.debug = debug;
    }

    /** Creates (or returns existing) a mapper session for the given player. */
    public MapperSession createSession(Player player) {
        final MapperSession existing = this.sessions.get(player.getUniqueId());
        if (existing != null) {
            return existing;
        }

        final MapperSession session = new MapperSession(player, this, this.workerHost, this.workerPort);
        final MapperSession raced = this.sessions.putIfAbsent(player.getUniqueId(), session);
        final MapperSession active = raced != null ? raced : session;

        // Attach any backend entity id that arrived before this session existed.
        final Integer pendingBackend = this.pendingBackendIds.remove(player.getUniqueId());
        if (pendingBackend != null) {
            active.setBackendEntityId(pendingBackend);
        }

        if (raced == null) {
            session.connect();
        }
        return active;
    }

    public MapperSession getSession(UUID uuid) {
        return this.sessions.get(uuid);
    }

    public void onPlayerDisconnect(Player player) {
        final MapperSession session = this.sessions.remove(player.getUniqueId());
        this.pendingBackendIds.remove(player.getUniqueId());
        if (session != null) {
            this.removeMappingsFor(session);
            session.disconnect();
        }
    }

    public void removeSession(MapperSession session) {
        this.sessions.remove(session.getPlayer().getUniqueId());
        this.removeMappingsFor(session);
    }

    private void removeMappingsFor(MapperSession session) {
        final int worker = session.getWorkerEntityId();
        final int backend = session.getBackendEntityId();
        if (worker >= 0) {
            final Integer mapped = this.workerToBackend.remove(worker);
            if (mapped != null) {
                this.backendToWorker.remove(mapped);
            }
        }
        if (backend >= 0) {
            final Integer mapped = this.backendToWorker.remove(backend);
            if (mapped != null) {
                this.workerToBackend.remove(mapped);
            }
            this.modelCache.remove(backend);
        }
    }

    /** Caches the latest S2C model/state payload for a backend entity (already id-remapped). */
    public void cacheModelData(int backendId, byte[] data) {
        this.modelCache.put(backendId, data);
    }

    /** Returns the cached S2C model/state payload for a backend entity, or {@code null}. */
    public byte[] getCachedModelData(int backendId) {
        return this.modelCache.get(backendId);
    }

    /** Registers the backend entity id of a real player (from the backend JOIN_GAME packet). */
    public void onBackendEntityId(UUID uuid, int backendId) {
        final MapperSession session = this.sessions.get(uuid);
        if (session != null) {
            session.setBackendEntityId(backendId);
            this.refreshMappings(session);
        } else {
            this.pendingBackendIds.put(uuid, backendId);
        }
    }

    /** Registers the worker entity id of a fake client (from the Worker JOIN_GAME packet). */
    public void onWorkerEntityId(MapperSession session, int workerId) {
        session.setWorkerEntityId(workerId);
        this.refreshMappings(session);
    }

    private void refreshMappings(MapperSession session) {
        final int worker = session.getWorkerEntityId();
        final int backend = session.getBackendEntityId();
        if (worker < 0 || backend < 0) {
            return;
        }
        this.workerToBackend.put(worker, backend);
        this.backendToWorker.put(backend, worker);
        this.logger.info("[Freesia] entity mapping: workerId {} -> backendId {} (player {})",
                worker, backend, session.getPlayer().getUsername());
    }

    public Integer backendForWorker(int workerId) {
        return this.workerToBackend.get(workerId);
    }

    /** The Worker-side entity id of a player's fake client, or -1 if unknown yet. */
    public int getWorkerEntityId(UUID uuid) {
        final MapperSession session = this.sessions.get(uuid);
        return session != null ? session.getWorkerEntityId() : -1;
    }

    /** Fallback: push a rewritten YSM payload to every online player except {@code owner}. */
    public void broadcastToOthers(UUID owner, byte[] data) {
        for (Player player : this.proxy.getAllPlayers()) {
            if (!player.getUniqueId().equals(owner)) {
                player.sendPluginMessage(YsmConstants.CHANNEL_KEY, data);
            }
        }
    }

    public Integer workerForBackend(int backendId) {
        return this.backendToWorker.get(backendId);
    }

    public boolean isDebug() {
        return this.debug;
    }

    public Logger getLogger() {
        return this.logger;
    }
}
