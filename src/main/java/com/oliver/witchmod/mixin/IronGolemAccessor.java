package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** puppeteer: an iron golem puppet's arm-swing animation, run by hand (the client-side dummy is never ticked). */
@Mixin(net.minecraft.world.entity.animal.IronGolem.class)
public interface IronGolemAccessor {
    @Accessor("attackAnimationTick")
    void witchmodSetAttackAnimationTick(int ticks);
}
