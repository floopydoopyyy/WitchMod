package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Build up a head of steam and nothing stands in your way (master-spec Brute, sacrificial item IRON HELMET).
 * Charge is built by CONSISTENT sprinting (any direction) — jumping or dropping out of sprint interrupts it:
 * <ul>
 *   <li>An initial quiet <b>windup</b> ({@code bruteWindupTicks}, ~1.5s) with no particles and no speed. Once
 *       completed it's BANKED — a jump then only resets the ramp, not this windup.</li>
 *   <li>Then a <b>speed ramp</b> ({@code bruteRampTimeTicks}) up to {@code bruteMaxSpeedMultiplier}, with
 *       particle feedback.</li>
 * </ul>
 * While charging you <b>tear through light nature</b> (leaves, top snow, grass, plants, vines) for free, no
 * slowdown. At/near top speed you <b>launch</b> entities you plough into and <b>smash through walls</b> up to
 * {@code bruteBlockHardnessMax} hardness — obsidian/bedrock stop you — at {@code bruteHpCostPerBlock} health
 * each, with a zombie-door-break crunch, camera jolt and debris. Custom ramp/smash sounds still pending.
 */
public final class BlessingBrute extends Effect {
    private static final ResourceLocation SPEED_ID = EffectUtil.modifierId("brute_charge_speed");

    private static final Map<UUID, Integer> CHARGE = new HashMap<>();
    private static final Map<UUID, Integer> AIRBORNE = new HashMap<>();
    private static final Map<UUID, Float> LAST_STEP = new HashMap<>();
    /** How far you travel between amplified footfalls, in blocks. */
    private static final float STEP_DISTANCE = 1.6F;

