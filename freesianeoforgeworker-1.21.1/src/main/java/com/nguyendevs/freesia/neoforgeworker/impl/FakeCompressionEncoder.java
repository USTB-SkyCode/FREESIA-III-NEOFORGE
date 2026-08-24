package com.nguyendevs.freesia.neoforgeworker.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import net.minecraft.network.VarInt;

/**
 * Writes packets WITHOUT compressing them (just a 0-length "uncompressed" header).
 * The proxy (MCPRotocolLib) connects with no compression, so this keeps the wire framing valid.
 */
public class FakeCompressionEncoder extends MessageToByteEncoder<ByteBuf> {
    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
        int i = msg.readableBytes();
        if (i > 8388608) {
            throw new IllegalArgumentException("Packet too big (is " + i + ", should be less than 8388608)");
        }
        VarInt.write(out, 0);
        out.writeBytes(msg);
    }
}
