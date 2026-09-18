package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EntityBasedExplosionDamageCalculator;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.Curses;

/**
 * you come down like a dropped anvil. You fall faster, you take more for it, and past a
 * certain height you stop landing and start <i>impacting</i> — a real explosion at the landing site that
 * craters the ground, throws everything nearby and hurts you too.
 *
 * <p><b>The extra fall speed is the GRAVITY attribute, not a downward shove.</b> That keeps acceleration,
 * terminal velocity and the fall-distance bookkeeping entirely vanilla's — you're simply heavier, so the
 * bigger fall damage partly follows on its own before the multiplier is even applied. (Same approach Bad
 * Swimmer uses; note the attribute is hard-capped at 1.0 by the game.)
 *
 * <p><b>The crater scales with the drop, up to a cap.</b> The cap is the important half: without it a fall
 * from build height would level a base. Scaling is measured on fall DISTANCE rather than sampling impact
 * velocity on the exact landing tick — distance is monotonic in speed right up to terminal velocity, so it
 * gives the same felt result far more stably.
 *
 * <p><b>Trap inherited from Super Explosive:</b> the explosion's source ENTITY must be {@code null}.
 * {@code Explosion} gathers victims with {@code Level.getEntities(source, box)}, whose first argument is the
 * entity to <i>exclude</i> — naming the player there would quietly leave them out of their own crater, so
 * they'd take neither damage nor knockback from it. Attribution rides on the damage source instead. World
 * damage is gated on {@code mobGriefing} by using vanilla's own {@link Level.ExplosionInteraction#MOB}.
 */
public final class CurseHeavy extends Effect {
    private static final ResourceLocation GRAVITY_MODIFIER_ID = EffectUtil.modifierId("curse_heavy_gravity");
    private static final ResourceLocation JUMP_MODIFIER_ID = EffectUtil.modifierId("curse_heavy_jump");

    /** last tick's {@code fallDistance} per victim — the landing edge is where it drops back to zero. */
    private static final Map<UUID, Float> LAST_FALL_DISTANCE = new HashMap<>();
    /** game tick after which another crater is allowed — see the chaining note in {@link #watchForLanding}. */
    private static final Map<UUID, Long> NEXT_CRATER = new HashMap<>();

