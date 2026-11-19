package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** puppeteer: a polar bear puppet's rear-up, run by hand (the client-side dummy is never ticked). */
@Mixin(net.minecraft.world.entity.animal.PolarBear.class)
public interface PolarBearAccessor {
    @Accessor("clientSideStandAnimation")
    float witchmodGetStand();

    @Accessor("clientSideStandAnimation")
    void witchmodSetStand(float stand);

    @Accessor("clientSideStandAnimationO")
    void witchmodSetStandO(float stand);
}
