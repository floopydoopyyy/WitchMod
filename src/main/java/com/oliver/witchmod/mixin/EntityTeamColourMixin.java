package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;

import com.oliver.witchmod.effects.blessings.puppeteer.client.PuppetSenses;

/** puppeteer: what a puppet picks out glows in its own colours (a warden's marked red and sniffed teal, a shulker's lock). */
@Mixin(Entity.class)
public abstract class EntityTeamColourMixin {
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void witchmod$wardenColour(CallbackInfoReturnable<Integer> cir) {
        int colour = PuppetSenses.outlineColour((Entity) (Object) this);
        if (colour >= 0) {
            cir.setReturnValue(colour);
        }
    }
}
