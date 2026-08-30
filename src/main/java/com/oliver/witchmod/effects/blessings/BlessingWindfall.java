package com.oliver.witchmod.effects.blessings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.effects.Blessings;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * The wind keeps blowing good fortune your way (master-spec Windfall, sacrificial item WIND CHARGE). As you
 * walk, every now and then a nice item comes drifting THROUGH your view — spawned off to one side, out in open
 * air, and pushed on a steady breeze so it slides ACROSS in front of you. The point is the "ooo, what's that"
 * moment: you notice it passing and decide whether to break stride and grab it.
 *
 * <p>It's a passing thing, not a handout: it spawns several blocks away (never on top of you), and the drift
 * timer only advances while you're actually moving. If you ignore it too long ({@code windfallItemLifespanTicks})
 * or walk away from it ({@code windfallWalkAwayDistance}), it is instantly removed rather than left on the
 * ground. Timing is a random {@code windfallIntervalMin..Max} countdown.
 */
public final class BlessingWindfall extends Effect {
    /** player -> ticks of MOVEMENT until the next windfall drifts by. */
    private static final Map<UUID, Integer> NEXT = new HashMap<>();

    /** player -> the drifting items currently in the air for them (so they can be culled on walk-away/expiry). */
    private static final Map<UUID, List<Drifting>> ACTIVE = new HashMap<>();

    /** A live windfall item plus the tick it was spawned, so its age can be measured. */
    private record Drifting(ItemEntity entity, int spawnedAt) {}

    /** Below this horizontal speed the player counts as "standing still" and the timer is paused. */
    private static final double MOVING_SPEED_SQR = 0.0025 * 0.0025; // ~0.0025 blocks/tick

    /** Mostly-useful things (chosen {@code windfallBeneficialChance} of the time). */
    private static final Item[] BENEFICIAL = {
            Items.BREAD, Items.COOKED_BEEF, Items.APPLE, Items.GOLDEN_CARROT, Items.CARROT, Items.WHEAT,
            Items.IRON_INGOT, Items.GOLD_INGOT, Items.EMERALD, Items.COAL, Items.REDSTONE, Items.LAPIS_LAZULI,
            Items.ARROW, Items.ENDER_PEARL, Items.EXPERIENCE_BOTTLE, Items.IRON_NUGGET, Items.STRING,
            Items.LEATHER, Items.BONE, Items.SLIME_BALL, Items.GLOWSTONE_DUST, Items.BOOK, Items.FEATHER,
            Items.FLINT, Items.GUNPOWDER, Items.HONEYCOMB, Items.AMETHYST_SHARD, Items.COPPER_INGOT
    };

    /** The occasional dud, so it isn't a pure freebie faucet. */
    private static final Item[] JUNK = {
            Items.STICK, Items.ROTTEN_FLESH, Items.DIRT, Items.GRAVEL, Items.POISONOUS_POTATO,
            Items.WHEAT_SEEDS, Items.DEAD_BUSH, Items.CACTUS, Items.COBBLESTONE, Items.WOODEN_HOE
    };

