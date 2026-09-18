package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import net.minecraft.resources.ResourceLocation;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * the protagonist moment: when you're
 * SURROUNDED — 3+ hostiles or 2+ players nearby — an intense drum theme kicks in and you power up. Double
 * that crowd (6+ hostiles / 4+ players) and it escalates a tier.
 *
 * <p>Buffs (with particle feedback), by tier:
 * <ul>
 *   <li>Faster attacks — {@code mainCharCooldownReductionT1/T2} (~30% / 45% shorter cooldown) via an
 *       {@code ATTACK_SPEED} modifier.</li>
 *   <li>Harder knockback dealt — {@code mainCharKnockbackT1/T2} (1.3x / 1.6x), applied in
 *       {@code BlessingEventHandler} by multiplying the knockback of anyone you hit.</li>
 *   <li>Speed I and Resistance I.</li>
 * </ul>
 *
 * <p><b>Stickiness:</b> once you stop meeting the conditions the buffs + music linger for
 * {@code mainCharStickyTicks} (4s), and any moment the conditions are met again the timer resets. The current
 * tier is published on the synced {@link WitchModAttachments#MAINCHAR_TIER} attachment — the client plays the
 * theme off it, and the knockback handler reads it. Custom drum theme supplied.
 */
public final class BlessingMainCharacter extends Effect {
    private static final ResourceLocation ATTACK_SPEED_ID = EffectUtil.modifierId("mainchar_attack_speed");
    private static final int BUFF_REFRESH_TICKS = 10;
    private static final int BUFF_DURATION_TICKS = 40;

    /** player -> game tick the sticky window ends. */
    private static final Map<UUID, Long> ACTIVE_UNTIL = new HashMap<>();
    /** target entity uuid -> the knockback multiplier queued by a Main Character's incoming hit this tick. */
    private static final Map<UUID, Float> PENDING_KB = new HashMap<>();

    public BlessingMainCharacter() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 35, () -> Items.FIREWORK_ROCKET);
    }

    /** you find out the first time the moment kicks in (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        deactivate(target, target.getData(WitchModAttachments.MAINCHAR_TIER));
        ACTIVE_UNTIL.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        ServerLevel level = target.serverLevel();
        long now = level.getGameTime();

        int raw = rawTier(target, level);
        int current = target.getData(WitchModAttachments.MAINCHAR_TIER);
        int effective;
        if (raw >= 1) {
            effective = raw;
            ACTIVE_UNTIL.put(target.getUUID(), now + Config.MAINCHAR_STICKY_TICKS.get());
        } else if (now < ACTIVE_UNTIL.getOrDefault(target.getUUID(), 0L)) {
            effective = current; // still in the sticky window — hold the last tier
        } else {
            effective = 0;
        }

        if (effective != current) {
            onTierChange(target, current, effective);
        }
        if (effective > 0) {
            if (now % BUFF_REFRESH_TICKS == 0) {
                EffectUtil.addTimedEffect(target, MobEffects.MOVEMENT_SPEED, BUFF_DURATION_TICKS, 0);
                EffectUtil.addTimedEffect(target, MobEffects.DAMAGE_RESISTANCE, BUFF_DURATION_TICKS, effective >= 3 ? 1 : 0);
                if (effective >= 3) {
                    // a whole horde on you: the protagonist powers up hard.
                    EffectUtil.addTimedEffect(target, MobEffects.DAMAGE_BOOST, BUFF_DURATION_TICKS, 0);
                    EffectUtil.addTimedEffect(target, MobEffects.REGENERATION, BUFF_DURATION_TICKS, 0);
                }
            }
            if (now % 4 == 0) {
                Vec3 c = target.position();
                level.sendParticles(ParticleTypes.FIREWORK, c.x, c.y + 1.0, c.z,
                        effective >= 2 ? 6 : 3, 0.5, 0.7, 0.5, 0.03);
                level.sendParticles(effective >= 2 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD,
                        c.x, c.y + 1.0, c.z, effective >= 2 ? 4 : 2, 0.5, 0.7, 0.5, 0.02);
            }
        }
    }

    /** count nearby hostiles/players and map to a tier: 0 none, 1 surrounded, 2 (double) really surrounded. */
    private static int rawTier(ServerPlayer target, ServerLevel level) {
        double r = Config.MAINCHAR_RADIUS.get();
        // only hostiles actually TARGETING you count — so it's a real fight, and the moment ends the instant the
        // crowd is dead or loses aggro (mobs lurking behind walls no longer keep it stuck on).
        int hostiles = level.getEntitiesOfClass(Monster.class, target.getBoundingBox().inflate(r),
                e -> e.isAlive() && e.getTarget() == target).size();
        int players = level.getPlayers(p -> p != target && p.isAlive() && !p.isSpectator()
                && p.distanceToSqr(target) <= r * r).size();

        int hT = Config.MAINCHAR_HOSTILE_THRESHOLD.get();
        int pT = Config.MAINCHAR_PLAYER_THRESHOLD.get();
        // a genuine HORDE aggroed onto you (any source, not just Popularity) is the top tier.
        if (hostiles >= Config.MAINCHAR_HORDE_THRESHOLD.get()) {
            return 3;
        }
        if (hostiles >= hT * 2 || players >= pT * 2) {
            return 2;
        }
        if (hostiles >= hT || players >= pT) {
            return 1;
        }
        return 0;
    }

    private static void onTierChange(ServerPlayer target, int from, int to) {
        target.setData(WitchModAttachments.MAINCHAR_TIER, to);

        // re-apply the attack-speed modifier for the new tier (remove first so the value is replaced cleanly).
        EffectUtil.removeModifier(target, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID);
        if (to > 0) {
            double reduction = to >= 3 ? Config.MAINCHAR_COOLDOWN_REDUCTION_T3.get()
                    : to >= 2 ? Config.MAINCHAR_COOLDOWN_REDUCTION_T2.get()
                    : Config.MAINCHAR_COOLDOWN_REDUCTION_T1.get();
            double speedMult = 1.0 / (1.0 - reduction) - 1.0; // e.g. 30% shorter cooldown -> +42.9% attack speed
            EffectUtil.addModifier(target, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, speedMult,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }

        if (to > 0 && from == 0) {
            // the moment kicks in.
            markDiscoveredByVictimStatic(target);
            ServerLevel level = target.serverLevel();
            Vec3 c = target.position();
            level.sendParticles(ParticleTypes.FIREWORK, c.x, c.y + 1.0, c.z, 30, 0.6, 0.8, 0.6, 0.08);
            level.playSound(null, target.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundSource.PLAYERS, 0.6F, 0.8F);
        } else if (to == 0 && from > 0) {
            deactivate(target, from);
        }
    }

    private static void deactivate(ServerPlayer target, int from) {
        target.setData(WitchModAttachments.MAINCHAR_TIER, 0);
        EffectUtil.removeModifier(target, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID);
        EffectUtil.removeTimedEffect(target, MobEffects.MOVEMENT_SPEED);
        EffectUtil.removeTimedEffect(target, MobEffects.DAMAGE_RESISTANCE);
        EffectUtil.removeTimedEffect(target, MobEffects.DAMAGE_BOOST);
        EffectUtil.removeTimedEffect(target, MobEffects.REGENERATION);
    }

    private static void markDiscoveredByVictimStatic(ServerPlayer target) {
        com.oliver.witchmod.effects.Blessings.MAIN_CHARACTER.get().markDiscoveredByVictim(target);
    }

    // --- Outgoing-knockback multiplier, driven from BlessingEventHandler's attack + knockback events. --------

    /** the knockback multiplier for a Main Character at the given tier (1.0 = none). */
    public static float knockbackMultiplier(int tier) {
        if (tier >= 3) {
            return Config.MAINCHAR_KNOCKBACK_T3.get().floatValue();
        }
        if (tier >= 2) {
            return Config.MAINCHAR_KNOCKBACK_T2.get().floatValue();
        }
        if (tier == 1) {
            return Config.MAINCHAR_KNOCKBACK_T1.get().floatValue();
        }
        return 1.0F;
    }

    /** queue that {@code target} is about to be knocked back harder because a Main Character just hit it. */
    public static void queueKnockback(LivingEntity target, float mult) {
        PENDING_KB.put(target.getUUID(), mult);
    }

    /** consume (and clear) any queued knockback multiplier for {@code target}; 1.0 if none. */
    public static float consumeKnockback(LivingEntity target) {
        Float mult = PENDING_KB.remove(target.getUUID());
        return mult == null ? 1.0F : mult;
    }
}
