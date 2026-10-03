package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.Minecraft;

/**
 * puppeteer: lets a pounce puppet (spider / wolf) clear the vanilla miss-swing cooldown, which otherwise blocks
 * left-click input for 10 ticks after a missed swing — so a swing at air or an out-of-reach entity never pounced.
 */
@Mixin(Minecraft.class)
public interface MinecraftMissTimeAccessor {
    @Accessor("missTime")
    void witchmodSetMissTime(int ticks);
}
