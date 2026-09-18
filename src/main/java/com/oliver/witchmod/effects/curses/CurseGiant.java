package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModDamageTypes;
import com.oliver.witchmod.effects.Curses;

/**
 * GIANT. You swell to THREE TIMES your size and become a slow,
 * near-unkillable powerhouse: 10% slower, take 75% less damage, deal 80% more, but swing 30% slower.
 *
 * <p>Everything but the combat multipliers is a set of TRANSIENT attribute modifiers (re-applied each tick,
 * the same pattern as Dwarfism/Gluttony/Heavy — a reload drops transient modifiers while the curse persists):
 * <ul>
 *   <li>{@code SCALE} ×3 — model AND hitbox, so you genuinely tower and no longer fit through a one-block gap;</li>
 *   <li>{@code MOVEMENT_SPEED} ×0.9 — a lumbering 10% slower;</li>
 *   <li>{@code ATTACK_SPEED} ×0.7 — the 30% longer swing cooldown of a heavy giant;</li>
 *   <li>{@code ENTITY_INTERACTION_RANGE} ×2 — DOUBLE reach, so the towering player can still clobber things on
 *       the floor at its feet.</li>
 * </ul>
 * The ±damage (75% less taken / 80% more melee dealt) is done on the damage event in {@code CurseEventHandler},
 * exactly like Glass Cannon, so it's exact and applies to any source in / melee out.
 *
 * <p><b>STOMP:</b> anything the giant physically stands on is crushed — a per-tick sweep of its foot-slab deals
 * the custom {@code witchmod:stomp} damage (its own death message) with big knockback, launching them clear.
 */
public final class CurseGiant extends Effect {
    private static final ResourceLocation SCALE_ID = EffectUtil.modifierId("curse_giant_scale");
    private static final ResourceLocation SPEED_ID = EffectUtil.modifierId("curse_giant_speed");
    private static final ResourceLocation SWING_ID = EffectUtil.modifierId("curse_giant_swing");
    private static final ResourceLocation REACH_ID = EffectUtil.modifierId("curse_giant_reach");

    /** per-victim next-stomp tick, so a crushed entity isn't hit every single tick before it's launched clear. */
    private static final Map<UUID, Long> NEXT_STOMP = new HashMap<>();

