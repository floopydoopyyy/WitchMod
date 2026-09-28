package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import net.minecraft.world.entity.LivingEntity;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * dexterous — shortens the fixed 5-tick shield-raise gate in isBlocking(). that delay is a hardcoded
 * constant, not use-duration driven, so only a constant swap can touch it. gated on the synced dexterous
 * flag; other entities keep vanilla.
 */
@Mixin(LivingEntity.class)
public class ShieldRaiseMixin {
    @ModifyConstant(method = "isBlocking", constant = @Constant(intValue = 5))
    private int witchmod$fasterShieldRaise(int original) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getData(WitchModAttachments.DEXTEROUS_ACTIVE) >= 0) {
            return Math.min(original, Config.DEXTEROUS_SHIELD_DELAY_TICKS.get());
        }
        return original;
    }
}
