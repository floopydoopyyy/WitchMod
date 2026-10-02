package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Player;

import com.oliver.witchmod.effects.blessings.BlessingPuppeteer;

/**
 * puppeteer: Player.tick resets noPhysics every tick (to "is spectator"); a burrowing endermite puppet keeps it set
 * between ticks, so the server's movement check doesn't reject it for being inside blocks.
 */
@Mixin(Player.class)
public abstract class PlayerBurrowMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void witchmod$stayNoclip(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (BlessingPuppeteer.burrowed(self)) {
            self.noPhysics = true;
        }
    }
}
