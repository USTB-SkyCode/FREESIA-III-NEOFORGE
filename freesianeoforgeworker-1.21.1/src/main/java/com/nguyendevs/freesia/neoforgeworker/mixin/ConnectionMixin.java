package com.nguyendevs.freesia.neoforgeworker.mixin;

import com.mojang.logging.LogUtils;
import com.nguyendevs.freesia.neoforgeworker.impl.FakeCompressionEncoder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import net.minecraft.network.CompressionDecoder;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.configuration.ClientboundResetChatPacket;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import net.minecraft.network.protocol.configuration.ClientboundSelectKnownPacks;
import net.minecraft.network.protocol.configuration.ClientboundUpdateEnabledFeaturesPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ClientboundGameProfilePacket;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Worker must not blast the proxy with world/entity packets. Only whitelist the packets a fake
 * client actually needs (login / config / custom-payload / ping / keep-alive) and drop the rest.
 * Also replaces the compression encoder with a no-op one so framing stays valid without compression.
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Shadow
    private Channel channel;

    @Inject(method = "setupCompression", at = @At("HEAD"), cancellable = true)
    public void freesiaWorker$onSetupCompression(int threshold, boolean bundling, CallbackInfo ci) {
        if (threshold >= 0) {
            ChannelHandler handler = this.channel.pipeline().get("decompress");
            if (handler instanceof CompressionDecoder compressionDecoder) {
                compressionDecoder.setThreshold(threshold, bundling);
            } else {
                this.channel.pipeline().addAfter("splitter", "decompress", new CompressionDecoder(threshold, bundling));
            }

            handler = this.channel.pipeline().get("compress");
            if (!(handler instanceof FakeCompressionEncoder)) {
                this.channel.pipeline().addAfter("prepender", "compress", new FakeCompressionEncoder());
            }
        } else {
            if (this.channel.pipeline().get("decompress") instanceof CompressionDecoder) {
                this.channel.pipeline().remove("decompress");
            }
            if (this.channel.pipeline().get("compress") instanceof FakeCompressionEncoder) {
                this.channel.pipeline().remove("compress");
            }
        }
        ci.cancel();
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    public void freesiaWorker$sendPacket(Packet<?> packet, CallbackInfo ci) {
        if (!this.checkPacket(packet)) {
            LOGGER.info("[Freesia-Worker] Dropping packet: " + packet.getClass().getName());
            ci.cancel();
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"), cancellable = true)
    public void freesiaWorker$sendPacketWithListener(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        if (!this.checkPacket(packet)) {
            if (listener != null) {
                listener.onSuccess();
            }
            ci.cancel();
        }
    }

    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V",
            at = @At("HEAD"), cancellable = true)
    public void freesiaWorker$sendPacketWithListenerAndBool(Packet<?> packet, PacketSendListener listener, boolean bl,
            CallbackInfo ci) {
        if (!this.checkPacket(packet)) {
            if (listener != null) {
                listener.onSuccess();
            }
            ci.cancel();
        }
    }

    @Unique
    private boolean checkPacket(Packet<?> pkt) {
        if (pkt instanceof ClientboundCustomPayloadPacket payload) {
            LOGGER.info("[Freesia-Worker] Sending custom payload: " + payload);
        }
        return pkt instanceof ClientboundLoginCompressionPacket
                || pkt instanceof ClientboundHelloPacket
                || pkt instanceof ClientboundGameProfilePacket
                || pkt instanceof ClientboundCustomQueryPacket
                || pkt instanceof ClientboundCustomPayloadPacket
                || pkt instanceof ClientboundPingPacket
                || pkt instanceof ClientboundFinishConfigurationPacket
                || pkt instanceof ClientboundLoginPacket
                || pkt instanceof ClientboundLoginDisconnectPacket
                || pkt instanceof ClientboundStartConfigurationPacket
                || pkt instanceof ClientboundRegistryDataPacket
                || pkt instanceof ClientboundUpdateEnabledFeaturesPacket
                || pkt instanceof ClientboundSelectKnownPacks
                || pkt instanceof ClientboundUpdateTagsPacket
                || pkt instanceof ClientboundResetChatPacket
                || pkt instanceof ClientboundKeepAlivePacket;
    }
}
