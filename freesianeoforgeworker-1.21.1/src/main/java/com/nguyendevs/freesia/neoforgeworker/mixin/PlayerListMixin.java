package com.nguyendevs.freesia.neoforgeworker.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

/**
 * Always allow the fake proxy clients to log in (bypass whitelist / player limit / ban checks).
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "canPlayerLogin", at = @At("HEAD"), cancellable = true)
    public void freesiaWorker$canPlayerLogin(SocketAddress socketAddress, GameProfile gameProfile,
            CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(null);
    }
}
