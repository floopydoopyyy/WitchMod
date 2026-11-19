package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import com.oliver.witchmod.effects.blessings.puppeteer.PuppetSteps;

/** puppeteer / disguise: a puppet's footsteps are its mob's, and a disguised player kicks up no sprinting dust. */
@Mixin(Entity.class)
public abstract class EntityStepMixin {
    @Inject(method = "walkingStepSound", at = @At("HEAD"), cancellable = true)
    private void witchmod$puppetStep(BlockPos pos, BlockState state, CallbackInfo ci) {
        if ((Object) this instanceof Player player && PuppetSteps.replaceStep(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "playCombinationStepSounds", at = @At("HEAD"), cancellable = true)
    private void witchmod$puppetCombinationStep(BlockState primary, BlockState secondary, BlockPos primaryPos, BlockPos secondaryPos,
                                                CallbackInfo ci) {
        if ((Object) this instanceof Player player && PuppetSteps.replaceStep(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "canSpawnSprintParticle", at = @At("HEAD"), cancellable = true)
    private void witchmod$noSprintDust(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && PuppetSteps.hidesSprintDust(player)) {
            cir.setReturnValue(false);
        }
    }
}
