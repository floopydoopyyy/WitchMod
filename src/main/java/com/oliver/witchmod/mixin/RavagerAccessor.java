package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** puppeteer: a ravager puppet's bite + roar animations, run by hand (the client-side dummy is never ticked). */
@Mixin(net.minecraft.world.entity.monster.Ravager.class)
public interface RavagerAccessor {
    @Accessor("attackTick")
    void witchmodSetAttackTick(int ticks);

    @Accessor("attackTick")
    int witchmodGetAttackTick();

    @Accessor("roarTick")
    void witchmodSetRoarTick(int ticks);

    @Accessor("roarTick")
    int witchmodGetRoarTick();
}
