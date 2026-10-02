package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** puppeteer: a horse puppet rears up as it leaps, like a ridden horse — its stand animation, run by hand. */
@Mixin(net.minecraft.world.entity.animal.horse.AbstractHorse.class)
public interface AbstractHorseAccessor {
    @Accessor("standAnim")
    float witchmodGetStandAnim();

    @Accessor("standAnim")
    void witchmodSetStandAnim(float value);

    @Accessor("standAnimO")
    void witchmodSetStandAnimO(float value);
}
