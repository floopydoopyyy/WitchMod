package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
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
import com.oliver.witchmod.synergy.Synergies;

/**
 * build up a head of steam and nothing stands in your way.
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
    private static final Map<UUID, Long> LAST_SPRINT = new HashMap<>();
    private static final Map<UUID, Float> LAST_STEP = new HashMap<>();
    /** last non-trivial horizontal heading — from real position movement, so it survives a wall stop. */
    private static final Map<UUID, Vec3> LAST_HEADING = new HashMap<>();
    /** last tick position, to derive the heading from ACTUAL movement (server deltaMovement is unreliable for players). */
    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
    /** per-player: entity id -> last game-tick it was plough-hit (per-entity cleave cooldown). */
    private static final Map<UUID, Map<Integer, Long>> ENTITY_HITS = new HashMap<>();
    /** how far you travel between amplified footfalls, in blocks. */
    private static final float STEP_DISTANCE = 1.6F;

    public BlessingBrute() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 42, () -> Items.IRON_HELMET);
    }

    /** you find out when you first build up a real head of steam (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeModifier(target, Attributes.MOVEMENT_SPEED, SPEED_ID);
        CHARGE.remove(target.getUUID());
        LAST_SPRINT.remove(target.getUUID());
        LAST_HEADING.remove(target.getUUID());
        LAST_POS.remove(target.getUUID());
        ENTITY_HITS.remove(target.getUUID());
    }

    /** at/above the launch+smash threshold — used for the knockback immunity while barrelling. */
    public static boolean isCharged(ServerPlayer target) {
        int windup = Config.BRUTE_WINDUP_TIME.get();
        int rampTime = Config.BRUTE_RAMP_TIME.get();
        int charge = CHARGE.getOrDefault(target.getUUID(), 0);
        float progress = rampTime <= 0 ? (charge >= windup ? 1.0F : 0.0F)
                : Math.max(0.0F, Math.min(1.0F, (charge - windup) / (float) rampTime));
        return progress >= Config.BRUTE_CAP_THRESHOLD.get();
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        UUID id = target.getUUID();
        int windup = Config.BRUTE_WINDUP_TIME.get();
        int rampTime = Config.BRUTE_RAMP_TIME.get();
        int full = windup + rampTime;
        long now = target.serverLevel().getGameTime();

        // heading from ACTUAL position movement (server deltaMovement is unreliable for players); it persists
        // through a wall stop, which is the whole reason detection kept failing.
        Vec3 pos = target.position();
        Vec3 prevPos = LAST_POS.put(id, pos);
        if (prevPos != null) {
            Vec3 disp = new Vec3(pos.x - prevPos.x, 0.0, pos.z - prevPos.z);
            if (disp.lengthSqr() > 0.0025 * 0.0025) {
                LAST_HEADING.put(id, disp.normalize());
            }
        }

        // speed synergy: a speed blessing makes the whole charge come up quicker and land harder.
        boolean speedSynergy = Synergies.SPRINTING.activeFor(target);
        boolean sprinting = target.isSprinting();
        int charge = CHARGE.getOrDefault(id, 0);
        // build while sprinting; a brief drop (jump/turn) is forgiven by the grace, a sustained stop resets it.
        // hitting a wall does NOT keep it charged — the smash resets the charge below, so you must re-build (no tunnelling).
        if (sprinting) {
            LAST_SPRINT.put(id, now);
            charge = Math.min(charge + 1 + (speedSynergy ? Config.BRUTE_SPEED_CHARGE_BONUS.get() : 0), full);
        } else if (now - LAST_SPRINT.getOrDefault(id, Long.MIN_VALUE / 2) <= Config.BRUTE_SPRINT_GRACE_TICKS.get()) {
            charge = Math.min(charge, full); // hold through the brief drop
        } else {
            charge = 0;
        }
        CHARGE.put(id, charge);

        // tear through light nature the whole time you're moving under charge — no slowdown, no cost.
        boolean moving = target.getDeltaMovement().horizontalDistanceSqr() > 0.03 * 0.03;
        if (charge > 0 && moving) {
            tearLightBlocks(target);
        }
        // heavy, amplified footfalls the whole time you're building up — audible "something's coming".
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
            // cleave through a crowd: each body hit at most once per cooldown, no momentum bleed (you keep barrelling).
            if (launchEntities(target, now, speedSynergy) > 0) {
                impactFeedback(target);
            }
            // ran into a block: explode it, then RESET the charge — your momentum is spent, you must re-build
            // before the next smash. This is what stops continuous tunnelling. Only while GROUNDED, so JUMPING
            // up onto / landing on a block doesn't smash it — only running into one on the ground does.
            BlockPos wall = target.onGround() ? blockAhead(target) : null;
            if (wall != null) {
                smashWall(target, wall);
                CHARGE.put(id, 0);
                applySpeed(target, 0.0F);
                target.setDeltaMovement(target.getDeltaMovement().multiply(0.2, 1.0, 0.2)); // kill forward momentum
                target.hurtMarked = true;
            }
        }
    }

    /** sound, camera jolt and particles when you plough into an entity. */
    private static void impactFeedback(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        target.setData(WitchModAttachments.BRUTE_SHAKE_END, level.getGameTime() + Config.BRUTE_SHAKE_TICKS.get());
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1.0, target.getZ(),
                4, 0.4, 0.3, 0.4, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 1.0, target.getZ(),
                5, 0.3, 0.3, 0.3, 0.0);
        // the supplied heavy-impact crunch on the hit.
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

    /** brush leaves/snow/grass/plants/vines out of the space you're moving through, free and without slowing. */
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

    /** plough into bodies in your path — each hit at most once per cooldown, so you cleave a crowd cleanly. */
    private static int launchEntities(ServerPlayer target, long now, boolean speedSynergy) {
        ServerLevel level = target.serverLevel();
        Vec3 forward = horizontalHeading(target);
        double force = Config.BRUTE_LAUNCH_FORCE.get() * (speedSynergy ? Config.BRUTE_SPEED_KNOCKBACK_MULT.get() : 1.0);
        float damage = (float) (double) (Config.BRUTE_LAUNCH_DAMAGE.get()
                + (speedSynergy ? Config.BRUTE_SPEED_BONUS_DAMAGE.get() : 0.0));
        int cooldown = Config.BRUTE_ENTITY_HIT_COOLDOWN.get();
        Map<Integer, Long> hits = ENTITY_HITS.computeIfAbsent(target.getUUID(), k -> new HashMap<>());
        hits.values().removeIf(t -> now - t > cooldown * 4L); // keep the map small
        int launched = 0;
        AABB hitBox = target.getBoundingBox().inflate(0.4).expandTowards(forward.x, 0.0, forward.z);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                hitBox, e -> e != target && e.isAlive())) {
            if (now - hits.getOrDefault(victim.getId(), Long.MIN_VALUE / 2) < cooldown) {
                continue; // hit this one recently — skip so you cleave rather than pin it
            }
            hits.put(victim.getId(), now);
            Vec3 push = forward.scale(force).add(0.0, 0.55, 0.0);
            victim.setDeltaMovement(victim.getDeltaMovement().add(push));
            victim.hurtMarked = true;
            victim.hurt(level.damageSources().mobAttack(target), damage);
            launched++;
        }
        return launched;
    }

    /**
     * the nearest solid, non-nature block the player has run into — scans the player's own bounding box extended
     * FORWARD in their heading, so it catches any block in the way (leg-height to head, and slightly to the
     * side) rather than a fragile single ray. Null if the path is clear.
     */
    private static BlockPos blockAhead(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        Vec3 h = horizontalHeading(target);
        AABB probe = target.getBoundingBox().inflate(0.15, 0.0, 0.15).expandTowards(h.x * 0.7, 0.0, h.z * 0.7);
        Vec3 c = target.position();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos bp : BlockPos.betweenClosed(
                (int) Math.floor(probe.minX), (int) Math.floor(probe.minY), (int) Math.floor(probe.minZ),
                (int) Math.floor(probe.maxX), (int) Math.floor(probe.maxY), (int) Math.floor(probe.maxZ))) {
            BlockState state = level.getBlockState(bp);
            if (!state.blocksMotion() || isLightNature(state)) {
                continue;
            }
            double d = c.distanceToSqr(bp.getX() + 0.5, c.y, bp.getZ() + 0.5);
            if (d < bestDist) {
                bestDist = d;
                best = bp.immutable();
            }
        }
        return best;
    }

    /** blast the blocking block in a real explosion. You take only the small HP cost; others take the blast. */
    private static void smashWall(ServerPlayer target, BlockPos wall) {
        ServerLevel level = target.serverLevel();
        double hpCost = Config.BRUTE_SMASH_HP_COST.get();
        if (target.getHealth() > hpCost + 1.0) {
            target.setHealth((float) (target.getHealth() - hpCost));
        }
        // source=target EXCLUDES the brute from the blast; block-breaking follows blast resistance + mobGriefing.
        level.explode(target, wall.getX() + 0.5, wall.getY() + 0.5, wall.getZ() + 0.5,
                (float) (double) Config.BRUTE_SMASH_POWER.get(), Level.ExplosionInteraction.MOB);
        target.setData(WitchModAttachments.BRUTE_SHAKE_END, level.getGameTime() + Config.BRUTE_SHAKE_TICKS.get());
        level.playSound(null, target.blockPosition(), com.oliver.witchmod.data.WitchModSounds.BRUTE_IMPACT.get(),
                SoundSource.PLAYERS, 1.2F, 0.9F);
    }

    /** amplified footfalls while charging, so the wind-up is audible — the block's own step sound, but big. */
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

    /** horizontal heading: current movement, else the last one recorded while moving (survives a collision
     *  stop), else look direction. */
    private static Vec3 horizontalHeading(ServerPlayer target) {
        Vec3 move = target.getDeltaMovement();
        Vec3 flat = new Vec3(move.x, 0.0, move.z);
        if (flat.lengthSqr() > 1.0E-4) {
            return flat.normalize();
        }
        Vec3 last = LAST_HEADING.get(target.getUUID());
        if (last != null) {
            return last;
        }
        Vec3 look = target.getLookAngle();
        Vec3 lookFlat = new Vec3(look.x, 0.0, look.z);
        return lookFlat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : lookFlat.normalize();
    }
}
