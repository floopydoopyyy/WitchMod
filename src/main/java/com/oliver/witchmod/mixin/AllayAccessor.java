package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** puppeteer: an allay puppet's hold-out-the-item arm pose, run by hand (the client-side dummy is never ticked). */
@Mixin(net.minecraft.world.entity.animal.allay.Allay.class)
public interface AllayAccessor {
    @Accessor("holdingItemAnimationTicks")
    float witchmodGetHoldingTicks();

    @Accessor("holdingItemAnimationTicks")
    void witchmodSetHoldingTicks(float ticks);

    @Accessor("holdingItemAnimationTicks0")
    void witchmodSetHoldingTicks0(float ticks);
}
