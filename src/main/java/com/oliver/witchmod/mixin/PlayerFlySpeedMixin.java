package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.player.Player;

import com.oliver.witchmod.effects.blessings.puppeteer.Puppets;

/**
 * a flier that mustn't sprint-fly (a slow puppet flier, or the bat disguise without Flight) never gets vanilla's doubled
 * fly speed for sprinting — however the sprint was started (held key, double-tap, toggle).
 */
@Mixin(Player.class)
public abstract class PlayerFlySpeedMixin {
    @Inject(method = "getFlyingSpeed", at = @At("RETURN"), cancellable = true)
    private void witchmod$noSprintFly(CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        if (self.isSprinting() && Puppets.noSprintFly(self)) {
            cir.setReturnValue(cir.getReturnValueF() * 0.5F);
        }
    }
}
