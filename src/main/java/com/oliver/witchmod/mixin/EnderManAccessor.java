package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.EnderMan;

/** puppeteer: an enraged enderman puppet's dropped jaw and shake (the dummy's "creepy" flag, set by hand). */
@Mixin(EnderMan.class)
public interface EnderManAccessor {
    @Accessor("DATA_CREEPY")
    static EntityDataAccessor<Boolean> witchmodCreepyKey() {
        throw new AssertionError();
    }
}
