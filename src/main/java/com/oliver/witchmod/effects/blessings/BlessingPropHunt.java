package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.Blessings;

/**
 * Prop hunt (sacrificial item FLOWER POT): crouch and hold still for a second to disguise as the block below
 * you — for everyone — with a pop. Once disguised, you can RUN AROUND as the block (the disguise follows you);
 * <b>crouch to anchor</b> onto the grid at exact block height, and it re-samples the block underfoot so you can
 * borrow other blocks' looks. Only a real ACTION (attack/mine/use/place/interact) pops you back.
 *
 * <p>The anchor cell is computed from the SUPPORT block (feet minus a sliver) and synced as a {@code BlockPos}
 * so the render is exactly grid-aligned — never sunk by the feet resting a hair below the integer.
 */
public final class BlessingPropHunt extends Effect {
    private static final Map<UUID, Integer> STILL = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();

    public BlessingPropHunt() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.FLOWER_POT);
    }

    @Override
    public java.util.Optional<String> scryingDetail(ServerPlayer target) {
        int id = target.getData(WitchModAttachments.PROPHUNT_BLOCK);
        if (id < 0) {
            return java.util.Optional.of("not disguised");
        }
        return java.util.Optional.of("disguised as "
                + net.minecraft.world.level.block.Block.stateById(id).getBlock().getName().getString());
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        UUID id = target.getUUID();
        ServerLevel level = target.serverLevel();
        Vec3 pos = target.position();
        Vec3 last = LAST_POS.put(id, pos);
        boolean crouching = target.isCrouching() && !target.isSwimming();
        boolean disguised = target.getData(WitchModAttachments.PROPHUNT_BLOCK) >= 0;

        if (!disguised) {
            // Enter: crouch + hold still for a second.
            boolean still = last != null && last.distanceToSqr(pos) < 0.0016;
            if (crouching && still) {
                if (STILL.merge(id, 1, Integer::sum) >= Config.PROPHUNT_STILL_TICKS.get()) {
                    anchor(target, level, true);
                }
            } else {
                STILL.put(id, 0);
            }
            return;
        }

        // Already disguised — persists through movement. Crouch anchors + re-samples the block underfoot.
        if (crouching) {
            anchor(target, level, false);
        } else {
            target.setData(WitchModAttachments.PROPHUNT_ANCHOR, Long.MIN_VALUE); // moving — the block follows you
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        if (target.getData(WitchModAttachments.PROPHUNT_BLOCK) >= 0) {
            cancel(target);
        }
        STILL.remove(target.getUUID());
        LAST_POS.remove(target.getUUID());
    }

    /** A real action (attack/use/etc.) drops the disguise. Called from {@code BlessingEventHandler}. */
    public static void actionTaken(ServerPlayer player) {
        if (player.getData(WitchModAttachments.PROPHUNT_BLOCK) >= 0) {
            cancel(player);
        }
    }

    /** The support block just below the feet (robust against the feet resting a hair below the integer). */
    private static BlockPos supportPos(ServerPlayer target) {
        return BlockPos.containing(target.getX(), target.getY() - 0.1, target.getZ());
    }

    private static void anchor(ServerPlayer target, ServerLevel level, boolean entering) {
        BlockPos support = supportPos(target);
        BlockState state = level.getBlockState(support);
        if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
            return; // nothing solid to become / anchor to
        }
        int newId = Block.getId(state);
        int oldId = target.getData(WitchModAttachments.PROPHUNT_BLOCK);
        target.setData(WitchModAttachments.PROPHUNT_BLOCK, newId);
        target.setData(WitchModAttachments.PROPHUNT_ANCHOR, support.above().asLong()); // the cell you stand IN
        if (entering || newId != oldId) {
            level.playSound(null, target.blockPosition(), state.getSoundType().getPlaceSound(), SoundSource.PLAYERS, 0.8F, 1.5F);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                    target.getX(), target.getY() + 0.5, target.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
            if (entering) {
                Blessings.PROP_HUNT.get().markDiscoveredByVictim(target);
            }
        }
    }

    private static void cancel(ServerPlayer target) {
        int wasId = target.getData(WitchModAttachments.PROPHUNT_BLOCK);
        target.setData(WitchModAttachments.PROPHUNT_BLOCK, -1);
        target.setData(WitchModAttachments.PROPHUNT_ANCHOR, Long.MIN_VALUE);
        STILL.put(target.getUUID(), 0);
        ServerLevel level = target.serverLevel();
        if (wasId >= 0) {
            BlockState was = Block.stateById(wasId);
            level.playSound(null, target.blockPosition(), was.getSoundType().getBreakSound(), SoundSource.PLAYERS, 0.8F, 1.5F);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                    target.getX(), target.getY() + 0.5, target.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
        }
    }
}
