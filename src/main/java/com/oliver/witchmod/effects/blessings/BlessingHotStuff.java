package com.oliver.witchmod.effects.blessings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * You run hot (master-spec Hot Stuff, sacrificial item COAL): a furnace, blast furnace or smoker you're
 * LOOKING at cooks {@code hotStuffSpeedMultiplier} (5x) faster. Each tick your gaze lands on a lit furnace
 * (within {@code hotStuffLookRange}) we run its cook logic a few extra times — so it also burns fuel that
 * bit faster, keeping the fuel-per-item the same, just quicker. The prototype's Fire Resistance stand-in is
 * dropped.
 *
 * <p>(Campfires from the spec are deferred — their cook tick needs a recipe-check argument that's awkward to
 * assemble here; the three furnace types cover the ask.)
 */
public final class BlessingHotStuff extends Effect {
    public BlessingHotStuff() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 21, () -> Items.COAL);
    }

    /** You find out the first time your stare gets a furnace going (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        ServerLevel level = target.serverLevel();
        double range = Config.HOTSTUFF_LOOK_RANGE.get();
        Vec3 eye = target.getEyePosition();
        Vec3 end = eye.add(target.getViewVector(1.0F).scale(range));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, target));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        // A LIT furnace/blast furnace/smoker (LIT only turns on while it's actually burning fuel to cook).
        if (!state.hasProperty(BlockStateProperties.LIT) || !state.getValue(BlockStateProperties.LIT)
                || !(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) {
            return;
        }

        int extra = (int) Math.round(Config.HOTSTUFF_SPEED_MULT.get()) - 1; // it already ticks once itself
        for (int i = 0; i < extra; i++) {
            BlockState now = level.getBlockState(pos);
            if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity f)) {
                break;
            }
            AbstractFurnaceBlockEntity.serverTick(level, pos, now, f);
        }
        markDiscoveredByVictim(target);

        // A wisp of extra flame so the heat reads.
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                2, 0.2, 0.1, 0.2, 0.01);
    }
}
