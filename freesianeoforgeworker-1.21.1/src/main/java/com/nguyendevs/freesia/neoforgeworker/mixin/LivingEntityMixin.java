package com.nguyendevs.freesia.neoforgeworker.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Belt-and-suspenders: every fake proxy client is a Player; make all Players on the Worker
 * invulnerable to every damage source so they can never die (mobs, fall, void, /kill, ...).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    public void freesiaWorker$onIsInvulnerableTo(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player) {
            cir.setReturnValue(true);
        }
    }
}
