package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** puppeteer: a panda puppet's roll pose, run by hand (the client-side dummy is never ticked). */
@Mixin(net.minecraft.world.entity.animal.Panda.class)
public interface PandaAccessor {
    @Accessor("rollAmount")
    float witchmodGetRollAmount();

    @Accessor("rollAmount")
    void witchmodSetRollAmount(float amount);

    @Accessor("rollAmountO")
    void witchmodSetRollAmountO(float amount);
}
