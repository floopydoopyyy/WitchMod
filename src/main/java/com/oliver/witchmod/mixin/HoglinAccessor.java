package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** puppeteer: a hoglin puppet's tusk-swing animation, run by hand (the client-side dummy is never ticked). */
@Mixin(net.minecraft.world.entity.monster.hoglin.Hoglin.class)
public interface HoglinAccessor {
    @Accessor("attackAnimationRemainingTicks")
    void witchmodSetAttackAnimationTicks(int ticks);
}
