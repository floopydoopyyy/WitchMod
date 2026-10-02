package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import com.oliver.witchmod.effects.blessings.BlessingPuppeteer;

/**
 * puppeteer: a puppet never drops into the player's swimming mode (the lying-flat sprint-swim, which drags the camera
 * down) — it stays upright, and swimmer puppets get their own dive / speed instead (PuppeteerClient). both sides.
 */
@Mixin(Entity.class)
public abstract class EntitySwimMixin {
    @Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
    private void witchmod$noPuppetSwimming(CallbackInfo ci) {
        if ((Object) this instanceof Player player && BlessingPuppeteer.isPuppetSafe(player)) {
            if (player.isSwimming()) {
                player.setSwimming(false);
            }
            ci.cancel();
        }
    }
}