    public BlessingBrute() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 42, () -> Items.IRON_HELMET);
    }

    /** You find out when you first build up a real head of steam (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeModifier(target, Attributes.MOVEMENT_SPEED, SPEED_ID);
        CHARGE.remove(target.getUUID());
        AIRBORNE.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        UUID id = target.getUUID();
        int windup = Config.BRUTE_WINDUP_TIME.get();
        int rampTime = Config.BRUTE_RAMP_TIME.get();
        int full = windup + rampTime;

        int airborne = target.onGround() ? 0 : AIRBORNE.getOrDefault(id, 0) + 1;
        AIRBORNE.put(id, airborne);
        boolean sprinting = target.isSprinting();

        int charge = CHARGE.getOrDefault(id, 0);
        if (sprinting && airborne <= Config.BRUTE_AIRBORNE_GRACE.get()) {
            charge = Math.min(charge + 1, full);
        } else if (!sprinting) {
            charge = 0;                                   // stopped sprinting — full reset
        } else {
            charge = charge >= windup ? windup : 0;       // jumped — bank the windup if it was completed
        }
        CHARGE.put(id, charge);

        // Tear through light nature the whole time you're moving under charge — no slowdown, no cost.
        boolean moving = target.getDeltaMovement().horizontalDistanceSqr() > 0.03 * 0.03;
        if (charge > 0 && moving) {
            tearLightBlocks(target);
        }
        // Heavy, amplified footfalls the whole time you're building up — audible "something's coming".
        if (charge > 0) {
            stomp(target);
        }

        float progress = rampTime <= 0 ? (charge >= windup ? 1.0F : 0.0F)
                : Math.max(0.0F, Math.min(1.0F, (charge - windup) / (float) rampTime));
        applySpeed(target, progress);
        if (progress <= 0.0F) {
            return; // still in the quiet windup (or not charging) — no particles, no smashing
        }

        ServerLevel level = target.serverLevel();
        Vec3 c = target.position();
        level.sendParticles(ParticleTypes.CRIT, c.x, c.y + 0.4, c.z, Math.round(progress * 4) + 1,
                0.3, 0.2, 0.3, 0.02);

        if (progress >= Config.BRUTE_CAP_THRESHOLD.get()) {
            markDiscoveredByVictim(target);
            if (launchEntities(target) > 0) {
                // Ploughing into something bleeds your momentum — you slow down and must build back up (which
                // also stops you multi-hitting the same target every tick). But recovery is MUCH faster than a
                // full reset, so you keep barrelling through crowds rather than stalling on the first body.
                impactFeedback(target);
                int capCharge = windup + (int) Math.ceil(Config.BRUTE_CAP_THRESHOLD.get() * rampTime);
                int recovered = Math.max(0, capCharge - Config.BRUTE_ENTITY_RECOVERY_TICKS.get());
                CHARGE.put(id, recovered);
                float newProgress = rampTime <= 0 ? 0.0F
                        : Math.max(0.0F, Math.min(1.0F, (recovered - windup) / (float) rampTime));
                applySpeed(target, newProgress);
                return; // and no wall-smashing on the same tick you bounced off a body
            }
            smashBlocks(target);
        }
    }

    /** Sound, camera jolt and particles when you plough into an entity. */
    private static void impactFeedback(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        target.setData(WitchModAttachments.BRUTE_SHAKE_END, level.getGameTime() + Config.BRUTE_SHAKE_TICKS.get());
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1.0, target.getZ(),
                4, 0.4, 0.3, 0.4, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 1.0, target.getZ(),
                5, 0.3, 0.3, 0.3, 0.0);
        // The supplied heavy-impact crunch on the hit.
        level.playSound(null, target.blockPosition(), com.oliver.witchmod.data.WitchModSounds.BRUTE_IMPACT.get(),
                SoundSource.PLAYERS, 1.1F, 1.0F);
    }

    private static void applySpeed(ServerPlayer target, float progress) {
        EffectUtil.removeModifier(target, Attributes.MOVEMENT_SPEED, SPEED_ID);
        if (progress > 0.0F) {
            double mult = (Config.BRUTE_MAX_SPEED_MULT.get() - 1.0) * progress;
            EffectUtil.addModifier(target, Attributes.MOVEMENT_SPEED, SPEED_ID, mult,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
    }

    /** Brush leaves/snow/grass/plants/vines out of the space you're moving through, free and without slowing. */
    private static void tearLightBlocks(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        Vec3 forward = horizontalHeading(target);
        AABB box = target.getBoundingBox().inflate(0.15).expandTowards(forward.x * 0.5, 0.0, forward.z * 0.5);
        BlockPos.betweenClosedStream(box).forEach(bp -> {
            BlockPos pos = bp.immutable();
            BlockState state = level.getBlockState(pos);
            if (isLightNature(state)) {
                level.destroyBlock(pos, true, target);
            }
        });
    }

    private static boolean isLightNature(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        return state.is(BlockTags.LEAVES)
                || state.is(BlockTags.REPLACEABLE)   // short grass, ferns, dead bush, etc.
                || state.is(BlockTags.FLOWERS)
                || state.getBlock() instanceof BushBlock       // flowers, saplings, crops, berries, mushrooms
                || state.getBlock() instanceof SnowLayerBlock  // top snow
                || state.getBlock() instanceof VineBlock;
    }

    /** Anything you plough into at full charge is flung away and hurt. @return how many were launched. */
    private static int launchEntities(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        Vec3 forward = horizontalHeading(target);
        double force = Config.BRUTE_LAUNCH_FORCE.get();
        float damage = (float) (double) Config.BRUTE_LAUNCH_DAMAGE.get();
        int launched = 0;
        // Reach a bit ahead in your direction of travel so fast passes still catch bodies in your path.
        AABB hitBox = target.getBoundingBox().inflate(0.4).expandTowards(forward.x, 0.0, forward.z);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                hitBox, e -> e != target && e.isAlive())) {
            Vec3 push = forward.scale(force).add(0.0, 0.55, 0.0);
            victim.setDeltaMovement(victim.getDeltaMovement().add(push));
            victim.hurtMarked = true;
            victim.hurt(level.damageSources().mobAttack(target), damage);
            launched++;
        }
        return launched;
    }

    /** Smash a hole through the wall ahead at full charge, hardness-gated, for health each (capped per tick). */
    private static void smashBlocks(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return; // respects mobGriefing
        }
        Vec3 forward = horizontalHeading(target);
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 base = target.position().add(forward.scale(0.7));
        double hpCost = Config.BRUTE_HP_COST_PER_BLOCK.get();
        double maxHardness = Config.BRUTE_BLOCK_HARDNESS_MAX.get();
        int maxBlocks = Config.BRUTE_MAX_BLOCKS_PER_TICK.get();

        int broken = 0;
        BlockState anyBroken = null;
        // A 3-tall column ahead, then widen sideways — a proper hole rather than a single gap.
        outer:
        for (double dy : new double[] {0.2, 1.0, 1.8}) {
            for (double lateral : new double[] {0.0, 1.0, -1.0}) {
                if (broken >= maxBlocks || target.getHealth() <= hpCost + 1.0) {
                    break outer;
                }
                Vec3 p = base.add(right.scale(lateral)).add(0.0, dy, 0.0);
                BlockPos pos = BlockPos.containing(p.x, p.y, p.z);
                BlockState state = level.getBlockState(pos);
                float hardness = state.getDestroySpeed(level, pos);
                // hardness < 0 = unbreakable (bedrock); > max = too hard (obsidian). Both stop you.
                if (state.isAir() || hardness < 0.0F || hardness > maxHardness || !state.getFluidState().isEmpty()) {
                    continue;
                }
                level.destroyBlock(pos, true, target);
                target.setHealth((float) Math.max(1.0, target.getHealth() - hpCost));
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 18, 0.3, 0.3, 0.3, 0.15);
                broken++;
                anyBroken = state;
            }
        }

        if (broken > 0) {
            level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY() + 1.0, target.getZ(),
                    8, 0.4, 0.4, 0.4, 0.02);
            level.playSound(null, target.blockPosition(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,
                    SoundSource.PLAYERS, 0.8F, 0.9F);
            level.playSound(null, target.blockPosition(), com.oliver.witchmod.data.WitchModSounds.BRUTE_IMPACT.get(),
                    SoundSource.PLAYERS, 0.9F, 1.1F);
            // A slight camera jolt on the smash.
            target.setData(WitchModAttachments.BRUTE_SHAKE_END,
                    level.getGameTime() + Config.BRUTE_SHAKE_TICKS.get());
        }
    }

    /** Amplified footfalls while charging, so the wind-up is audible — the block's own step sound, but big. */
    private static void stomp(ServerPlayer target) {
        UUID id = target.getUUID();
        float now = target.walkDist;
        float last = LAST_STEP.getOrDefault(id, now);
        if (now < last) {
            last = now; // walkDist restarts on respawn/relog
        }
        if (!target.onGround() || target.isPassenger() || now - last < STEP_DISTANCE) {
            LAST_STEP.put(id, Math.min(now, last + STEP_DISTANCE));
            return;
        }
        LAST_STEP.put(id, now);
        ServerLevel level = target.serverLevel();
        BlockPos below = target.blockPosition().below();
        BlockState ground = level.getBlockState(below);
        if (ground.isAir()) {
            return;
        }
        net.minecraft.world.level.block.SoundType st = ground.getSoundType(level, below, target);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), st.getStepSound(), SoundSource.PLAYERS,
                Config.BRUTE_FOOTSTEP_VOLUME.get().floatValue(), st.getPitch() * 0.8F);
    }

    /** The player's horizontal movement heading (falls back to look direction when nearly still). */
    private static Vec3 horizontalHeading(ServerPlayer target) {
        Vec3 move = target.getDeltaMovement();
        Vec3 flat = new Vec3(move.x, 0.0, move.z);
        if (flat.lengthSqr() < 1.0E-4) {
            Vec3 look = target.getLookAngle();
            flat = new Vec3(look.x, 0.0, look.z);
        }
        return flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
    }
}
