package com.oliver.witchmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;

/** puppeteer: a parrot puppet's mimicry — the parrot's own impression of a mob (PARROT_AMBIENT if it has none). */
@Mixin(net.minecraft.world.entity.animal.Parrot.class)
public interface ParrotInvoker {
    @Invoker("getImitatedSound")
    static SoundEvent witchmodImitatedSound(EntityType<?> type) {
        throw new AssertionError();
    }
}
