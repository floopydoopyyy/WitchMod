package com.oliver.witchmod.blocks;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/** shared helpers for standing in holy water — the touching-it check and the subtle blocked-magic fizzle fx. */
public final class HolyWater {
    private HolyWater() {}

    /** true while {@code entity} is touching Holy Water (feet in it — standing OR submerged). */
    public static boolean isProtecting(Entity entity) {
        return entity != null && entity.isInFluidType(WitchModFluids.PURIFYING_WATER_TYPE.get());
    }

    /** true if the block at {@code pos} holds Holy Water (source or flowing). */
    public static boolean isProtectingFluidAt(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        net.minecraft.world.level.material.Fluid fluid = level.getFluidState(pos).getType();
        return fluid == WitchModFluids.PURIFYING_WATER.get() || fluid == WitchModFluids.PURIFYING_WATER_FLOWING.get();
    }

    /** A quiet "the magic fizzles out" cue at a blocked target — a few white sparks + a soft hiss/chime. */
    public static void fizzle(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.END_ROD, x, y + 1.0, z, 8, 0.35, 0.45, 0.35, 0.01);
        level.sendParticles(ParticleTypes.SPLASH, x, y + 1.0, z, 6, 0.3, 0.3, 0.3, 0.0);
        level.playSound(null, x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.35F, 1.7F);
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.35F, 1.5F);
    }

    /** fizzle centred on an entity. */
    public static void fizzle(Entity entity) {
        if (entity.level() instanceof ServerLevel level) {
            fizzle(level, entity.getX(), entity.getY(), entity.getZ());
        }
    }
}
