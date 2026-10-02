package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.LocalPlayer;

import com.oliver.witchmod.client.PuppeteerClient;

/**
 * puppeteer: a silverfish puppet hidden inside a stone block (or an endermite burrowing through rock) must stay there —
 * vanilla nudges a local player out of any block it's stuck in, so that nudge is skipped meanwhile.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerPushMixin {
    @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
    private void witchmod$stayHidden(double x, double z, CallbackInfo ci) {
        if (PuppeteerClient.inBlocksOnPurpose((LocalPlayer) (Object) this)) {
            ci.cancel();
        }
    }
}
