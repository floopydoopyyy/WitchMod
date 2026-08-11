package com.oliver.witchmod.effects.blessings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.effects.Blessings;

/**
 * A green thumb (master-spec Farmer's Spirit, sacrificial item CARROT): plants near you shoot up. Every pass
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

    /** You find out the first time something visibly surges under your influence (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (!EffectUtil.every(ticksRemaining, Config.FARMSPIRIT_INTERVAL.get())) {
            return;
        }
        ServerLevel level = target.serverLevel();
        int radius = Config.FARMSPIRIT_RADIUS.get();
        int attempts = Config.FARMSPIRIT_ATTEMPTS.get();

        for (int i = 0; i < attempts; i++) {
            BlockPos pos = target.blockPosition().offset(
                    level.random.nextInt(radius * 2 + 1) - radius,
                    level.random.nextInt(5) - 2,
                    level.random.nextInt(radius * 2 + 1) - radius);
            tryGrow(level, target, pos);
        }
    }

    private static void tryGrow(ServerLevel level, ServerPlayer target, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BonemealableBlock bonemealable)
                || !bonemealable.isValidBonemealTarget(level, pos, state)) {
            return;
        }

        boolean grass = state.getBlock() instanceof GrassBlock;
        if (grass) {
            // Grass gets ONE application, only occasionally, and NEVER right where you're standing — spawning
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

        // Crops/saplings/etc. get MANY applications per hit, so they shoot up fast (re-checking validity each
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

    private static void sparkle(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                6, 0.3, 0.3, 0.3, 0.0);
    }
}
