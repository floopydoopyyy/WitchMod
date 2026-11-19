package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;

import com.oliver.witchmod.effects.blessings.puppeteer.client.WardenSenses;

/** puppeteer (warden): a blind warden has no sky — no sun, no moon, no stars — as under Darkness. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererSkyMixin {
    @Inject(method = "doesMobEffectBlockSky", at = @At("HEAD"), cancellable = true)
    private void witchmod$wardenBlind(Camera camera, CallbackInfoReturnable<Boolean> cir) {
        if (WardenSenses.blocksSky()) {
            cir.setReturnValue(true);
        }
    }
}
