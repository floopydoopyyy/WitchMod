package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModDamageTypes;

/**
 * the walls are too close. Being shut indoors, with no sky above you, wears at
 * you — the gentler mirror of Basement Dweller: less damage, no hat mitigation, and it bites whenever you
 * can't see the sky (night included, since the problem is the roof, not the sun).
 *
 * <p>Like Basement Dweller, the gap between bites RAMPS from a slow start ({@code claustrophobiaStart}) down
 * to a fast end over {@code claustrophobiaRampTicks}, tracked per-player from the moment you're boxed in (see
 * {@link EnvBurn}, which also folds in the combined-curse leeway). A subtle particle puff fires ONCE when you
 * first get boxed in — not constantly — and each bite adds a faint 'crushing' sound over the heartbeat.
 *
 * <p>Uses the mod's own {@code witchmod:cave_dread} damage type: fatal on every difficulty, bypasses armour,
 * no knockback. Discovered on the first bite.
 */
public final class CurseClaustrophobia extends Effect {
    /** victim -> {rampOrigin (grace end), nextBiteTick}. */
    private static final Map<UUID, long[]> STATE = new HashMap<>();

    public CurseClaustrophobia() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 30, () -> Items.DEEPSLATE);
    }

    /** you find out the first time the walls close in (Rule 2). */
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

        boolean boxedIn = !level.canSeeSky(target.blockPosition());
        if (!boxedIn) {
            STATE.remove(id); // open air (or the night sky) is fine — stepping back out resets the ramp
            return;
        }

        long now = level.getGameTime();
        boolean both = EnvBurn.bothActive(target);
        long[] s = STATE.get(id);
        if (s == null) {
            long rampOrigin = now + EnvBurn.graceTicks(both);
            long first = rampOrigin + EnvBurn.interval(0, Config.CLAUSTRO_START_INTERVAL.get(),
                    Config.CLAUSTRO_END_INTERVAL.get(), Config.CLAUSTRO_RAMP_TICKS.get(), both);
            s = new long[]{rampOrigin, first};
            STATE.put(id, s);
            // A one-off puff as the walls first press in — NOT a constant particle stream.
            level.sendParticles(ParticleTypes.ASH, target.getX(), target.getY() + 1.0, target.getZ(), 12, 0.4, 0.5, 0.4, 0.01);
            level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY() + 0.5, target.getZ(), 4, 0.3, 0.3, 0.3, 0.0);
        }

        if (now < s[1]) {
            return;
        }
        long interval = EnvBurn.interval(now - s[0], Config.CLAUSTRO_START_INTERVAL.get(),
                Config.CLAUSTRO_END_INTERVAL.get(), Config.CLAUSTRO_RAMP_TICKS.get(), both);
        s[1] = now + interval;

        double damage = Config.CLAUSTRO_DAMAGE.get();
        if (damage <= 0.0) {
            return;
        }
        // A faint warden heartbeat plus a subtle CRUSHING thud — a quiet zombie-on-a-door hit, which reads as
        // something heavy pressing/splintering in on you — a clear, oppressive cue where the dread comes from.
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.4F, 1.1F);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS, 0.35F, 0.6F + target.getRandom().nextFloat() * 0.1F);
        if (target.hurt(WitchModDamageTypes.caveDread(level), (float) damage)) {
            markDiscoveredByVictim(target);
        }
    }
}
