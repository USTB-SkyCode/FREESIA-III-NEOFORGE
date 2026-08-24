package com.nguyendevs.freesia.neoforge.proxy;

import com.nguyendevs.freesia.neoforge.YsmConstants;
import com.nguyendevs.freesia.neoforge.mapper.MapperManager;
import com.nguyendevs.freesia.neoforge.util.BufUtil;
import com.velocitypowered.api.proxy.Player;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Rewrites Yes Steve Model packets that cross the proxy, translating entity ids between the
 * Worker (fake client) and the backend (real player).
 * <p>
 * Wire format of every payload: {@code VarInt discriminator} + message body (NeoForge 1.21.1
 * "SimpleChannel-like" wrapper, same numbering as Forge 1.20.1 YSM).
 */
public class YsmPacketProxy {

    /** Outcome of rewriting a packet. */
    public enum Kind { PASS, MODIFY, DROP }

    public record Result(Kind kind, byte[] data, boolean broadcast) {
        static Result pass(byte[] data) {
            return new Result(Kind.PASS, data, false);
        }

        static Result modify(byte[] data, boolean broadcast) {
            return new Result(Kind.MODIFY, data, broadcast);
        }

        static Result drop() {
            return new Result(Kind.DROP, null, false);
        }
    }

    private final Player player;
    private final MapperManager mapper;

    private String ysmVersion = YsmConstants.PROTOCOL_VERSION;
    private boolean handshaked = false;

    public YsmPacketProxy(Player player, MapperManager mapper) {
        this.player = player;
        this.mapper = mapper;
    }

    /** Worker -> client. */
    public Result processS2C(byte[] data) {
        final ByteBuf buf = Unpooled.wrappedBuffer(data);
        try {
            final int packetId = BufUtil.readVarInt(buf);

            switch (packetId) {
                case YsmConstants.S2C_SET_MODEL, YsmConstants.S2C_ANIMATION -> {
                    // [discriminator] + [VarInt workerEntityId] + [body]
                    final int workerEntityId = BufUtil.readVarInt(buf);
                    final Integer backendId = this.mapper.backendForWorker(workerEntityId);
                    if (backendId == null) {
                        // Unknown worker entity; forward unchanged rather than drop.
                        debug("S2C id={} workerEntity={} UNKNOWN -> pass-through", packetId, workerEntityId);
                        return Result.pass(data);
                    }
                    final int selfWorkerId = this.mapper.getWorkerEntityId(this.player.getUniqueId());
                    final boolean isSelf = selfWorkerId >= 0 && workerEntityId == selfWorkerId;
                    debug("S2C id={} workerEntity={} -> backendEntity={} (self={})",
                            packetId, workerEntityId, backendId, isSelf);
                    final ByteBuf out = Unpooled.buffer(data.length);
                    BufUtil.writeVarInt(out, packetId);
                    BufUtil.writeVarInt(out, backendId);
                    out.writeBytes(buf, buf.readableBytes());
                    final byte[] remapped = readAll(out);
                    // Cache the remapped payload so we can re-broadcast it later when the
                    // backend re-tracks this entity (dimension switch / chunk load).
                    this.mapper.cacheModelData(backendId, remapped);
                    // Self = own model/state: forward to self AND broadcast to others (fallback).
                    // Not-self = another player's state: forward to self only.
                    return Result.modify(remapped, isSelf);
                }

                case YsmConstants.S2C_MOLANG -> {
                    // [discriminator] + [VarInt count] + [VarInt workerId]* + [UTF expr]
                    final int count = BufUtil.readVarInt(buf);
                    final int[] remapped = new int[count];
                    boolean changed = false;
                    for (int i = 0; i < count; i++) {
                        final int workerId = BufUtil.readVarInt(buf);
                        final Integer backendId = this.mapper.backendForWorker(workerId);
                        remapped[i] = backendId == null ? workerId : backendId;
                        if (backendId != null) {
                            changed = true;
                        }
                    }
                    if (!changed) {
                        debug("S2C id={} molang no entity known -> pass-through", packetId);
                        return Result.pass(data);
                    }
                    debug("S2C id={} molang remapped {} entities", packetId, count);
                    final ByteBuf out = Unpooled.buffer(data.length);
                    BufUtil.writeVarInt(out, packetId);
                    BufUtil.writeVarInt(out, count);
                    for (int id : remapped) {
                        BufUtil.writeVarInt(out, id);
                    }
                    out.writeBytes(buf, buf.readableBytes());
                    return Result.modify(readAll(out), false);
                }

                case YsmConstants.S2C_HANDSHAKE -> {
                    final String version = BufUtil.readUtf(buf);
                    this.ysmVersion = version;
                    this.handshaked = true;
                    debug("S2C handshake version={}", version);
                    return Result.pass(data);
                }

                default -> {
                    return Result.pass(data);
                }
            }
        } finally {
            buf.release();
        }
    }

    /** Client -> Worker. */
    public Result processC2S(byte[] data) {
        final ByteBuf buf = Unpooled.wrappedBuffer(data);
        try {
            final int packetId = BufUtil.readVarInt(buf);

            switch (packetId) {
                case YsmConstants.C2S_HANDSHAKE -> {
                    final String version = BufUtil.readUtf(buf);
                    this.ysmVersion = version;
                    this.handshaked = true;
                    debug("C2S handshake version={}", version);
                    return Result.pass(data);
                }

                default -> {
                    return Result.pass(data);
                }
            }
        } finally {
            buf.release();
        }
    }

    private void debug(String fmt, Object... args) {
        if (this.mapper.isDebug()) {
            this.mapper.getLogger().info("[Freesia] " + fmt, args);
        }
    }

    private static byte[] readAll(ByteBuf buf) {
        final byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        buf.release();
        return out;
    }

    public Player getPlayer() {
        return this.player;
    }

    public boolean hasHandshaked() {
        return this.handshaked;
    }
}