    public CurseGiant() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 40, () -> Items.WHEAT_SEEDS);
    }

    /** you notice the instant you balloon in size (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        grow(target);
        markDiscoveredByVictim(target);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        // size-crisis synergy (with Dwarfism): hand the whole size/traits over to the oscillator.
        if (com.oliver.witchmod.synergy.Synergies.SIZE_CRISIS.activeFor(target)) {
            SizeCrisis.tick(target);
            return;
        }
        grow(target); // re-assert the transient modifiers (survives reload)...
        stomp(target); //... and crush anything underfoot
    }

    @Override
    public void onRemove(ServerPlayer target) {
        clearModifiers(target);
        SizeCrisis.clear(target); // stop the giant+dwarfism oscillation cleanly if it was running
    }

    /** strip the giant attribute modifiers (used by onRemove and the size-crisis synergy takeover). */
    public static void clearModifiers(ServerPlayer target) {
        EffectUtil.removeModifier(target, Attributes.SCALE, SCALE_ID);
        EffectUtil.removeModifier(target, Attributes.MOVEMENT_SPEED, SPEED_ID);
        EffectUtil.removeModifier(target, Attributes.ATTACK_SPEED, SWING_ID);
        EffectUtil.removeModifier(target, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID);
    }

    private static void grow(ServerPlayer target) {
        addMult(target, Attributes.SCALE, SCALE_ID, Config.GIANT_SCALE.get() - 1.0);
        addMult(target, Attributes.MOVEMENT_SPEED, SPEED_ID, Config.GIANT_SPEED_MULT.get() - 1.0);
        addMult(target, Attributes.ATTACK_SPEED, SWING_ID, Config.GIANT_ATTACK_SPEED_MULT.get() - 1.0);
        addMult(target, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, Config.GIANT_REACH_MULT.get() - 1.0);
    }

    private static void addMult(ServerPlayer target, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                ResourceLocation id, double amount) {
        AttributeInstance inst = target.getAttribute(attr);
        if (inst != null && !inst.hasModifier(id)) {
            inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** crushes any LivingEntity the giant TOUCHES — walking into them or standing on them: stomp damage + a launch. */
    static void stomp(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        long now = level.getGameTime();
        // anything overlapping the giant's huge body is being trampled — you don't have to stand ON them, just
        // walk INTO them (fair, since the giant is slow).
        var footprint = target.getBoundingBox().inflate(0.25);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, footprint,
                e -> e != target && e.isAlive() && !e.isSpectator())) {
            Long next = NEXT_STOMP.get(e.getUUID());
            if (next != null && now < next) {
                continue;
            }
            NEXT_STOMP.put(e.getUUID(), now + Config.GIANT_STOMP_INTERVAL_TICKS.get());
            e.hurt(WitchModDamageTypes.stomp(level, target), Config.GIANT_STOMP_DAMAGE.get().floatValue());
            // A big, mostly-outward launch so they're flung clear of underfoot.
            Vec3 away = e.position().subtract(target.getX(), target.getY(), target.getZ());
            away = away.horizontalDistanceSqr() < 1.0E-4 ? new Vec3(level.random.nextDouble() - 0.5, 0, level.random.nextDouble() - 0.5) : away;
            away = away.horizontalDistanceSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : new Vec3(away.x, 0, away.z).normalize();
            double kb = Config.GIANT_STOMP_KNOCKBACK.get();
            e.setDeltaMovement(away.x * kb, 0.5 * kb, away.z * kb);
            e.hurtMarked = true;
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 1.6F);
            level.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY(), e.getZ(), 3, 0.2, 0.05, 0.2, 0.0);
        }
        // housekeeping so the cooldown map doesn't grow forever.
        if (now % 200 == 0) {
            NEXT_STOMP.values().removeIf(t -> t < now);
        }
    }

    /** true for a direct melee blow FROM this attacker (a projectile's direct entity is the projectile). */
    public static boolean isMelee(Entity directEntity, LivingEntity attacker) {
        return directEntity == attacker;
    }

    public static float onDamageTaken(float amount) {
        return amount * (float) (double) Config.GIANT_DAMAGE_TAKEN_MULT.get();
    }

    public static float onMeleeDealt(float amount) {
        return amount * (float) (double) Config.GIANT_MELEE_DEALT_MULT.get();
    }

    /**
     * A giant's base melee hit lands HEAVY: extra knockback + a big burst of impact FX so it reads like being
     * clobbered by something enormous. Fired from the {@code AttackEntityEvent} in {@code CurseEventHandler}.
     */
    public static void onMeleeHit(ServerPlayer giant, net.minecraft.world.entity.Entity target) {
        if (!(giant.serverLevel() instanceof ServerLevel level) || !(target instanceof LivingEntity victim)) {
            return;
        }
        // big extra knockback away from the giant (vanilla's own knockback then stacks on top).
        victim.knockback(Config.GIANT_HIT_KNOCKBACK.get(), giant.getX() - victim.getX(), giant.getZ() - victim.getZ());
        victim.hurtMarked = true;
        double cx = victim.getX(), cy = victim.getY() + victim.getBbHeight() * 0.6, cz = victim.getZ();
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, cx, cy, cz, 4, 0.35, 0.25, 0.35, 0.0);
        level.sendParticles(ParticleTypes.CRIT, cx, cy, cz, 30, 0.4, 0.35, 0.4, 0.35);
        level.sendParticles(ParticleTypes.EXPLOSION, cx, cy, cz, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, cx, cy, cz, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.4F, 0.55F);
        level.playSound(null, cx, cy, cz, com.oliver.witchmod.data.WitchModSounds.BIG_HIT.get(), SoundSource.PLAYERS, 1.0F, 0.9F);
    }
}
