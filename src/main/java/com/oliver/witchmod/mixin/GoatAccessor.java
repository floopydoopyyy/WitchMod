package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** puppeteer: a goat puppet lowers its head to charge a ram — its head tilt, run by hand (the dummy is never ticked). */
@Mixin(net.minecraft.world.entity.animal.goat.Goat.class)
public interface GoatAccessor {
    @Accessor("lowerHeadTick")
    int witchmodGetLowerHeadTick();

    @Accessor("lowerHeadTick")
    void witchmodSetLowerHeadTick(int ticks);
}
