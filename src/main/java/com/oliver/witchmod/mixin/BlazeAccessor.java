package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** puppeteer: a blaze puppet lights up in combat — the dummy's "charged" (on fire) flag, set by hand. */
@Mixin(net.minecraft.world.entity.monster.Blaze.class)
public interface BlazeAccessor {
    @Invoker("setCharged")
    void witchmodSetCharged(boolean charged);
}
