package com.oliver.witchmod.effects.curses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModSounds;
import com.oliver.witchmod.effects.Curses;

/**
 * It happens. Loudly, and with enough force to move you (master-spec Gassy). Every so often you go off and
 * are launched in some direction at a randomised velocity — and the direction is BIASED toward whatever
 * nearby would make it worse.
 *
 * <p><b>The bias is weighted, never a guarantee.</b> Backseat Driver's hazard scan uses strict priority
 * (nastiest type always wins) because an AI deliberately steering into danger should be reliable about it.
 * This one must not be: a fixed {@code RANDOM_DIRECTION_CHANCE} of farts ignore hazards entirely, and even
 * when a hazard is chosen it's a weighted draw across everything found rather than "nearest lava, always".
 * Otherwise standing anywhere near lava would be a death sentence on a timer rather than a running joke.
 *
 * <p>Hazards recognised: lava, fire and soul fire, magma, lit campfires, cacti, sweet berry bushes, wither
 * roses, pointed dripstone, powder snow, lit TNT (an ENTITY, so it needs its own lookup), and ledges — a
 * column with a real drop under it.
 *
 * <p><b>Events force one out of you.</b> Any nearby explosion (via {@code ExplosionEvent.Detonate}, so TNT,
 * creepers, beds, end crystals and the rest all count), a firework going off nearby, or simply taking a hit.
 * Those roll a much higher big-fart chance and carry an extra velocity multiplier, and share one cooldown so
 * a firework show doesn't punt you across the map forty times.
 */
public final class CurseGassy extends Effect {
    /** A faint green tinge through the white. Deliberately far weaker than Unhygienic's stink cloud. */
    private static final DustParticleOptions GREEN_TINGE =
            new DustParticleOptions(new Vector3f(0.42F, 0.78F, 0.24F), 1.2F);
    private static final int WHITE_PUFF = 14;
    private static final int WHITE_PUFF_BIG = 30;
    private static final int GREEN_TINGE_COUNT = 3;
    private static final int GREEN_TINGE_BIG = 6;

    private static final Map<UUID, Long> NEXT_FART = new HashMap<>();
    private static final Map<UUID, Long> EVENT_COOLDOWN = new HashMap<>();
    /** Firework ids seen near each victim last tick, so a vanished one can be read as a detonation. */
    private static final Map<UUID, Set<Integer>> NEARBY_FIREWORKS = new HashMap<>();

    /** How much each hazard pulls the draw toward itself. Relative, not absolute — nothing here is certain. */
    private enum Hazard {
        LAVA(10.0),
        LIT_TNT(9.0),
        LEDGE(7.0),
        FIRE(6.0),
        MAGMA(5.0),
        DRIPSTONE(4.0),
        CACTUS(4.0),
        POWDER_SNOW(3.0),
        BERRY_BUSH(2.0),
        WITHER_ROSE(2.0);

        private final double weight;

        Hazard(double weight) {
            this.weight = weight;
        }
    }

    private record Candidate(Hazard type, Vec3 position) {}

