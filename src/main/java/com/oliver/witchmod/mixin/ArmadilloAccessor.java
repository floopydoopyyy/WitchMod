package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** puppeteer: an armadillo puppet's time in its curl state (it decides what the model hides), run by hand. */
@Mixin(net.minecraft.world.entity.animal.armadillo.Armadillo.class)
public interface ArmadilloAccessor {
    @Accessor("inStateTicks")
    long witchmodGetInStateTicks();

    @Accessor("inStateTicks")
    void witchmodSetInStateTicks(long ticks);
}
