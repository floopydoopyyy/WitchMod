package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.PuppetType;

/**
 * puppeteer: the hidden player inside a puppet never shows flames of its own (the model shows its mob's — none for a
 * fire-proof one); and a strider puppet isn't shoved about by flowing lava (it walks it like still lava).
 */
@Mixin(Entity.class)
public abstract class EntityPuppetBodyMixin {
    @Inject(method = "displayFireAnimation", at = @At("HEAD"), cancellable = true)
    private void witchmod$noPlayerFlames(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && player.hasData(WitchModAttachments.PUPPET_TYPE)
                && !player.getData(WitchModAttachments.PUPPET_TYPE).isEmpty()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isPushedByFluid()Z", at = @At("HEAD"), cancellable = true)
    private void witchmod$striderIgnoresFlow(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && player.hasData(WitchModAttachments.PUPPET_TYPE)
                && PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.STRIDER && player.isInLava()) {
            cir.setReturnValue(false);
        }
    }
}
