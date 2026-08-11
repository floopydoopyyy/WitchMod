package com.oliver.witchmod.effects.curses;

import java.util.Arrays;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Something else decides where you're going (master-spec Backseat Driver). While you're riding anything, an
 * AI can seize the wheel: your control is cut dead and the mount makes for the stupidest thing in range —
 * lava, water, or the nearest big drop — or wanders if there's nothing dangerous to aim at.
 *
 * <p><b>The mount moves itself.</b> Nothing is shoved around with forced velocity (that slides and hovers).
 * A ridden mount takes its facing from the RIDER's yaw and its throttle from the rider's forward input, so
 * the hijack steers the RIDER (client-side, off the synced {@link WitchModAttachments#BACKSEAT_DRIVE_YAW})
 * and the animal walks there under its own movement code — real gait, gravity, step-up and collision. A
 * mount that ISN'T rider-steered (a pig without a carrot on a stick, say) is instead sent via its own
 * pathfinding, so it genuinely walks there too. Speed comes from a real Speed effect, not teleporting.
 *
 * <p><b>Chance ramps</b> the longer you ride without a takeover, clamped to a ceiling that jumps the moment
 * a hazard is spotted nearby. It <b>ends instantly on dismount</b>, but bailing early earns a SHORTER
 * cooldown than sitting through it. All numbers are config-exposed.
 */
public final class CurseBackseatDriver extends Effect {
    private static final int CHECK_INTERVAL = 20;    // roll for a takeover once a second while riding
    private static final int RETARGET_INTERVAL = 20; // how often the wander heading changes

    public CurseBackseatDriver() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 17, () -> Items.SADDLE);
    }

    /** You find out you have this the first time the AI actually takes the wheel (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.BACKSEAT_EPISODE_END, 0L);
    }

    @Override
    public String debugForce(ServerPlayer target, String arg) {
        net.minecraft.world.entity.Entity vehicle = target.getVehicle();
        if (vehicle == null) {
            return "mount a ridable first — nothing to take over";
        }
        startEpisode(target, vehicle, target.serverLevel().getGameTime());
        return "seized the wheel";
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        ServerLevel level = target.serverLevel();
        long now = level.getGameTime();
        Entity vehicle = target.getVehicle();
        boolean episodeActive = now < target.getData(WitchModAttachments.BACKSEAT_EPISODE_END);

        if (episodeActive) {
            if (vehicle == null) {
                // Bailed out — stops instantly, but the next one comes around sooner.
                endEpisode(target, now, Config.BACKSEAT_EARLY_EXIT_COOLDOWN_SECONDS.get());
            } else {
                steer(level, target, vehicle, now);
            }
            return;
        }

        if (vehicle == null) {
            // Not riding: hold the ramp at zero so the chance builds from mounting, not from idling.
            target.setData(WitchModAttachments.BACKSEAT_NEXT_ALLOWED, now);
            return;
        }
        if (now % CHECK_INTERVAL != 0) {
            return;
        }

        long nextAllowed = target.getData(WitchModAttachments.BACKSEAT_NEXT_ALLOWED);
        if (now < nextAllowed) {
            return; // still on cooldown
        }

        // Chance grows with time ridden, clamped to a ceiling that jumps when a hazard is in range.
        boolean hazardNearby = findHazard(level, vehicle) != null;
        int cap = hazardNearby ? Config.BACKSEAT_HAZARD_CHANCE_CAP_PERCENT.get() : Config.BACKSEAT_CHANCE_CAP_PERCENT.get();
        long secondsRidden = (now - nextAllowed) / 20L;
        int chance = (int) Math.min(cap, secondsRidden * Config.BACKSEAT_CHANCE_GROWTH_PER_SECOND.get());

        if (chance > 0 && target.getRandom().nextInt(100) < chance) {
            startEpisode(target, vehicle, now);
        }
    }

    private void startEpisode(ServerPlayer target, Entity vehicle, long now) {
        int episodeTicks = Config.BACKSEAT_EPISODE_SECONDS.get() * 20;
        target.setData(WitchModAttachments.BACKSEAT_EPISODE_END, now + episodeTicks);

        // Let it really bolt — a genuine Speed effect on the mount, which expires on its own.
        int boost = Config.BACKSEAT_SPEED_BOOST_LEVEL.get();
        if (boost > 0 && vehicle instanceof LivingEntity mount) {
            mount.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, episodeTicks, boost - 1, false, false));
        }
        markDiscoveredByVictim(target);
    }

    private static void endEpisode(ServerPlayer target, long now, int cooldownSeconds) {
        target.setData(WitchModAttachments.BACKSEAT_EPISODE_END, 0L);
        target.setData(WitchModAttachments.BACKSEAT_NEXT_ALLOWED, now + cooldownSeconds * 20L);
    }

    /**
     * Points the hijack at the nearest hazard (or a wandering heading) and lets the mount get itself there.
     * Publishes the heading for the client-side steering; additionally pathfinds any mount that isn't
     * rider-steered, since those ignore rider input entirely.
     */
    private static void steer(ServerLevel level, ServerPlayer rider, Entity vehicle, long now) {
        Vec3 destination = findHazard(level, vehicle);
        if (destination == null) {
            // No hazard in range — wander, changing heading periodically so it looks erratic, not scripted.
            double angle = (now / RETARGET_INTERVAL) * 2.399963; // golden-angle steps = non-repeating spread
            destination = vehicle.position().add(Math.cos(angle) * 10.0, 0.0, Math.sin(angle) * 10.0);
        }

        Vec3 toward = destination.subtract(vehicle.position());
        if (toward.horizontalDistanceSqr() < 1.0E-4) {
            return;
        }
        // A ridden mount faces wherever the rider faces — so publish that heading for the client hijack.
        float yaw = (float) Math.toDegrees(Math.atan2(-toward.x, toward.z));
        rider.setData(WitchModAttachments.BACKSEAT_DRIVE_YAW, yaw);

        // Mounts that aren't rider-steered (pig with no carrot on a stick, llamas, ...) never read rider
        // input, so send them with their OWN pathfinding instead — they walk there like they mean it.
        if (vehicle instanceof Mob mob && mob.getControllingPassenger() != rider) {
            mob.getNavigation().moveTo(destination.x, destination.y, destination.z, Config.BACKSEAT_NAV_SPEED_MULTIPLIER.get());
        }
    }

    /**
     * What the AI would love to drive into, in PRIORITY order — declaration order IS the priority, so the
     * scan always picks the best available type and only falls back to proximity within that type.
     */
    private enum Hazard {
        LIT_TNT,
        LEDGE,
        LAVA,
        HOSTILE,
        CACTUS,
        WATER
    }

    /**
     * Best "stupid action" target in range: the highest-priority hazard type present, nearest-first within
     * that type. Blocks are sampled on a coarse grid so it stays cheap enough to run while riding.
     */
    @Nullable
    private static Vec3 findHazard(ServerLevel level, Entity vehicle) {
        int radius = Config.BACKSEAT_HAZARD_SCAN_RADIUS.get();
        Vec3 from = vehicle.position();
        BlockPos origin = vehicle.blockPosition();
        HazardScan scan = new HazardScan(from);
        AABB box = vehicle.getBoundingBox().inflate(radius);

        // Entity hazards.
        for (PrimedTnt tnt : level.getEntitiesOfClass(PrimedTnt.class, box)) {
            scan.consider(Hazard.LIT_TNT, tnt.position());
        }
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m instanceof Enemy && m.isAlive())) {
            scan.consider(Hazard.HOSTILE, mob.position());
        }

        // Block hazards.
        for (int dx = -radius; dx <= radius; dx += 3) {
            for (int dz = -radius; dz <= radius; dz += 3) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                scanColumn(level, origin.offset(dx, 0, dz), origin.getY(), scan);
            }
        }
        return scan.best();
    }

    /** Records every block hazard in one sampled column: lava, water, cacti, or a nasty drop. */
    private static void scanColumn(ServerLevel level, BlockPos column, int riderY, HazardScan scan) {
        for (int dy = 1; dy >= -2; dy--) {
            BlockPos pos = column.atY(riderY + dy);
            if (level.getFluidState(pos).is(FluidTags.LAVA)) {
                scan.consider(Hazard.LAVA, Vec3.atCenterOf(pos));
            } else if (level.getFluidState(pos).is(FluidTags.WATER)) {
                scan.consider(Hazard.WATER, Vec3.atCenterOf(pos));
            }
            if (level.getBlockState(pos).is(Blocks.CACTUS)) {
                scan.consider(Hazard.CACTUS, Vec3.atCenterOf(pos));
            }
        }
        // Ledge: nothing solid for several blocks under this column means a drop worth running off.
        for (int dy = 0; dy >= -3; dy--) {
            if (!level.getBlockState(column.atY(riderY + dy)).isAir()) {
                return; // ground is close, not a ledge
            }
        }
        scan.consider(Hazard.LEDGE, Vec3.atCenterOf(column.atY(riderY)));
    }

    /** Keeps the nearest candidate per hazard type, then hands back the highest-priority one found. */
    private static final class HazardScan {
        private final Vec3 from;
        private final Vec3[] nearest = new Vec3[Hazard.values().length];
        private final double[] distance = new double[Hazard.values().length];

        HazardScan(Vec3 from) {
            this.from = from;
            Arrays.fill(distance, Double.MAX_VALUE);
        }

        void consider(Hazard type, Vec3 position) {
            double d = position.distanceToSqr(from);
            if (d < distance[type.ordinal()]) {
                distance[type.ordinal()] = d;
                nearest[type.ordinal()] = position;
            }
        }

        @Nullable
        Vec3 best() {
            for (Hazard hazard : Hazard.values()) { // enum order == priority
                if (nearest[hazard.ordinal()] != null) {
                    return nearest[hazard.ordinal()];
                }
            }
            return null;
        }
    }
}
