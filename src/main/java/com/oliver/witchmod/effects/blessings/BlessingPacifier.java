package com.oliver.witchmod.effects.blessings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.effects.Blessings;

/**
 * an anti-grief aura: being
 * near anything that would blow a hole in your build simply neutralises it. Primed TNT and TNT minecarts
 * fizzle out, creepers deflate and can never detonate, fireballs wink out, and spreading fire is snuffed —
 * all with a protective sparkle. Keeps your builds safe even when someone's actively trying to grief them.
 *
 * <p>A per-tick sweep on a short interval (config), entity + block scans. Discovered the first time it snuffs
 * something out.
 */
public final class BlessingPacifier extends Effect {
    public BlessingPacifier() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> net.minecraft.world.item.Items.ALLIUM);
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        ServerLevel level = target.serverLevel();
        boolean acted = false;

        // entity sweep runs EVERY tick — fast projectiles (fire charges) can cross the radius in fewer than a
        // few ticks, so an interval would let them slip through.
        AABB box = target.getBoundingBox().inflate(Config.PACIFIER_RADIUS.get());
        for (Entity e : level.getEntities(target, box)) {
            if (e instanceof PrimedTnt tnt) {
                fizzle(level, tnt.position());
                tnt.discard();
                acted = true;
            } else if (e instanceof MinecartTNT cart) {
                fizzle(level, cart.position());
                cart.discard(); // takes the primed cart with it before it can go off
                acted = true;
            } else if (e instanceof Creeper creeper) {
                // deflate any swell and pacify — it can never complete an explosion near you.
                if (creeper.getSwellDir() > 0 || creeper.getTarget() != null) {
                    acted = true;
                    fizzle(level, creeper.position().add(0, 0.5, 0));
                }
                creeper.setSwellDir(-1);
                creeper.setTarget(null);
            } else if (isHarmfulProjectile(e)) {
                // fire charges / ghast + blaze fireballs / wither skulls / wind charges / dragon fireballs.
                fizzle(level, e.position());
                e.discard();
                acted = true;
            }
        }

        // snuff out spreading fire nearby (leaves campfires/torches — those aren't the FIRE block). This is
        // the expensive cubic scan, so it stays on the interval.
        if (ticksRemaining % Config.PACIFIER_CHECK_INTERVAL.get() == 0) {
            int br = Config.PACIFIER_BLOCK_RADIUS.get();
            BlockPos origin = target.blockPosition();
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int dx = -br; dx <= br; dx++) {
                for (int dy = -br; dy <= br; dy++) {
                    for (int dz = -br; dz <= br; dz++) {
                        cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        var block = level.getBlockState(cursor).getBlock();
                        if (block == Blocks.FIRE || block == Blocks.SOUL_FIRE) {
                            level.removeBlock(cursor, false);
                            fizzle(level, Vec3.atCenterOf(cursor));
                            acted = true;
                        }
                    }
                }
            }
        }

        if (acted) {
            Blessings.PACIFIER.get().markDiscoveredByVictim(target);
        }
    }

    /** harmful, build-threatening projectiles: fireballs (incl. fire charges), wind charges, wither skulls. */
    private static boolean isHarmfulProjectile(Entity e) {
        return e instanceof AbstractHurtingProjectile; // SmallFireball (fire charge), LargeFireball, WitherSkull, WindCharge, DragonFireball
    }

    /** the protective "fizzle": a puff of smoke plus a bright END_ROD sparkle and a soft extinguish hiss. */
    private static void fizzle(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 12, 0.3, 0.3, 0.3, 0.01);
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 8, 0.25, 0.25, 0.25, 0.02);
        level.playSound(null, BlockPos.containing(pos), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.4F);
    }
}
