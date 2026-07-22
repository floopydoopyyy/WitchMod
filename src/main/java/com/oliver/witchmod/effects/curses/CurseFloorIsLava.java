package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * The ground remembers being lava, and it only notices you when you stop (master-spec Floor Is Lava). Stand
 * still past the grace period and you start burning — and it gets worse the longer you stand there.
 *
 * <p><b>The grace period and the ramp are doing opposite jobs, and both are necessary.</b> The grace period
 * is what makes the curse survivable to play around: you can still craft, read a sign or dig through a chest.
 * The ramp is what stops it being a flat tax you simply eat — the longer you ignore it the sharper it bites,
 * so it eventually forces you to move rather than merely costing you health. The cap on the ramp then stops
 * an AFK player being executed outright, which would be a disconnect rather than a joke.
 *
 * <p>Damage uses vanilla's own {@code HOT_FLOOR} source — the magma-block one, which is exactly what the
 * sacrificial item is. Fire resistance therefore protects, which is deliberate counterplay rather than an
 * oversight.
 *
 * <p>Movement is measured as real displacement, so turning on the spot doesn't save you. It is deliberately
 * NOT paused while a GUI is open: cowering in your inventory is the exact behaviour the curse exists to
 * punish, and the grace period is already the allowance for legitimate crafting.
 */
public final class CurseFloorIsLava extends Effect {
    /** victim -> where they were when they last counted as having moved. */
    private static final Map<UUID, Vec3> ANCHOR = new HashMap<>();
    /** victim -> ticks spent stationary since that anchor. */
    private static final Map<UUID, Integer> STILL_TICKS = new HashMap<>();
    /** victim -> how many consecutive burns they've taken, which drives the ramp. */
    private static final Map<UUID, Integer> BURNS = new HashMap<>();

    public CurseFloorIsLava() {
        super(EffectCategory.CURSE, EffectCostTier.MAJOR, 55, () -> Items.MAGMA_BLOCK);
    }

    /** You find out the first time the floor bites (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        reset(target);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        ANCHOR.remove(target.getUUID());
        STILL_TICKS.remove(target.getUUID());
        BURNS.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        UUID id = target.getUUID();
        Vec3 anchor = ANCHOR.get(id);
        if (anchor == null) {
            reset(target); // self-heal after a relog
            return;
        }

        double reset = Config.FIL_MOVEMENT_RESET_DISTANCE.get();
        if (target.position().distanceToSqr(anchor) >= reset * reset) {
            reset(target);
            return;
        }

        int still = STILL_TICKS.merge(id, 1, Integer::sum);
        int grace = Config.FIL_GRACE_TICKS.get();
        if (still <= grace) {
            return;
        }
        // Warn a moment before the first burn, so it reads as the floor heating up rather than random damage.
        if (still % 5 == 0) {
            smoulder(target, still - grace);
        }
        if ((still - grace) % Config.FIL_DAMAGE_INTERVAL.get() != 0) {
            return;
        }
        burn(target, BURNS.merge(id, 1, Integer::sum));
    }

    private static void reset(ServerPlayer target) {
        ANCHOR.put(target.getUUID(), target.position());
        STILL_TICKS.put(target.getUUID(), 0);
        BURNS.put(target.getUUID(), 0);
    }

    private void burn(ServerPlayer target, int consecutiveBurns) {
        double damage = Math.min(
                Config.FIL_BASE_DAMAGE.get() + Config.FIL_RAMP_PER_BURN.get() * (consecutiveBurns - 1),
                Config.FIL_MAX_DAMAGE.get());

        ServerLevel level = target.serverLevel();
        target.hurt(level.damageSources().hotFloor(), (float) damage);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.GENERIC_BURN, SoundSource.PLAYERS, 0.5F, 1.6F + target.getRandom().nextFloat() * 0.2F);
        markDiscoveredByVictim(target);
    }

    /** Flames licking up around the feet, thickening as the ramp climbs — the tell that it's getting worse. */
    private static void smoulder(ServerPlayer target, int ticksBurning) {
        int count = 2 + Math.min(10, ticksBurning / 20);
        target.serverLevel().sendParticles(ParticleTypes.FLAME,
                target.getX(), target.getY() + 0.05, target.getZ(),
                count, 0.3, 0.02, 0.3, 0.01);
        target.serverLevel().sendParticles(ParticleTypes.SMOKE,
                target.getX(), target.getY() + 0.05, target.getZ(),
                count, 0.3, 0.02, 0.3, 0.005);
    }
}
