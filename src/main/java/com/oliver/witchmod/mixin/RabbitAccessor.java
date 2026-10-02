package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** puppeteer: a rabbit puppet's hop animation, run by hand (the client-side dummy is never ticked). */
@Mixin(net.minecraft.world.entity.animal.Rabbit.class)
public interface RabbitAccessor {
    @Accessor("jumpTicks")
    int witchmodGetJumpTicks();

    @Accessor("jumpTicks")
    void witchmodSetJumpTicks(int ticks);

    @Accessor("jumpDuration")
    int witchmodGetJumpDuration();

    @Accessor("jumpDuration")
    void witchmodSetJumpDuration(int ticks);
}
