package com.nguyendevs.freesia.neoforge.util;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Minimal VarInt / UTF / UUID helpers over a Netty {@link ByteBuf} (Minecraft wire encoding).
 */
public final class BufUtil {

    private BufUtil() {
    }

    public static int readVarInt(ByteBuf buf) {
        int result = 0;
        int shift = 0;
        while (true) {
            byte b = buf.readByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
            if (shift > 35) {
                throw new IllegalStateException("VarInt too big");
            }
        }
    }

    public static void writeVarInt(ByteBuf buf, int value) {
        while ((value & ~0x7F) != 0) {
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    public static String readUtf(ByteBuf buf) {
        int len = readVarInt(buf);
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeUtf(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    public static UUID readUuid(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }

    public static void writeUuid(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    /** Reads all remaining bytes into a new array. */
    public static byte[] readRemaining(ByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return data;
    }
}
