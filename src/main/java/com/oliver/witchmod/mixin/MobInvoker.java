package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.sounds.SoundEvent;

/** puppeteer: a mob's own ambient call (a parrot puppet mimics the ones a real parrot has no impression of). */
@Mixin(net.minecraft.world.entity.Mob.class)
public interface MobInvoker {
    @Invoker("getAmbientSound")
    SoundEvent witchmodAmbientSound();
}
