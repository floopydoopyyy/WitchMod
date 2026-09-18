package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.effects.Curses;

/**
 * you hit hard and you break like one. Every hit you take lands for
 * {@code TAKEN_MULT} (200%), and every MELEE hit you land deals {@code DEALT_MULT} (150%).
 *
 * <p><b>Both are done on the damage event, not via attributes</b> (the prototype used ATTACK_DAMAGE and ARMOR
 * modifiers, which only approximate it — armour reduction isn't a clean "×2 damage", and attack-damage
 * modifiers miss enchantment and crit contributions). Multiplying the actual damage amount is exact, applies
 * to <i>any</i> source on the way in, and lets the "melee only" restriction on dealt damage be precise: it's
 * applied only when the direct entity of the hit IS the glass-cannon player, so a shot arrow doesn't count.
 *
 * <p>Each direction gets a shatter of particles and a glassy sound for emphasis — the whole point is that
 * both hitting and being hit feel fragile and violent. The hooks live in {@code CurseEventHandler}.
 */
public final class CurseGlassCannon extends Effect {
    /**
     * per-player FX cooldown. The DAMAGE multiplier still applies to every instance — it must, or lava and
     * fire wouldn't hit at 200% — but the shatter sound and particles are throttled, so a fast-ticking source
     * like standing in lava crackles once rather than machine-gunning a wall of glass every tick.
     */
    private static final int FX_COOLDOWN_TICKS = 10;
    private static final Map<UUID, Long> NEXT_TAKEN_FX = new HashMap<>();
    private static final Map<UUID, Long> NEXT_DEALT_FX = new HashMap<>();

    public CurseGlassCannon() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 30, () -> Items.GLASS);
    }

    /** you find out the first time you shatter — hitting or being hit (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /** multiplies incoming damage and shatters glass around the victim. Returns the scaled amount. */
    public static float onDamageTaken(ServerPlayer victim, float amount) {
        if (fxReady(NEXT_TAKEN_FX, victim)) {
            ServerLevel level = victim.serverLevel();
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8F, 0.9F);
            level.sendParticles(ParticleTypes.CRIT,
                    victim.getX(), victim.getEyeY(), victim.getZ(), 14, 0.3, 0.3, 0.3, 0.15);
        }
        Curses.GLASS_CANNON.value().markDiscoveredByVictim(victim);
        return amount * (float) (double) Config.GLASS_CANNON_DAMAGE_TAKEN_MULT.get();
    }

    /** multiplies a melee hit and cracks the air at the target. Returns the scaled amount. */
    public static float onMeleeDealt(ServerPlayer attacker, Entity victim, float amount) {
        if (attacker.level() instanceof ServerLevel level && fxReady(NEXT_DEALT_FX, attacker)) {
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.GLASS_HIT, SoundSource.PLAYERS, 1.0F, 0.7F);
            level.sendParticles(ParticleTypes.CRIT,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(),
                    18, 0.2, 0.2, 0.2, 0.25);
        }
        Curses.GLASS_CANNON.value().markDiscoveredByVictim(attacker);
        return amount * (float) (double) Config.GLASS_CANNON_DAMAGE_DEALT_MULT.get();
    }

    private static boolean fxReady(Map<UUID, Long> schedule, ServerPlayer player) {
        long now = player.level().getGameTime();
        Long next = schedule.get(player.getUUID());
        if (next != null && now < next) {
            return false;
        }
        schedule.put(player.getUUID(), now + FX_COOLDOWN_TICKS);
        return true;
    }

    /** true for a direct melee blow FROM this attacker (a projectile's direct entity is the projectile). */
    public static boolean isMelee(Entity directEntity, LivingEntity attacker) {
        return directEntity == attacker;
    }
}
