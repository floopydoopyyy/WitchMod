package com.oliver.witchmod.effects.blessings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.effects.Blessings;

/**
 * A green thumb: plants near you shoot up. Every pass
 * it "bone-meals" a handful of random {@link BonemealableBlock}s in range — so ANYTHING that responds to bone
 * meal grows fast: crops, saplings, stems, nether wart, sweet berries, cocoa, bamboo, and so on.
 *
 * <p><b>Grass to a lesser extent:</b> grass BLOCKS also count (bone meal spreads flowers/tall grass over
 * them), but only at {@code farmersSpiritGrassChance}, so the ground fuzzes over gradually rather than
 * carpeting instantly — and the block right under your feet is nudged the same way as you walk.
 */
public final class BlessingFarmersSpirit extends Effect {
    public BlessingFarmersSpirit() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.CARROT);
    }

    /** you find out the first time something visibly surges under your influence (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @org.jetbrains.annotations.Nullable ServerPlayer caster, int durationTicks) {
        target.setData(com.oliver.witchmod.data.WitchModAttachments.FARMERS_SPIRIT_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(com.oliver.witchmod.data.WitchModAttachments.FARMERS_SPIRIT_ACTIVE, -1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (target.getData(com.oliver.witchmod.data.WitchModAttachments.FARMERS_SPIRIT_ACTIVE) < 0) {
            target.setData(com.oliver.witchmod.data.WitchModAttachments.FARMERS_SPIRIT_ACTIVE, 1); // self-heal (relog)
        }
        if (!EffectUtil.every(ticksRemaining, Config.FARMSPIRIT_INTERVAL.get())) {
            return;
        }
        ServerLevel level = target.serverLevel();
        int radius = Config.FARMSPIRIT_RADIUS.get();
        int attempts = Config.FARMSPIRIT_ATTEMPTS.get();

        for (int i = 0; i < attempts; i++) {
            int dx = level.random.nextInt(radius * 2 + 1) - radius;
            int dz = level.random.nextInt(radius * 2 + 1) - radius;
            if (dx * dx + dz * dz > radius * radius) {
                continue; // keep it a circle, not a square
            }
            tryGrow(level, target, target.blockPosition().offset(dx, level.random.nextInt(5) - 2, dz));
        }
        growBabies(target, level, radius);
    }

    /** young livestock in range grow up quicker; the Drive synergy multiplies the rush. */
    private static void growBabies(ServerPlayer target, ServerLevel level, int radius) {
        int growth = Config.FARMSPIRIT_BABY_GROWTH_TICKS.get();
        if (growth <= 0) {
            return;
        }
        if (com.oliver.witchmod.synergy.Synergies.GROWTH_SPURT.activeFor(target)) {
            growth = (int) Math.round(growth * Config.FARMSPIRIT_DRIVE_GROWTH_MULTIPLIER.get());
        }
        for (net.minecraft.world.entity.animal.Animal a : level.getEntitiesOfClass(
                net.minecraft.world.entity.animal.Animal.class, target.getBoundingBox().inflate(radius),
                e -> e.isAlive() && e.isBaby())) {
            a.setAge(Math.min(0, a.getAge() + growth)); // negative age counts up to 0 = adult
            if (level.random.nextInt(3) == 0) {
                sparkle(level, a.blockPosition());
            }
            Blessings.FARMERS_SPIRIT.get().markDiscoveredByVictim(target);
        }
    }

    private static void tryGrow(ServerLevel level, ServerPlayer target, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        // producers (pumpkin/melon stems, sugar cane, cactus, bamboo) make their blocks via RANDOM TICKS, not
        // bone meal — so hit them with a burst of random ticks to crank out fruit/stalks fast.
        if (isProducer(state) && state.isRandomlyTicking()) {
            boolean did = false;
            for (int i = 0; i < Config.FARMSPIRIT_PRODUCE_TICKS.get(); i++) {
                BlockState now = level.getBlockState(pos);
                if (!isProducer(now)) {
                    break;
                }
                now.randomTick(level, pos, level.random);
                did = true;
            }
            if (did) {
                sparkle(level, pos);
                Blessings.FARMERS_SPIRIT.get().markDiscoveredByVictim(target);
            }
            return;
        }

        if (!(state.getBlock() instanceof BonemealableBlock bonemealable)
                || !bonemealable.isValidBonemealTarget(level, pos, state)) {
            return;
        }

        boolean grass = state.getBlock() instanceof GrassBlock;
        if (grass) {
            // grass gets ONE application, only occasionally, and NEVER right where you're standing — spawning
            // double tall grass on your own feet is insufferable, so keep the player's own column clear.
            double dx = (pos.getX() + 0.5) - target.getX();
            double dz = (pos.getZ() + 0.5) - target.getZ();
            if (dx * dx + dz * dz < 2.25 || level.random.nextDouble() >= Config.FARMSPIRIT_GRASS_CHANCE.get()) {
                return;
            }
            if (bonemealable.isBonemealSuccess(level, level.random, pos, state)) {
                bonemealable.performBonemeal(level, level.random, pos, state);
                sparkle(level, pos);
                Blessings.FARMERS_SPIRIT.get().markDiscoveredByVictim(target);
            }
            return;
        }

        // crops/saplings/etc. get MANY applications per hit, so they shoot up fast (re-checking validity each
        // time — a fully grown plant simply stops).
        int applied = 0;
        for (int k = 0; k < Config.FARMSPIRIT_GROWTH_PER_HIT.get(); k++) {
            BlockState now = level.getBlockState(pos);
            if (!(now.getBlock() instanceof BonemealableBlock b) || !b.isValidBonemealTarget(level, pos, now)) {
                break;
            }
            if (b.isBonemealSuccess(level, level.random, pos, now)) {
                b.performBonemeal(level, level.random, pos, now);
                applied++;
            }
        }
        if (applied > 0) {
            sparkle(level, pos);
            Blessings.FARMERS_SPIRIT.get().markDiscoveredByVictim(target);
        }
    }

    private static boolean isProducer(BlockState state) {
        return state.getBlock() instanceof StemBlock
                || state.getBlock() instanceof SugarCaneBlock
                || state.getBlock() instanceof CactusBlock
                || state.getBlock() instanceof BambooStalkBlock;
    }

    private static void sparkle(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                6, 0.3, 0.3, 0.3, 0.0);
    }
}
