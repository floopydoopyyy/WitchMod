package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.animal.Sheep;

/** puppeteer: drives the client-side sheep puppet's grazing animation (it's never ticked, so we count it down). */
@Mixin(Sheep.class)
public interface SheepAccessor {
    @Accessor("eatAnimationTick")
    int witchmodGetEatAnimationTick();

    @Accessor("eatAnimationTick")
    void witchmodSetEatAnimationTick(int ticks);
}
