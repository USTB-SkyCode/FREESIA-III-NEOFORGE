package com.nguyendevs.freesia.neoforgeworker.mixin;

import net.minecraft.Util;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Accepts any keep-alive response without running the full vanilla handler.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
    @Shadow
    private long keepAliveTime;

    @Shadow
    private int latency;

    @Shadow
    private boolean keepAlivePending;

    @Inject(method = "handleKeepAlive", at = @At("HEAD"), cancellable = true)
    public void freesiaWorker$onKeepAlive(ServerboundKeepAlivePacket packet, CallbackInfo ci) {
        int i = (int) (Util.getMillis() - this.keepAliveTime);
        this.latency = (this.latency * 3 + i) / 4;
        this.keepAlivePending = false;
        ci.cancel();
    }
}