    public CurseGassy() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.PUFFERFISH);
    }

    /** You find out the first time you leave the ground under your own power. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        schedule(target);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        NEXT_FART.remove(target.getUUID());
        EVENT_COOLDOWN.remove(target.getUUID());
        NEARBY_FIREWORKS.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        long now = target.level().getGameTime();
        trackFireworks(target, now);

        Long due = NEXT_FART.get(target.getUUID());
        if (due == null) {
            // Self-heal: the schedule is transient, so a curse that survived a relog would otherwise go
            // permanently quiet (the same trap Yap, Gluttony and Delusions all hit).
            schedule(target);
            return;
        }
        if (now < due) {
            return;
        }
        schedule(target);
        fart(target, target.getRandom().nextInt(100) < Config.GASSY_BIG_CHANCE.get(), 1.0);
    }

    // --- Triggers --------------------------------------------------------------------------------------

    /** A hit frightens one out of you; an explosion much more so. Called from {@code CurseEventHandler}. */
    public static void onExternalTrigger(ServerPlayer target, int chancePercent) {
        long now = target.level().getGameTime();
        Long until = EVENT_COOLDOWN.get(target.getUUID());
        if (until != null && now < until) {
            return;
        }
        if (target.getRandom().nextInt(100) >= chancePercent) {
            return;
        }
        EVENT_COOLDOWN.put(target.getUUID(), now + Config.GASSY_EVENT_COOLDOWN.get());
        boolean big = target.getRandom().nextInt(100) < Config.GASSY_EVENT_BIG_CHANCE.get();
        Curses.GASSY.get().fart(target, big, Config.GASSY_EVENT_VELOCITY_MULT.get());
        // The schedule resets too — you just went, so the next spontaneous one starts its clock over.
        schedule(target);
    }

    /**
     * Fireworks don't produce an {@code Explosion}, so there's no explosion event to listen for. Instead the
     * ones near the victim are remembered each tick, and any that has vanished by the next tick is treated as
     * having gone off — a firework that close disappearing for any other reason is vanishingly unlikely.
     */
    private static void trackFireworks(ServerPlayer target, long now) {
        double radius = Config.GASSY_EXPLOSION_HEAR_RADIUS.get();
        AABB box = target.getBoundingBox().inflate(radius);
        Set<Integer> current = new HashSet<>();
        for (FireworkRocketEntity rocket : target.serverLevel().getEntitiesOfClass(FireworkRocketEntity.class, box)) {
            current.add(rocket.getId());
        }
        Set<Integer> previous = NEARBY_FIREWORKS.put(target.getUUID(), current);
        if (previous == null) {
            return;
        }
        for (int id : previous) {
            if (!current.contains(id)) {
                onExternalTrigger(target, Config.GASSY_ON_EXPLOSION_CHANCE.get());
                return;
            }
        }
    }

    private static void schedule(ServerPlayer target) {
        int min = Config.GASSY_INTERVAL_MIN.get();
        int max = Math.max(min, Config.GASSY_INTERVAL_MAX.get());
        long delay = min + target.getRandom().nextInt(max - min + 1);
        NEXT_FART.put(target.getUUID(), target.level().getGameTime() + delay);
    }

    // --- The main event --------------------------------------------------------------------------------

    private void fart(ServerPlayer target, boolean big, double extraVelocity) {
        ServerLevel level = target.serverLevel();
        RandomSource rnd = target.getRandom();

        double min = Config.GASSY_VELOCITY_MIN.get();
        double max = Math.max(min, Config.GASSY_VELOCITY_MAX.get());
        double power = (min + rnd.nextDouble() * (max - min)) * extraVelocity;
        if (big) {
            power *= Config.GASSY_BIG_MULTIPLIER.get();
        }

        Vec3 launch = pickLaunch(level, target, rnd).scale(power);
        target.setDeltaMovement(target.getDeltaMovement().add(launch));
        // Without this the server never sends the velocity down and the victim doesn't budge on their screen.
        target.hurtMarked = true;
        target.hasImpulse = true;

        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                big ? WitchModSounds.GASSY_FART_LARGE.get() : WitchModSounds.GASSY_FART_SMALL.get(),
                SoundSource.PLAYERS, big ? 1.2F : 0.9F, 0.9F + rnd.nextFloat() * 0.2F);

        // A white puff of gas with just a hint of green in it. CLOUD carries the body of it; the green is
        // DustParticleOptions, since vanilla has no tintable smoke — and it's kept sparse on purpose, so it
        // reads as a tinge rather than as Unhygienic's full stink cloud.
        level.sendParticles(ParticleTypes.CLOUD,
                target.getX(), target.getY() + 0.4, target.getZ(),
                big ? WHITE_PUFF_BIG : WHITE_PUFF, 0.18, 0.12, 0.18, 0.02);
        level.sendParticles(GREEN_TINGE,
                target.getX(), target.getY() + 0.4, target.getZ(),
                big ? GREEN_TINGE_BIG : GREEN_TINGE_COUNT, 0.2, 0.14, 0.2, 0.01);

        // The propellant, aimed the opposite way to the launch so it reads as thrust. Count 0 makes the
        // offsets a VELOCITY instead, which is what gives it the directional jet.
        Vec3 exhaust = launch.normalize().scale(-0.35);
        level.sendParticles(ParticleTypes.CLOUD,
                target.getX() + exhaust.x * 0.5, target.getY() + 0.35 + exhaust.y * 0.5, target.getZ() + exhaust.z * 0.5,
                0, exhaust.x, exhaust.y, exhaust.z, big ? 0.6 : 0.35);

        markDiscoveredByVictim(target);
    }

    /** A unit vector to be launched along: sometimes straight up, sometimes at a hazard, sometimes anywhere. */
    private static Vec3 pickLaunch(ServerLevel level, ServerPlayer target, RandomSource rnd) {
        if (rnd.nextInt(100) < Config.GASSY_STRAIGHT_UP_CHANCE.get()) {
            // Mostly up, with just enough lean that it isn't a clean elevator ride.
            return new Vec3((rnd.nextDouble() - 0.5) * 0.35, 1.0, (rnd.nextDouble() - 0.5) * 0.35).normalize();
        }

        Vec3 horizontal = null;
        if (rnd.nextInt(100) >= Config.GASSY_RANDOM_DIRECTION_CHANCE.get()) {
            Vec3 hazard = pickHazard(level, target, rnd);
            if (hazard != null) {
                Vec3 toward = hazard.subtract(target.position());
                if (toward.horizontalDistanceSqr() > 1.0E-4) {
                    horizontal = new Vec3(toward.x, 0.0, toward.z).normalize();
                }
            }
        }
        if (horizontal == null) {
            double angle = rnd.nextDouble() * Math.PI * 2.0;
            horizontal = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
        }

        double vMin = Config.GASSY_VERTICAL_MIN.get();
        double vMax = Math.max(vMin, Config.GASSY_VERTICAL_MAX.get());
        double up = vMin + rnd.nextDouble() * (vMax - vMin);
        return new Vec3(horizontal.x, up, horizontal.z).normalize();
    }

    /**
     * A weighted draw over everything unpleasant in range. Weight is the hazard's own nastiness divided by
     * its distance, so near and nasty wins MORE OFTEN — but a far-off cliff can still beat adjacent lava,
     * which is exactly the unpredictability the spec asks for.
     */
    @Nullable
    private static Vec3 pickHazard(ServerLevel level, ServerPlayer target, RandomSource rnd) {
        List<Candidate> found = scanHazards(level, target);
        if (found.isEmpty()) {
            return null;
        }
        double total = 0.0;
        double[] weights = new double[found.size()];
        for (int i = 0; i < found.size(); i++) {
            Candidate candidate = found.get(i);
            double distance = Math.max(1.0, candidate.position().distanceTo(target.position()));
            weights[i] = candidate.type().weight / distance;
            total += weights[i];
        }
        double roll = rnd.nextDouble() * total;
        for (int i = 0; i < found.size(); i++) {
            roll -= weights[i];
            if (roll <= 0.0) {
                return found.get(i).position();
            }
        }
        return found.get(found.size() - 1).position();
    }

    private static List<Candidate> scanHazards(ServerLevel level, ServerPlayer target) {
        int radius = Config.GASSY_HAZARD_SCAN_RADIUS.get();
        int ledgeDrop = Config.GASSY_LEDGE_DROP_MIN.get();
        BlockPos origin = target.blockPosition();
        List<Candidate> found = new ArrayList<>();

        // Lit TNT is an ENTITY, not a block — a block scan alone silently misses the best hazard going.
        for (PrimedTnt tnt : level.getEntitiesOfClass(PrimedTnt.class, target.getBoundingBox().inflate(radius))) {
            found.add(new Candidate(Hazard.LIT_TNT, tnt.position()));
        }

        // Sampled on a coarse grid — this only runs when a fart actually fires, so it can afford to be wide.
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos column = origin.offset(dx, 0, dz);
                boolean solidFound = false;
                for (int dy = 2; dy >= -3; dy--) {
                    BlockPos pos = column.atY(origin.getY() + dy);
                    Hazard type = classify(level, pos);
                    if (type != null) {
                        found.add(new Candidate(type, Vec3.atCenterOf(pos)));
                    }
                    if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                        solidFound = true;
                    }
                }
                if (!solidFound && isLedge(level, column, origin.getY(), ledgeDrop)) {
                    found.add(new Candidate(Hazard.LEDGE, Vec3.atCenterOf(column.atY(origin.getY()))));
                }
            }
        }
        return found;
    }

    @Nullable
    private static Hazard classify(ServerLevel level, BlockPos pos) {
        if (level.getFluidState(pos).is(FluidTags.LAVA)) {
            return Hazard.LAVA;
        }
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
            return Hazard.FIRE;
        }
        if (state.is(Blocks.MAGMA_BLOCK)) {
            return Hazard.MAGMA;
        }
        if ((state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) && state.getValue(CampfireBlock.LIT)) {
            return Hazard.FIRE;
        }
        if (state.is(Blocks.CACTUS)) {
            return Hazard.CACTUS;
        }
        if (state.is(Blocks.POINTED_DRIPSTONE)) {
            return Hazard.DRIPSTONE;
        }
        if (state.is(Blocks.POWDER_SNOW)) {
            return Hazard.POWDER_SNOW;
        }
        if (state.is(Blocks.SWEET_BERRY_BUSH)) {
            return Hazard.BERRY_BUSH;
        }
        if (state.is(Blocks.WITHER_ROSE)) {
            return Hazard.WITHER_ROSE;
        }
        return null;
    }

    /** Nothing solid for a good few blocks under this column — worth being launched off. */
    private static boolean isLedge(ServerLevel level, BlockPos column, int fromY, int minDrop) {
        for (int dy = 0; dy < minDrop; dy++) {
            BlockPos pos = column.atY(fromY - dy);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
