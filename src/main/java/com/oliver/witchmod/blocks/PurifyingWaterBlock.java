package com.oliver.witchmod.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModSounds;

/**
 * The Holy (Purifying) Water block. Bathing rapidly burns down active curse/blessing timers — that drain,
 * the shine particles and the magic-blocking are all driven per-player in {@link HolyWaterHandler}
 * (so they fire once per tick and cover flowing water too, not per-overlapping-block).
 */
public final class PurifyingWaterBlock extends LiquidBlock {
    public PurifyingWaterBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    /**
     * A source block being set = a bucket placement (no infinite sources form, so flow never makes new ones).
     * Play a sped-up "blessed" chime for emphasis, once, on that placement.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && state.getFluidState().isSource() && !oldState.is(this)) {
            level.playSound(null, pos, WitchModSounds.BLESSED.get(), SoundSource.BLOCKS, 0.45F, 1.6F);
        }
    }

    /** Undead that wade into holy water are burned — periodic tick damage (spaced by vanilla invuln frames). */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (level.isClientSide() || !(entity instanceof LivingEntity living) || !living.getType().is(EntityTypeTags.UNDEAD)) {
            return;
        }
        if (living.hurt(level.damageSources().magic(), (float) (double) Config.PURIFY_UNDEAD_DAMAGE.get())
                && level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.END_ROD, living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(),
                    3, 0.2, 0.3, 0.2, 0.01);
            sl.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.3F, 1.6F);
        }
    }

    /** Occasional shiny white "star" motes drifting up off the holy water's surface. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Rare (roughly 1/20 of the old rate — a 95% cut) so the surface only occasionally glints.
        if (!level.getBlockState(pos.above()).isAir() || random.nextInt(200) != 0) {
            return;
        }
        double x = pos.getX() + random.nextDouble();
        double y = pos.getY() + 0.9 + random.nextDouble() * 0.1;
        double z = pos.getZ() + random.nextDouble();
        // END_ROD = a slow, glowing white mote (holy shine); an occasional FIREWORK spark twinkles like a star.
        level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0, 0.02 + random.nextDouble() * 0.02, 0.0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.FIREWORK, x, y, z, 0.0, 0.03, 0.0);
        }
    }
}
