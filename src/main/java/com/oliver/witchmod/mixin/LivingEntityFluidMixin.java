package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;

import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.PuppetType;

/**
 * puppeteer: a strider puppet stands on lava, as the real strider does (its canStandOnFluid) — lava sources become a
 * floor underfoot and it walks rather than wades. both sides (the possessed type is synced).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFluidMixin {
    @Inject(method = "canStandOnFluid", at = @At("HEAD"), cancellable = true)
    private void witchmod$striderOnLava(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && fluid.is(FluidTags.LAVA) && player.hasData(WitchModAttachments.PUPPET_TYPE)
                && PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.STRIDER) {
            cir.setReturnValue(true);
        }
    }
}
