package com.nguyendevs.freesia.neoforgeworker.mixin;

import com.mojang.datafixers.DataFixer;
import com.nguyendevs.freesia.neoforgeworker.FreesiaNeoForgeWorker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.Proxy;
import java.util.function.BooleanSupplier;

/**
 * Strips the Worker down: no world ticking, no chunk tasks, no world saving — only the
 * connection listener keeps running so incoming proxy packets are processed.
 */
@Mixin(value = MinecraftServer.class, priority = 600)
public abstract class MinecraftServerMixin {
    @Unique
    volatile boolean shouldPollTask = true;

    @Shadow
    public abstract ServerConnectionListener getConnection();

    @Shadow
    public abstract PlayerList getPlayerList();

    @Inject(method = "pollTaskInternal", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/ServerTickRateManager;isSprinting()Z", shift = At.Shift.BEFORE),
            cancellable = true)
    public void freesiaWorker$onExecutingChunkSystemTasks(CallbackInfoReturnable<Boolean> cir) {
        if (this.shouldPollTask) {
            return;
        }
        cir.setReturnValue(false);
    }

    @Redirect(method = "tickChildren", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;tick(Ljava/util/function/BooleanSupplier;)V"))
    public void freesiaWorker$tickLevelHook(ServerLevel instance, BooleanSupplier booleanSupplier) {
        this.shouldPollTask = true;
    }

    @Redirect(method = "saveEverything", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;saveAllChunks(ZZZ)Z"))
    public boolean freesiaWorker$saveAllChunksHook(MinecraftServer instance, boolean bl, boolean bl2, boolean bl3) {
        return true;
    }

    @Inject(method = "<init>", at = @At(value = "TAIL"))
    private void freesiaWorker$staticBlockInject(Thread thread, LevelStorageSource.LevelStorageAccess levelStorageAccess,
            PackRepository packRepository, WorldStem worldStem, Proxy proxy, DataFixer dataFixer, Services services,
            ChunkProgressListenerFactory chunkProgressListenerFactory, CallbackInfo ci) {
        FreesiaNeoForgeWorker.SERVER_INST = (MinecraftServer) ((Object) this);
    }

    @Inject(method = "tickChildren", at = @At(value = "HEAD"), cancellable = true)
    public void freesiaWorker$onTickChildrenCall(BooleanSupplier booleanSupplier, CallbackInfo ci) {
        ci.cancel();
        this.getConnection().tick();
        this.getPlayerList().tick();
    }
}