    public CurseHeavy() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 25, () -> Items.IRON_INGOT);
    }

    /** you find out the first time you crack the ground open (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        applyGravity(target);
        target.setData(WitchModAttachments.HEAVY_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeModifier(target, Attributes.GRAVITY, GRAVITY_MODIFIER_ID);
        EffectUtil.removeModifier(target, Attributes.JUMP_STRENGTH, JUMP_MODIFIER_ID);
        LAST_FALL_DISTANCE.remove(target.getUUID());
        NEXT_CRATER.remove(target.getUUID());
        target.setData(WitchModAttachments.HEAVY_ACTIVE, -1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        // self-heal: the modifier is TRANSIENT, so a world reload drops it while the curse itself persists,
        // and you'd quietly go back to falling normally. onApply never runs again, so it's re-applied here.
        applyGravity(target);
        // same self-heal reason: the synced flag drives the client-side water anchor.
        if (target.getData(WitchModAttachments.HEAVY_ACTIVE) < 0) {
            target.setData(WitchModAttachments.HEAVY_ACTIVE, 1);
        }
        watchForLanding(target);
    }

    /**
     * landing detection, done on the tick rather than from {@code LivingFallEvent}.
     *
     * <p><b>This is why cratering works in creative.</b> {@code Player.causeFallDamage} returns immediately
     * when {@code mayFly()} is true — so in creative the fall event never fires at all and a damage-driven
     * crater simply never happens. Watching vanilla's own {@code fallDistance} instead is mode-independent:
     * it accumulates while falling and is zeroed the moment you land. Flying resets it too (Player.travel
     * calls {@code resetFallDistance}), so a gentle creative descent doesn't crater — you have to actually
     * stop flying and drop.
     */
    private static void watchForLanding(ServerPlayer target) {
        float previous = LAST_FALL_DISTANCE.getOrDefault(target.getUUID(), 0.0F);
        float current = target.fallDistance;
        LAST_FALL_DISTANCE.put(target.getUUID(), current);

        if (previous <= 0.0F || current > 0.0F || !target.onGround()) {
            return;
        }
        // landed, having fallen `previous` blocks. (Landing in water leaves onGround false, so no crater —
        // which is the right call anyway.)
        // discovery is on the FIRST fall, not the first crater: you come down noticeably heavier straight
        // away, so waiting for a crater would only be telling the victim something they already know.
        Curses.DENSE.value().markDiscoveredByVictim(target);

        if (previous < Config.HEAVY_CRATER_MIN_FALL.get()) {
            return;
        }
        // chain guard: a crater blows the ground out from under you, so without this you fall into your own
        // hole, crater again, and keep excavating yourself downward on one drop.
        long now = target.level().getGameTime();
        Long allowed = NEXT_CRATER.get(target.getUUID());
        if (allowed != null && now < allowed) {
            return;
        }
        NEXT_CRATER.put(target.getUUID(), now + Config.HEAVY_CRATER_COOLDOWN.get());
        crater(target, previous);
    }

    private static void applyGravity(ServerPlayer target) {
        AttributeInstance gravity = target.getAttribute(Attributes.GRAVITY);
        if (gravity != null && !gravity.hasModifier(GRAVITY_MODIFIER_ID)) {
            gravity.addOrUpdateTransientModifier(new AttributeModifier(GRAVITY_MODIFIER_ID,
                    Config.HEAVY_FALL_SPEED_MULT.get() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        // heavier gravity cuts the jump apex to roughly v^2/(2g) — about 0.6 blocks at 1.8x, which is UNDER
        // the single block you need to get up a step. That turns the curse from a joke into a nuisance, so
        // the jump is compensated back to just over a block. It still feels leaden; you just aren't trapped.
        AttributeInstance jump = target.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null && !jump.hasModifier(JUMP_MODIFIER_ID)) {
            jump.addOrUpdateTransientModifier(new AttributeModifier(JUMP_MODIFIER_ID,
                    Config.HEAVY_JUMP_COMPENSATION.get() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /**
     * fall-damage hook only — see {@code CurseEventHandler}. The crater is deliberately NOT fired from here:
     * this event never runs in creative, so it lives in {@link #watchForLanding} instead.
     */
    public static float fallDamageMultiplier() {
        return Config.HEAVY_FALL_DAMAGE_MULT.get().floatValue();
    }

    private static void crater(ServerPlayer player, float fallDistance) {
        ServerLevel level = player.serverLevel();

        int min = Config.HEAVY_CRATER_MIN_FALL.get();
        int cap = Math.max(min + 1, Config.HEAVY_CRATER_CAP_FALL.get());
        // 0 at the minimum drop, 1 once the cap is reached — and clamped, which IS the cap.
        float scale = Mth.clamp((fallDistance - min) / (float) (cap - min), 0.0F, 1.0F);
        float power = Mth.lerp(scale,
                Config.HEAVY_CRATER_POWER_MIN.get().floatValue(),
                Config.HEAVY_CRATER_POWER_MAX.get().floatValue());

        impactParticles(level, player, scale);

        level.explode(
                // deliberately NULL — see the class javadoc. The player rides on the damage source instead.
                null,
                level.damageSources().explosion(null, player),
                new ImpactBlast(player),
                player.getX(), player.getY(), player.getZ(),
                power,
                false,
                Level.ExplosionInteraction.MOB); // MOB = vanilla's own mobGriefing gate

    }

    /** A spray of whatever they landed on, thrown outward — the crater's dust cloud. */
    private static void impactParticles(ServerLevel level, ServerPlayer player, float scale) {
        BlockPos below = player.blockPosition().below();
        BlockState landedOn = level.getBlockState(below);
        int count = 40 + Math.round(scale * 90.0F);

        if (!landedOn.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, landedOn),
                    player.getX(), player.getY() + 0.1, player.getZ(),
                    count, 0.6 + scale, 0.15, 0.6 + scale, 0.35 + scale * 0.4);
        }
        level.sendParticles(ParticleTypes.EXPLOSION,
                player.getX(), player.getY() + 0.2, player.getZ(),
                2 + Math.round(scale * 5.0F), 0.8 + scale, 0.1, 0.8 + scale, 0.0);
        level.sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.1, player.getZ(),
                20 + Math.round(scale * 40.0F), 0.5 + scale, 0.05, 0.5 + scale, 0.12);
    }

    /**
     * an ordinary explosion, except the faller takes only a share of it and everything gets thrown harder.
     * Extending {@link EntityBasedExplosionDamageCalculator} keeps vanilla's block-resistance behaviour for
     * a player-caused blast.
     */
    private static final class ImpactBlast extends EntityBasedExplosionDamageCalculator {
        private final Entity owner;

        private ImpactBlast(Entity owner) {
            super(owner);
            this.owner = owner;
        }

        @Override
        public float getEntityDamageAmount(Explosion explosion, Entity entity) {
            float damage = super.getEntityDamageAmount(explosion, entity);
            if (entity != owner) {
                return damage; // bystanders wear the whole thing
            }
            // they're at dead centre and are about to eat amplified fall damage on top of this.
            return damage * (Config.HEAVY_CRATER_SELF_DAMAGE_PERCENT.get() / 100.0F);
        }

        @Override
        public float getKnockbackMultiplier(Entity entity) {
            return Config.HEAVY_CRATER_KNOCKBACK_MULT.get().floatValue();
        }
    }
}
