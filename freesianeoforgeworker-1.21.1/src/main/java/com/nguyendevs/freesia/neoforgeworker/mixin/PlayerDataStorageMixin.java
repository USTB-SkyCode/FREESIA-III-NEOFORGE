package com.nguyendevs.freesia.neoforgeworker.mixin;

import com.mojang.authlib.GameProfile;
import com.nguyendevs.freesia.neoforgeworker.FreesiaNeoForgeWorker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * The fake proxy clients use the real players' UUIDs but must not write vanilla playerdata
 * files into {@code world/playerdata}. Instead we persist their full NBT (which includes YSM's
 * model DataAttachment) into {@code world/freesia_playerdata/<uuid>.dat} so an equipped model
 * survives a re-join, and fall back to a standard "null player" tag on first join.
 */
@Mixin(PlayerDataStorage.class)
public abstract class PlayerDataStorageMixin {
    @Unique
    private static CompoundTag standardTag;
    @Unique
    private static volatile boolean loaded = false;

    @Unique
    private static void loadNullPlayer() {
        if (loaded) {
            return;
        }
        synchronized (PlayerDataStorageMixin.class) {
            if (loaded) {
                return;
            }
            ServerPlayer wrappedNullPlayer = new ServerPlayer(
                    FreesiaNeoForgeWorker.SERVER_INST,
                    FreesiaNeoForgeWorker.SERVER_INST.overworld(),
                    new GameProfile(UUID.randomUUID(), "114514"),
                    new ClientInformation("en_US", 4, ChatVisiblity.FULL, true, 0, HumanoidArm.RIGHT, false, true));
            final CompoundTag nullTag = new CompoundTag();
            nullTag.put("freesia_null_entity", IntTag.valueOf(1));
            standardTag = wrappedNullPlayer.saveWithoutId(nullTag);
            standardTag.remove("freesia_null_entity");
            // Survival mode (0). The fake client is made invulnerable by LivingEntityMixin,
            // so it won't die; survival (rather than spectator) keeps YSM model tracking active.
            standardTag.putInt("playerGameType", 0);
            loaded = true;
        }
    }

    @Unique
    private static Path freesiaDataDir() {
        return FreesiaNeoForgeWorker.SERVER_INST.getWorldPath(LevelResource.ROOT)
                .resolve("freesia_playerdata");
    }

    @Unique
    private static Path dataFile(UUID uuid) {
        return freesiaDataDir().resolve(uuid.toString() + ".dat");
    }

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    public void freesiaWorker$onSaveCalled(Player player, CallbackInfo ci) {
        // Serialize the full player state (including YSM's model DataAttachment) and persist it
        // so the equipped model survives a re-join instead of being discarded.
        final CompoundTag tag = new CompoundTag();
        player.saveWithoutId(tag);
        persist(player.getUUID(), tag);
        ci.cancel();
    }

    @Inject(method = "load(Lnet/minecraft/world/entity/player/Player;)Ljava/util/Optional;", at = @At("HEAD"),
            cancellable = true)
    public void freesiaWorker$onLoadCalled(Player player, CallbackInfoReturnable<Optional<CompoundTag>> cir) {
        CompoundTag tag = restore(player.getUUID());
        if (tag == null) {
            loadNullPlayer();
            tag = standardTag.copy();
        }
        // Keep the fake client in survival mode for YSM model tracking.
        tag.putInt("playerGameType", 0);
        player.load(tag);
        cir.setReturnValue(Optional.of(tag));
    }

    @Unique
    private static void persist(UUID uuid, CompoundTag tag) {
        try {
            final Path dir = freesiaDataDir();
            Files.createDirectories(dir);
            NbtIo.writeCompressed(tag, dataFile(uuid));
        } catch (IOException ignored) {
            // Best-effort: a failed save only loses persistence for this session.
        }
    }

    @Unique
    private static CompoundTag restore(UUID uuid) {
        try {
            final Path file = dataFile(uuid);
            if (Files.exists(file)) {
                return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            }
        } catch (IOException ignored) {
            // Fall through to a fresh null-player tag.
        }
        return null;
    }
}
