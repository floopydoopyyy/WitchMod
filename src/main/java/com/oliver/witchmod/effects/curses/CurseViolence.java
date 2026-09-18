package com.oliver.witchmod.effects.curses;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * sudden, unprovoked violent urges. Your hand goes for whoever is nearby on its own,
 * and the hit is <b>vanilla's own {@link Player#attack}</b> — enchantments, knockback, crits, sweeping and
 * sounds all genuinely applied with the held item, not imitated.
 *
 * <p><b>Two different urges, deliberately behaving differently:</b>
 * <ul>
 *   <li><b>Sight swings</b> — whatever you are looking straight at (within a cone, and in line of sight) is
 *       in serious danger, and far more so if a shove would drop it off a ledge or into lava. These take
 *       INSTANT priority and never ramp: staring at something in reach is simply dangerous. No camera
 *       hijack, because you were already looking.</li>
 *   <li><b>Impulsive swings</b> — the camera IS hijacked onto something you weren't looking at. Possible at
 *       any moment, and the chance ramps the longer you have gone without a swing. Anything that loiters at
 *       a ledge or hazard for {@code violenceHazardSustainTicks} overrides that ramp outright.</li>
 * </ul>
 *
 * <p><b>Target priority is tiered, not just weighted</b>, because the spec demands guarantees that
 * multiplying scores together can't deliver — a wounded player has to outrank everything, and a wounded mob
 * has to outrank a healthy player. See {@link #priorityTier}. Within a tier it's nearest-first, nudged by
 * how badly the shove would end.
 *
 * <p>A stolen swing costs you the attack cooldown exactly like a real one ({@link Player#attack} resets that
 * counter itself), so it takes your next hit's damage with it rather than being a free extra attack.
 */
public final class CurseViolence extends Effect {
    /**
     * vanilla's attack-charge counter. {@code protected} in LivingEntity, so reflection is the only way to
     * force a swing to full strength without adding an access transformer to the build. Resolved once; if it
     * ever fails the curse still works, swings just land at whatever charge you happened to have.
     */
    @Nullable
    private static final Field ATTACK_STRENGTH_TICKER = resolveAttackStrengthTicker();

    /** in-progress flurries: player -> {extra swings left, ticks until the next one}. Transient by design. */
    private static final Map<UUID, int[]> FLURRIES = new HashMap<>();

    /** how long each entity has loitered somewhere dangerous: entity id -> {first seen, last seen}. */
    private static final Map<Integer, long[]> HAZARD_PERCHES = new HashMap<>();

    private static final int HAZARD_TRACK_INTERVAL = 5;
    private static final int HAZARD_PERCH_FORGET_TICKS = 40;
    private static final double TNT_DANGER_RADIUS = 3.0;

    public CurseViolence() {
        super(EffectCategory.CURSE, EffectCostTier.MODERATE, 45, () -> Items.IRON_SWORD);
    }

    /** you find out the first time your own arm swings without you (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public String debugForce(ServerPlayer target, String arg) {
        LivingEntity victim = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : target.serverLevel().getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(6.0), e -> e != target && e.isAlive())) {
            double d = e.distanceToSqr(target);
            if (d < best) {
                best = d;
                victim = e;
            }
        }
        if (victim == null) {
            return "nothing in range to swing at";
        }
        swingAt(target, victim, true);
        return "forced a swing at " + victim.getName().getString();
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.VIOLENCE_LAST_SWING, target.serverLevel().getGameTime());
    }

    @Override
    public void onRemove(ServerPlayer target) {
        FLURRIES.remove(target.getUUID());
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        long now = target.serverLevel().getGameTime();

        if (now % HAZARD_TRACK_INTERVAL == 0) {
            trackHazardPerches(target, now);
        }
        if (continueFlurry(target)) {
            return;
        }
        if (!EffectUtil.every(ticksRemaining, Config.VIOLENCE_CHECK_INTERVAL_TICKS.get())) {
            return;
        }

        // 1. Sight swings take instant priority — no ramp, no camera hijack.
        LivingEntity looked = entityInSight(target);
        if (looked != null) {
            int chance = shoveEndsBadly(target, looked)
                    ? Config.VIOLENCE_SIGHT_HAZARD_CHANCE_PERCENT.get()
                    : Config.VIOLENCE_SIGHT_CHANCE_PERCENT.get();
            if (target.getRandom().nextInt(100) < chance) {
                swingAt(target, looked, false);
                return;
            }
        }

        // 2. Otherwise an impulsive urge, which ramps and hijacks the camera.
        LivingEntity victim = pickVictim(target);
        if (victim == null) {
            return;
        }
        if (target.getRandom().nextInt(100) < impulsiveChance(target, now)) {
            swingAt(target, victim, true);
        }
    }

    /**
     * the impulsive chance: grows with every second since the last swing, up to a ceiling — unless something
     * has been loitering somewhere dangerous, which overrides the ramp entirely.
     */
    private static int impulsiveChance(ServerPlayer player, long now) {
        if (hasSustainedHazardPerch(player)) {
            return Config.VIOLENCE_IMPULSIVE_HAZARD_CHANCE_PERCENT.get();
        }
        double seconds = Math.max(0.0, (now - player.getData(WitchModAttachments.VIOLENCE_LAST_SWING)) / 20.0);
        double chance = Config.VIOLENCE_IMPULSIVE_BASE_CHANCE_PERCENT.get()
                + Config.VIOLENCE_IMPULSIVE_RAMP_PER_SECOND.get() * seconds;
        // frenzy synergy (with Berserker): the arm goes off far more often, feeding free berserker stacks.
        double cap = Config.VIOLENCE_IMPULSIVE_CAP_PERCENT.get();
        if (com.oliver.witchmod.synergy.Synergies.FRENZY.activeFor(player)) {
            double mult = Config.VIOLENCE_FRENZY_CHANCE_MULT.get();
            chance *= mult;
            cap = Math.min(100.0, cap * mult);
        }
        return (int) Math.min(cap, chance);
    }

    /** @return true if a flurry is mid-swing this tick, which suppresses the normal rolls. */
    private boolean continueFlurry(ServerPlayer target) {
        int[] flurry = FLURRIES.get(target.getUUID());
        if (flurry == null) {
            return false;
        }
        if (--flurry[1] > 0) {
            return true;
        }

        LivingEntity victim = pickVictim(target);
        if (victim != null) {
            swingAt(target, victim, !isLookingAt(target, victim));
        } else {
            target.swing(InteractionHand.MAIN_HAND, true); // nothing left to hit — flail at the air anyway
        }

        if (--flurry[0] <= 0) {
            FLURRIES.remove(target.getUUID());
        } else {
            flurry[1] = Config.VIOLENCE_MULTI_HIT_SPACING_TICKS.get();
        }
        return true;
    }

    /** swings for real, optionally dragging the camera onto the target first. */
    private void swingAt(ServerPlayer player, LivingEntity victim, boolean hijackCamera) {
        if (hijackCamera) {
            // A genuine camera move — ServerPlayer.lookAt sends the look-at packet, so the client really turns.
            player.lookAt(EntityAnchorArgument.Anchor.EYES, victim, EntityAnchorArgument.Anchor.EYES);
        }
        player.swing(InteractionHand.MAIN_HAND, true);

        int ticker = readAttackTicker(player);
        if (Config.VIOLENCE_FULL_STRENGTH_SWINGS.get()) {
            // land it as a properly-timed swing rather than a limp mid-cooldown tap (also allows sweep/crit).
            writeAttackTicker(player, (int) Math.ceil(player.getCurrentItemAttackStrengthDelay()));
        }
        player.attack(victim); // vanilla: damage, enchantments, knockback, crits, sweeping, sounds
        // attack() resets the cooldown itself, so by default the swing costs you your next hit's damage.
        if (Config.VIOLENCE_REFUNDS_ATTACK_COOLDOWN.get() && ticker >= 0) {
            writeAttackTicker(player, ticker);
        }

        player.setData(WitchModAttachments.VIOLENCE_LAST_SWING, player.serverLevel().getGameTime());
        markDiscoveredByVictim(player);

        if (player.getRandom().nextInt(100) < Config.VIOLENCE_MULTI_HIT_CHANCE_PERCENT.get()
                && !FLURRIES.containsKey(player.getUUID())) {
            int min = Config.VIOLENCE_MULTI_HIT_MIN.get();
            int max = Math.max(min, Config.VIOLENCE_MULTI_HIT_MAX.get());
            int extra = min + player.getRandom().nextInt(max - min + 1);
            FLURRIES.put(player.getUUID(), new int[] {extra, Config.VIOLENCE_MULTI_HIT_SPACING_TICKS.get()});
        }
    }

    // --- Targeting -------------------------------------------------------------------------------------

    /** everything alive, hittable and in range — the candidate pool for both urge types. */
    private static List<LivingEntity> candidates(ServerPlayer player) {
        double range = Config.VIOLENCE_TARGET_RANGE.get();
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(range),
                e -> e != player && e.isAlive() && e.isAttackable()
                        && !(e instanceof Player other && (other.isCreative() || other.isSpectator()))
                        && player.distanceTo(e) <= range); // the inflated box has corners; keep it a real radius
    }

    /** the nearest thing you are looking straight at, if any. */
    @Nullable
    private static LivingEntity entityInSight(ServerPlayer player) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates(player)) {
            if (!isLookingAt(player, candidate)) {
                continue;
            }
            double distance = player.distanceTo(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isLookingAt(ServerPlayer player, LivingEntity candidate) {
        Vec3 toward = candidate.getEyePosition().subtract(player.getEyePosition());
        if (toward.lengthSqr() < 1.0E-6) {
            return true;
        }
        double cosAngle = player.getLookAngle().normalize().dot(toward.normalize());
        return cosAngle >= Math.cos(Math.toRadians(Config.VIOLENCE_SIGHT_CONE_DEGREES.get()))
                && player.hasLineOfSight(candidate);
    }

    /** picks the impulsive victim: highest priority tier first, then nearest (nudged by a lethal shove). */
    @Nullable
    private static LivingEntity pickVictim(ServerPlayer player) {
        List<LivingEntity> pool = candidates(player);
        if (pool.isEmpty()) {
            return null;
        }
        // every so often the urge ignores all of the above and just lashes out at whoever, for variety.
        if (player.getRandom().nextInt(100) < Config.VIOLENCE_RANDOM_TARGET_CHANCE_PERCENT.get()) {
            return pool.get(player.getRandom().nextInt(pool.size()));
        }

        LivingEntity best = null;
        int bestTier = -1;
        double bestScore = -1.0;

        for (LivingEntity candidate : pool) {
            boolean lethalShove = shoveEndsBadly(player, candidate);
            int tier = priorityTier(candidate, lethalShove);

            double score = 1.0 / (1.0 + player.distanceTo(candidate)); // nearest-first within the tier
            if (candidate instanceof Player) {
                score *= Config.VIOLENCE_PLAYER_PRIORITY_MULTIPLIER.get();
            }
            if (lethalShove) {
                score *= Config.VIOLENCE_HAZARD_BIAS_MULTIPLIER.get();
            }

            if (tier > bestTier || (tier == bestTier && score > bestScore)) {
                bestTier = tier;
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * the pecking order, as tiers so the guarantees actually hold — multiplied weights can't promise
     * "always", since a big enough product elsewhere would eventually overtake. Each trait is worth strictly
     * more than everything beneath it combined, so the ordering is exact:
     * <ul>
     *   <li><b>+4 environmental kill</b> — the shove would put them off a ledge, into lava/fire/cactus, or
     *       next to lit TNT. This is the dominant term: ANY hazard shove outranks any non-hazard target,
     *       however wounded.</li>
     *   <li><b>+2 wounded</b> — at or under {@code violenceLowHealthPercent} health. Ranks below hazards but
     *       above being a player, so a wounded mob outranks a healthy player standing safely.</li>
     *   <li><b>+1 player</b> — the tie-breaker between otherwise equal targets.</li>
     * </ul>
     * Giving 7 (wounded player at a ledge) down to 0 (an ordinary mob standing somewhere harmless). To
     * re-rank, change these three weights — nothing else needs touching.
     */
    private static int priorityTier(LivingEntity candidate, boolean lethalShove) {
        boolean wounded = candidate.getHealth()
                <= candidate.getMaxHealth() * (Config.VIOLENCE_LOW_HEALTH_PERCENT.get() / 100.0F);

        int tier = 0;
        if (lethalShove) {
            tier += 4;
        }
        if (wounded) {
            tier += 2;
        }
        if (candidate instanceof Player) {
            tier += 1;
        }
        return tier;
    }

    // --- Hazards ---------------------------------------------------------------------------------------

    /** remembers how long each nearby entity has been standing somewhere it really shouldn't. */
    private static void trackHazardPerches(ServerPlayer player, long now) {
        for (LivingEntity candidate : candidates(player)) {
            if (isPerchedDangerously(player.serverLevel(), candidate)) {
                long[] perch = HAZARD_PERCHES.get(candidate.getId());
                if (perch == null) {
                    HAZARD_PERCHES.put(candidate.getId(), new long[] {now, now});
                } else {
                    perch[1] = now;
                }
            } else {
                HAZARD_PERCHES.remove(candidate.getId());
            }
        }
        // drop anything we've stopped hearing about, so the map can't grow without bound.
        HAZARD_PERCHES.values().removeIf(perch -> now - perch[1] > HAZARD_PERCH_FORGET_TICKS);
    }

    /** whether anything in reach has now been loitering somewhere dangerous for long enough to tempt fate. */
    private static boolean hasSustainedHazardPerch(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        int sustain = Config.VIOLENCE_HAZARD_SUSTAIN_TICKS.get();
        for (LivingEntity candidate : candidates(player)) {
            long[] perch = HAZARD_PERCHES.get(candidate.getId());
            if (perch != null && now - perch[0] >= sustain) {
                return true;
            }
        }
        return false;
    }

    /**
     * whether the entity is loitering at the edge of something nasty — used for the sustained timer, so it's
     * deliberately independent of where the cursed player happens to be standing.
     */
    private static boolean isPerchedDangerously(ServerLevel level, LivingEntity entity) {
        BlockPos at = entity.blockPosition();
        if (litTntNear(level, entity.position())) {
            return true;
        }
        if (isNasty(level, at) || isNasty(level, at.below())) {
            return true;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = at.relative(dir);
            if (isNasty(level, side) || isNasty(level, side.below()) || isDrop(level, side)) {
                return true;
            }
        }
        return false;
    }

    /**
     * whether knocking {@code victim} back would send them off a ledge or into something that hurts. Vanilla
     * knockback pushes directly away from the attacker, so this walks that line and looks for trouble.
     */
    private static boolean shoveEndsBadly(ServerPlayer player, LivingEntity victim) {
        Vec3 away = victim.position().subtract(player.position());
        if (away.horizontalDistanceSqr() < 1.0E-4) {
            return false;
        }
        Vec3 direction = new Vec3(away.x, 0.0, away.z).normalize();
        ServerLevel level = player.serverLevel();

        for (int step = 1; step <= Config.VIOLENCE_HAZARD_SHOVE_DISTANCE.get(); step++) {
            Vec3 spot = victim.position().add(direction.scale(step));
            BlockPos pos = BlockPos.containing(spot);
            if (isNasty(level, pos) || isNasty(level, pos.below()) || isDrop(level, pos)
                    || litTntNear(level, spot)) {
                return true;
            }
        }
        return false;
    }

    /** lit TNT is an ENTITY, not a block, so it needs its own look — the block scan would never see it. */
    private static boolean litTntNear(ServerLevel level, Vec3 spot) {
        return !level.getEntitiesOfClass(PrimedTnt.class, new AABB(spot, spot).inflate(TNT_DANGER_RADIUS)).isEmpty();
    }

    private static boolean isNasty(ServerLevel level, BlockPos pos) {
        if (level.getFluidState(pos).is(FluidTags.LAVA)) {
            return true;
        }
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POINTED_DRIPSTONE);
    }

    /** A ledge: nothing solid for several blocks straight down. */
    private static boolean isDrop(ServerLevel level, BlockPos pos) {
        for (int depth = 0; depth <= Config.VIOLENCE_LEDGE_DROP_MIN.get(); depth++) {
            if (!level.getBlockState(pos.below(depth)).isAir()) {
                return false;
            }
        }
        return true;
    }

    // --- Attack cooldown plumbing ----------------------------------------------------------------------

    private static int readAttackTicker(ServerPlayer player) {
        if (ATTACK_STRENGTH_TICKER == null) {
            return -1;
        }
        try {
            return ATTACK_STRENGTH_TICKER.getInt(player);
        } catch (IllegalAccessException e) {
            return -1;
        }
    }

    private static void writeAttackTicker(ServerPlayer player, int value) {
        if (ATTACK_STRENGTH_TICKER == null) {
            return;
        }
        try {
            ATTACK_STRENGTH_TICKER.setInt(player, value);
        } catch (IllegalAccessException ignored) {
            // non-fatal: the swing already happened, we just couldn't set the charge.
        }
    }

    @Nullable
    private static Field resolveAttackStrengthTicker() {
        try {
            Field field = LivingEntity.class.getDeclaredField("attackStrengthTicker");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            WitchMod.LOGGER.warn("[Violence] Could not access attackStrengthTicker; forced swings may land "
                    + "at partial strength.", e);
            return null;
        }
    }
}
