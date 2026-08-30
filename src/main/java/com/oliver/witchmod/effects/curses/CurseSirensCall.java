package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * The sea is calling and staying dry aches (master-spec Siren's Call, NEW). A hidden <b>longing</b> stat
 * builds whenever the victim is out of water and drains — after a grace period — when they're back in it,
 * escalating through SIX stages:
 * <ol>
 *   <li><b>Unease</b> — faint bubbles and a rare drip; no penalty.</li>
 *   <li><b>Yearning</b> — Mining Fatigue I, and an occasional "you yearn for the water" cue.</li>
 *   <li><b>Restlessness</b> — Mining Fatigue II, more frequent cue, brief Nausea flickers.</li>
 *   <li><b>Heaviness</b> — Slowness I on land.</li>
 *   <li><b>The Sea's Grip</b> — Slowness II on land, and intermittent pull-bursts toward water.</li>
 *   <li><b>The March</b> — a continuous movement hijack to the nearest water, and a magenta mind-control
 *       shader over the screen.</li>
 * </ol>
 *
 * <p><b>Water grace.</b> Getting into water doesn't settle you instantly — you must stay under for
 * {@code WATER_GRACE} ticks before the longing starts to fall, and the magenta shader fades out across
 * exactly that window, so it's fully gone by the moment the meter begins to drop.
 *
 * <p><b>Drowned protect their own</b> ({@code SOOTHE_RADIUS}): one nearby slows the longing and is barred
 * from targeting the victim.
 *
 * <p>The march itself is applied client-side off the synced {@code SIREN_PULL_ACTIVE}/{@code SIREN_PULL_YAW}
 * pair (the Backseat Driver steer), the shader off {@code SIREN_SHADER}; movement is client-authoritative, so
 * a server nudge alone wouldn't hold. A small server-side velocity tug rides on top.
 */
public final class CurseSirensCall extends Effect {
    private static final Map<UUID, Float> LONGING = new HashMap<>();
    /** Consecutive ticks the victim has been in water — drives the grace period and the shader fade. */
    private static final Map<UUID, Integer> WATER_TICKS = new HashMap<>();
    /** The magenta shader's current strength, ramped/faded per tick. */
    private static final Map<UUID, Float> SHADER = new HashMap<>();
    /** Whether the yearning cue has fired this bout, so it lands on the stage-2 EDGE for discovery. */
    private static final Map<UUID, Boolean> ACHED = new HashMap<>();

    public CurseSirensCall() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 35, () -> Items.HEART_OF_THE_SEA);
    }

    /** You find out the first time the longing bites, not the moment it lands (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public String debugForce(ServerPlayer target, String arg) {
        LONGING.put(target.getUUID(), Config.SIREN_LONGING_MAX.get().floatValue());
        return "longing maxed — the sea is calling and the march begins";
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        UUID id = target.getUUID();
        LONGING.put(id, 0.0F);
        WATER_TICKS.put(id, 0);
        SHADER.put(id, 0.0F);
        ACHED.put(id, false);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        UUID id = target.getUUID();
        LONGING.remove(id);
        WATER_TICKS.remove(id);
        SHADER.remove(id);
        ACHED.remove(id);
        target.setData(WitchModAttachments.SIREN_PULL_ACTIVE, -1);
        target.setData(WitchModAttachments.SIREN_SHADER, 0.0F);
    }

    /** The Scrying Mirror gives editable FLAVOUR for the yearning level (lang keys, resolved on the client). */
    @Override
    public java.util.Optional<String> scryingDetail(ServerPlayer target) {
        Float longing = LONGING.get(target.getUUID());
        if (longing == null) {
            return java.util.Optional.empty();
        }
        double pct = longing / Config.SIREN_LONGING_MAX.get();
        String band = pct < 0.28 ? "calm" : pct < 0.6 ? "aching" : pct < 0.88 ? "pulling" : "marching";
        return java.util.Optional.of("@witchmod.scry.siren." + band);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        perTick(target);                                     // water grace, shader, velocity tug — every tick
        if (EffectUtil.every(ticksRemaining, Config.SIREN_CHECK_INTERVAL.get())) {
            doCheck(target);                                 // longing math + stage effects — on the interval
        }
    }

    /** Per-tick: track time in water, drive the magenta shader, and apply the velocity tug while marching. */
    private static void perTick(ServerPlayer target) {
        UUID id = target.getUUID();
        boolean inWater = target.isInWater();
        int waterTicks = inWater ? WATER_TICKS.merge(id, 1, Integer::sum) : 0;
        if (!inWater) {
            WATER_TICKS.put(id, 0);
        }

        float grace = Config.SIREN_WATER_GRACE.get();
        float longing = LONGING.getOrDefault(id, 0.0F);
        boolean marching = target.getData(WitchModAttachments.SIREN_PULL_ACTIVE) >= 0;

        float shader = SHADER.getOrDefault(id, 0.0F);
        if (inWater) {
            // Fade out across the grace window, hitting 0 right as the longing is about to start dropping.
            float remaining = grace <= 0 ? 0.0F : Math.max(0.0F, 1.0F - waterTicks / grace);
            shader = Math.min(shader, remaining);
        } else if (marching) {
            shader = Math.min(1.0F, shader + 0.08F); // ramp up as the sea takes the wheel
        } else {
            shader = Math.max(0.0F, shader - 0.05F);
        }
        SHADER.put(id, shader);
        target.setData(WitchModAttachments.SIREN_SHADER, shader);

        if (marching) {
            double yaw = Math.toRadians(target.getData(WitchModAttachments.SIREN_PULL_YAW) + 90.0F);
            double force = Config.SIREN_PULL_FORCE.get();
            target.setDeltaMovement(target.getDeltaMovement()
                    .add(Math.cos(yaw) * force, 0.0, Math.sin(yaw) * force));
            target.hurtMarked = true;
        }
    }

    /** Interval: move the longing meter and apply the current stage's symptoms. */
    private void doCheck(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        UUID id = target.getUUID();
        float max = Config.SIREN_LONGING_MAX.get().floatValue();
        float longing = LONGING.getOrDefault(id, 0.0F);
        boolean inWater = target.isInWater();

        boolean soothed = drownedNearby(target);
        forgiveDrowned(target, soothed);

        if (inWater) {
            // Only past the grace period does the water actually settle you.
            if (WATER_TICKS.getOrDefault(id, 0) >= Config.SIREN_WATER_GRACE.get()) {
                longing = Math.max(0.0F, longing - Config.SIREN_WATER_DRAIN.get().floatValue());
            }
        } else {
            float gain = Config.SIREN_DRY_GAIN.get().floatValue();
            if (soothed) {
                gain *= Config.SIREN_DROWNED_GAIN_MULT.get().floatValue();
            }
            longing = Math.min(max, longing + gain);
        }
        LONGING.put(id, longing);

        applyStages(target, level, longing, inWater);
    }

    private void applyStages(ServerPlayer target, ServerLevel level, float longing, boolean inWater) {
        int refresh = Config.SIREN_CHECK_INTERVAL.get() + 20;
        UUID id = target.getUUID();

        boolean s1 = longing >= Config.SIREN_STAGE1.get();
        boolean s2 = longing >= Config.SIREN_STAGE2.get();
        boolean s3 = longing >= Config.SIREN_STAGE3.get();
        boolean s4 = longing >= Config.SIREN_STAGE4.get();
        boolean s5 = longing >= Config.SIREN_STAGE5.get();
        boolean s6 = longing >= Config.SIREN_STAGE6.get();

        // Stage 1 — Unease: a few bubbles off you and, rarely, a drip. Purely atmospheric.
        if (s1 && !inWater) {
            level.sendParticles(ParticleTypes.BUBBLE_POP,
                    target.getX(), target.getEyeY(), target.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
            if (target.tickCount % 120 == 0) {
                level.playSound(null, target.blockPosition(), SoundEvents.AMBIENT_UNDERWATER_LOOP,
                        SoundSource.PLAYERS, 0.3F, 1.2F);
            }
        }

        // Stage 2 — Yearning: the ache begins. This is the discovery moment.
        if (s2) {
            int amp = s3 ? 1 : 0; // stage 3 deepens Mining Fatigue to II
            target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, refresh, amp, false, false, true));
            if (!ACHED.getOrDefault(id, false)) {
                ACHED.put(id, true);
                markDiscoveredByVictim(target);
            }
            if (!inWater) {
                int gap = s3 ? 30 : 60; // yearning cue gets more insistent at stage 3
                if (target.tickCount % gap == 0) {
                    target.displayClientMessage(Component.literal("You yearn for the water...")
                            .withStyle(ChatFormatting.AQUA), true);
                }
            }
        } else {
            ACHED.put(id, false); // dropped below the ache — next onset counts fresh
        }

        // Stage 3 — Restlessness: the longing starts muddling your senses.
        if (s3 && !inWater && target.tickCount % 120 == 0) {
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0, false, false, true));
        }

        // Stage 4/5 — Heaviness/Grip: Slowness on land, deepening at stage 5.
        if (s4 && !inWater) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, refresh, s5 ? 1 : 0, false, false, true));
        }

        // Stage 5 — intermittent pull-bursts; Stage 6 — continuous march. Both need water in range.
        boolean wantsPull = s6 || (s5 && (target.tickCount % 80) < 30); // grip = ~1.5s on, ~2.5s off
        if (wantsPull && !inWater) {
            BlockPos water = nearestWater(level, target);
            if (water != null) {
                Vec3 delta = Vec3.atCenterOf(water).subtract(target.position());
                float yaw = (float) (Mth.atan2(delta.z, delta.x) * (180.0 / Math.PI)) - 90.0F;
                target.setData(WitchModAttachments.SIREN_PULL_YAW, yaw);
                target.setData(WitchModAttachments.SIREN_PULL_ACTIVE, 1);
                return;
            }
        }
        target.setData(WitchModAttachments.SIREN_PULL_ACTIVE, -1);
    }

    @Nullable
    private static BlockPos nearestWater(ServerLevel level, ServerPlayer target) {
        int radius = Config.SIREN_WATER_SEARCH_RADIUS.get();
        BlockPos origin = target.blockPosition();
        BlockPos best = null;
        double bestHoriz = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                // ⚠ Skip water basically straight below/above us: with no horizontal bearing the march yaw
                // flips around and you spin on the spot — the "goofy circle on a platform over water" bug.
                double horiz = dx * dx + dz * dz;
                if (horiz < 4.0 || horiz >= bestHoriz) {
                    continue;
                }
                for (int dy = -radius / 2; dy <= radius / 2; dy += 2) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockPos above = pos.above();
                    // ⚠ Only water you can actually get INTO — the surface must be OPEN, not sealed under a
                    // solid block. This is what stops it dragging you toward water trapped beneath a platform.
                    if (level.getFluidState(pos).is(FluidTags.WATER)
                            && level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
                        bestHoriz = horiz;
                        best = pos;
                        break; // this column qualifies; no need to check deeper here
                    }
                }
            }
        }
        return best;
    }

    private static boolean drownedNearby(ServerPlayer target) {
        double radius = Config.SIREN_DROWNED_SOOTHE_RADIUS.get();
        if (radius <= 0.0) {
            return false;
        }
        return !target.serverLevel().getEntitiesOfClass(Drowned.class,
                target.getBoundingBox().inflate(radius), d -> d.isAlive()).isEmpty();
    }

    private static void forgiveDrowned(ServerPlayer target, boolean soothed) {
        if (!soothed) {
            return;
        }
        double radius = Config.SIREN_DROWNED_SOOTHE_RADIUS.get();
        for (Drowned drowned : target.serverLevel().getEntitiesOfClass(Drowned.class,
                target.getBoundingBox().inflate(radius))) {
            if (drowned.getTarget() == target) {
                drowned.setTarget(null);
            }
        }
    }
}
