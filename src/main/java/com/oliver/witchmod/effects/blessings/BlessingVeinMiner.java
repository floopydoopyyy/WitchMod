package com.oliver.witchmod.effects.blessings;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.effects.Blessings;

/**
 * Vein Miner (sacrificial item IRON ORE). Break an ORE or a LOG and the whole connected vein / tree comes
 * down at once (combines vein-mining AND timber). Each extra block costs HALF the durability a manual break
 * would, so a big haul still wears the tool, just gently.
 *
 * <p><b>Fortune works both ways.</b> Each felled block drops via {@code Block.dropResources(..., tool)}, which
 * (a) runs the block's loot table with your TOOL, so the Fortune/Silk Touch ENCHANTMENT applies, and (b) fires
 * NeoForge's {@code BlockDropsEvent} with the breaker set to you, so the Fortune BLESSING's extra-drop hook
 * boosts them too — exactly like a manual break.
 */
public final class BlessingVeinMiner extends Effect {
    /** Guards against the flood-fill re-triggering the break event on the blocks it removes. */
    private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> false);

    public BlessingVeinMiner() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 42, () -> Items.IRON_ORE);
    }

    /** Not instantly noticeable — you discover it the first time a vein/tree comes down in one break. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    /** Called from the block-break event: if the broken block is an ore/log, fell the whole connected mass. */
    public static void onBreak(ServerPlayer player, ServerLevel level, BlockPos origin, BlockState state) {
        if (BUSY.get()) {
            return;
        }
        boolean log = state.is(BlockTags.LOGS);
        boolean ore = state.is(Tags.Blocks.ORES);
        if (!log && !ore) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        int cap = Config.VEIN_MINER_MAX.get();
        Set<BlockPos> found = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        found.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && found.size() < cap) {
            BlockPos p = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos np = p.offset(dx, dy, dz);
                        if (found.contains(np) || found.size() >= cap) {
                            continue;
                        }
                        BlockState ns = level.getBlockState(np);
                        // Trees connect through ANY log; a vein connects through the SAME ore block.
                        boolean match = log ? ns.is(BlockTags.LOGS) : ns.is(state.getBlock());
                        if (match) {
                            found.add(np);
                            queue.add(np);
                        }
                    }
                }
            }
        }
        found.remove(origin); // vanilla already breaks the one you actually hit
        if (found.isEmpty()) {
            return;
        }
        BUSY.set(true);
        try {
            int extra = 0;
            for (BlockPos p : found) {
                BlockState bs = level.getBlockState(p);
                Block.dropResources(bs, level, p, level.getBlockEntity(p), player, tool); // respects Fortune/Silk + fires BlockDropsEvent
                blockFx(level, bs, p);
                level.removeBlock(p, false);
                extra++;
            }
            if (extra > 0 && tool.isDamageableItem()) {
                tool.hurtAndBreak(Math.max(1, Math.round(extra * 0.5F)), player, EquipmentSlot.MAINHAND);
            }
        } finally {
            BUSY.set(false);
        }
        // A flourish at the origin so the whole vein/tree coming down reads as one satisfying event.
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, 30, 0.6, 0.6, 0.6, 0.6);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH,
                origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, 1, 0, 0, 0, 0);
        Blessings.VEIN_MINER.get().markDiscoveredByVictim(player);
    }

    /** Cool per-block break FX: the block's own crack dust plus a sparkle of enchant/crit. */
    private static void blockFx(ServerLevel level, BlockState state, BlockPos p) {
        double x = p.getX() + 0.5, y = p.getY() + 0.5, z = p.getZ() + 0.5;
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                net.minecraft.core.particles.ParticleTypes.BLOCK, state), x, y, z, 10, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, x, y, z, 4, 0.25, 0.25, 0.25, 0.15);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, x, y, z, 2, 0.3, 0.3, 0.3, 0.0);
    }
}
