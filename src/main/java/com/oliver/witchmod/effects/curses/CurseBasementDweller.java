package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModDamageTypes;

/**
 * you never go outside, and daylight makes that very clear. Standing in direct
 * sunlight burns you — a hat takes the edge off but never fully protects you.
 *
 * <p>A hat doesn't SOFTEN the burns, it SLOWS them: wearing anything on your head multiplies the gap between
 * burns, so you cook more slowly but never stop cooking. Tracking the next-burn tick per player (rather than
 * a fixed modulo) is what lets that interval change cleanly as a hat comes on or off.
 *
 * <p>Uses the mod's own {@code witchmod:sunburn} damage type: fatal on every difficulty, bypasses armour,
 * and carries no knockback (it's a slow cook, not a shove). "Direct sunlight" = daytime, clear sky access
 * (not raining/thundering over your head). Each burn hisses so it's clear where the damage is coming from.
 * Discovered on the first burn.
 */
public final class CurseBasementDweller extends Effect {
    /** victim -> {rampOrigin (grace end), nextBurnTick}. */
    private static final Map<UUID, long[]> STATE = new HashMap<>();

    public CurseBasementDweller() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 40, () -> Items.GRASS_BLOCK);
    }

    /** you find out the first time the sun bites (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        STATE.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        ServerLevel level = target.serverLevel();
        UUID id = target.getUUID();

        // genuinely IN the sun: daytime, sky visible straight up, and not raining on this spot.
        boolean inSun = level.isDay()
                && level.canSeeSky(target.blockPosition())
                && !level.isRainingAt(target.blockPosition().above());
        if (!inSun) {
            STATE.remove(id); // in the shade the clock stops; stepping back out starts a fresh, slow ramp
            return;
        }

        long now = level.getGameTime();
        boolean both = EnvBurn.bothActive(target);
        long[] s = STATE.get(id);
        if (s == null) {
            // just stepped into the sun. The burn interval STARTS slow and speeds up the longer you stay out
            // (ramp measured from the grace end); a combined-curse grace pushes the first burn back further.
            long rampOrigin = now + EnvBurn.graceTicks(both);
            long first = rampOrigin + EnvBurn.interval(0, Config.BASEMENT_START_INTERVAL.get(),
                    Config.BASEMENT_END_INTERVAL.get(), Config.BASEMENT_RAMP_TICKS.get(), both);
            s = new long[]{rampOrigin, first};
            STATE.put(id, s);
        }

        // subtle warning particles while the conditions are met — a light heat-haze, not a bonfire.
        if (now % 8 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY() + 1.1, target.getZ(), 1, 0.25, 0.4, 0.25, 0.0);
            if (target.getRandom().nextInt(3) == 0) {
                level.sendParticles(ParticleTypes.SMALL_FLAME, target.getX(), target.getY() + 0.6, target.getZ(), 1, 0.2, 0.3, 0.2, 0.0);
            }
        }

        if (now < s[1]) {
            return;
        }
        boolean hatted = !target.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        long interval = EnvBurn.interval(now - s[0], Config.BASEMENT_START_INTERVAL.get(),
                Config.BASEMENT_END_INTERVAL.get(), Config.BASEMENT_RAMP_TICKS.get(), both);
        if (hatted) {
            interval = Math.round(interval * Config.BASEMENT_HELMET_INTERVAL_MULT.get()); // a hat still buys time
        }
        s[1] = now + interval;

        double damage = Config.BASEMENT_DAMAGE.get();
        if (damage <= 0.0) {
            return;
        }
        // A quiet sizzle at the victim so the source of the damage is obvious, to them and anyone nearby.
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.GENERIC_BURN, SoundSource.PLAYERS, 0.4F, 1.6F + target.getRandom().nextFloat() * 0.2F);
        if (target.hurt(WitchModDamageTypes.sunburn(level), (float) damage)) {
            markDiscoveredByVictim(target);
        }
    }
}