    public BlessingWindfall() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.WIND_CHARGE);
    }

    /** You find out the first time you actually spot something drift by (Rule 2), not when it's cast. */
    @Override
    public java.util.Optional<String> scryingDetail(ServerPlayer target) {
        int next = NEXT.getOrDefault(target.getUUID(), 0);
        return java.util.Optional.of(next <= 20 ? "windfall imminent" : "windfall on cooldown");
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public String debugForce(ServerPlayer target, String arg) {
        return drift(target) ? "a windfall drifted in" : "no clear air to float a windfall through right now";
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        NEXT.put(target.getUUID(), 40 + target.getRandom().nextInt(100));
        ACTIVE.put(target.getUUID(), new ArrayList<>());
    }

    @Override
    public void onRemove(ServerPlayer target) {
        NEXT.remove(target.getUUID());
        cullAll(target.getUUID()); // any items still in the air when the blessing ends vanish with it
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        cull(target); // walk-away / expiry sweep every tick, so removal feels instant

        // Windfalls happen anywhere, but come HALF as often while you're standing still — the timer advances
        // every tick when moving, only every OTHER tick when stationary.
        boolean moving = target.getDeltaMovement().horizontalDistanceSqr() >= MOVING_SPEED_SQR;
        if (!moving && (ticksRemaining & 1) == 0) {
            return; // skip this tick's decrement, halving the effective rate while still
        }

        int countdown = NEXT.computeIfAbsent(target.getUUID(), k -> rollInterval(target.getRandom()));
        if (countdown > 0) {
            NEXT.put(target.getUUID(), countdown - 1);
            return;
        }
        // Only reset the timer if a drift actually happened (no open sky to the side? try again next tick).
        if (drift(target)) {
            NEXT.put(target.getUUID(), rollInterval(target.getRandom()));
            Blessings.WINDFALL.get().markDiscoveredByVictim(target); // discovered on the first drift you see
        }
    }

    private static int rollInterval(RandomSource random) {
        int min = Config.WINDFALL_INTERVAL_MIN.get();
        int max = Math.max(min, Config.WINDFALL_INTERVAL_MAX.get());
        return min + random.nextInt(max - min + 1);
    }

    /** Instantly remove any drifting item the player has walked away from or that has run out its life. */
    private static void cull(ServerPlayer target) {
        List<Drifting> items = ACTIVE.get(target.getUUID());
        if (items == null || items.isEmpty()) {
            return;
        }
        double walkAway = Config.WINDFALL_WALK_AWAY_DISTANCE.get();
        double walkAwaySqr = walkAway * walkAway;
        int lifespan = Config.WINDFALL_ITEM_LIFESPAN.get();

        Iterator<Drifting> it = items.iterator();
        while (it.hasNext()) {
            Drifting d = it.next();
            ItemEntity e = d.entity();
            if (e.isRemoved() || e.getItem().isEmpty()) {
                it.remove(); // picked up or already gone
                continue;
            }
            boolean tooOld = e.tickCount - d.spawnedAt() >= lifespan;
            boolean tooFar = e.distanceToSqr(target) > walkAwaySqr;
            if (tooOld || tooFar) {
                e.discard(); // instant removal, no ground clutter left behind
                it.remove();
            }
        }
    }

    private static void cullAll(UUID id) {
        List<Drifting> items = ACTIVE.remove(id);
        if (items == null) {
            return;
        }
        for (Drifting d : items) {
            if (!d.entity().isRemoved()) {
                d.entity().discard();
            }
        }
    }

    /**
     * Send one item sliding across the player's view. Returns false (so the timer doesn't reset) if there
     * wasn't a clear stretch of open air to spawn and drift it through.
     */
    private static boolean drift(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        RandomSource random = target.getRandom();

        // Forward (where you're looking/walking), flattened.
        Vec3 look = target.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0, look.z);
        if (forward.lengthSqr() < 1.0e-4) {
            forward = new Vec3(0.0, 0.0, 1.0);
        }
        forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x); // 90° to the right

        // The gust crosses your path: it blows left<->right (with a little forward/back lean) so the item
        // slides ACROSS in front of you rather than at or away from you.
        int side = random.nextBoolean() ? 1 : -1;
        Vec3 wind = right.scale(side).add(forward.scale((random.nextDouble() - 0.5) * 0.5)).normalize();

        // Aim the crossing a few blocks ahead of you, and loose the item from the upwind side of that point,
        // at about eye height, so it drifts through your forward view.
        Vec3 eye = target.getEyePosition();
        double ahead = 3.0 + random.nextDouble() * 3.0;   // 3..6 blocks in front
        double upwind = 4.0 + random.nextDouble() * 2.0;  // spawn 4..6 blocks to the windward side
        Vec3 crossPoint = eye.add(forward.scale(ahead)).add(0.0, -0.5 + random.nextDouble(), 0.0);
        Vec3 spawn = crossPoint.subtract(wind.scale(upwind));

        // Needs clear air where it spawns AND along the first stretch of drift, or it looks like it's phasing
        // through a wall / a platform. This is what stops the "weird interaction over a covered pool".
        if (!isOpenAir(level, spawn) || !isOpenAir(level, spawn.add(wind.scale(1.5)))
                || !isOpenAir(level, spawn.add(wind.scale(3.0)))) {
            return false;
        }

        boolean beneficial = random.nextDouble() < Config.WINDFALL_BENEFICIAL_CHANCE.get();
        Item[] pool = beneficial ? BENEFICIAL : JUNK;
        ItemStack stack = new ItemStack(pool[random.nextInt(pool.length)], 1 + random.nextInt(4));

        ItemEntity item = new ItemEntity(level, spawn.x, spawn.y, spawn.z, stack);
        item.setDeltaMovement(wind.x * 0.14, 0.0, wind.z * 0.14); // steady horizontal drift
        item.setNoGravity(true); // floats along on the breeze instead of dropping like a stone
        item.setPickUpDelay(20);  // brief grace so it can't be hoovered up the instant it appears
        item.lifespan = Integer.MAX_VALUE; // we manage removal ourselves so it can never quietly despawn early
        level.addFreshEntity(item);

        ACTIVE.computeIfAbsent(target.getUUID(), k -> new ArrayList<>()).add(new Drifting(item, item.tickCount));

        // A little visual gust; no sound cue (Oliver's call).
        level.sendParticles(ParticleTypes.CLOUD, spawn.x, spawn.y, spawn.z, 6, 0.3, 0.2, 0.3, 0.04);
        return true;
    }

    /** True if this point is in passable, fluid-free space — somewhere an item can visibly float, not inside a block. */
    private static boolean isOpenAir(ServerLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getFluidState(pos).isEmpty();
    }
}
