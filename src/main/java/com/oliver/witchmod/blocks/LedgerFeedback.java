package com.oliver.witchmod.blocks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;

/**
 * Tracks placed Ledger blocks (so a landed hex can react without scanning the world) and streams particle
 * feedback INTO every Ledger within {@link Config#LEDGER_RANGE} when an attachment is logged — enchanting-
 * table glyphs flowing from the hex into the block, plus a coloured trail and a chime.
 *
 * <p>The registry is filled on block place, right-click ({@link LedgerBlock}), and cleared on break. A
 * server restart empties it, so a Ledger only glows once it's been placed or opened that session — a purely
 * cosmetic gap; the block's actual log-reading (range-filtered from {@code LedgerLog}) always works.
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class LedgerFeedback {
    private LedgerFeedback() {}

    private static final Map<ResourceKey<Level>, Set<BlockPos>> LEDGERS = new HashMap<>();
    private static final DustParticleOptions CURSE_DUST = new DustParticleOptions(new Vector3f(0.72F, 0.40F, 0.85F), 1.0F);
    private static final DustParticleOptions BLESS_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.35F), 1.0F);

    static void register(Level level, BlockPos pos) {
        LEDGERS.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
    }

    static void unregister(Level level, BlockPos pos) {
        Set<BlockPos> set = LEDGERS.get(level.dimension());
        if (set != null) {
            set.remove(pos);
        }
    }

    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getPlacedBlock().is(WitchModBlocks.LEDGER.get()) && event.getLevel() instanceof Level level) {
            register(level, event.getPos());
        }
    }

    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getState().is(WitchModBlocks.LEDGER.get()) && event.getLevel() instanceof Level level) {
            unregister(level, event.getPos());
        }
    }

    /** Streams particles + a chime into every Ledger within range of a landed hex at {@code from}. */
    public static void pulse(ServerLevel level, Vec3 from, boolean blessing) {
        Set<BlockPos> set = LEDGERS.get(level.dimension());
        if (set == null || set.isEmpty()) {
            return;
        }
        double r2 = (double) Config.LEDGER_RANGE.get() * Config.LEDGER_RANGE.get();
        for (BlockPos lp : set) {
            if (Vec3.atCenterOf(lp).distanceToSqr(from) > r2) {
                continue;
            }
            if (!level.getBlockState(lp).is(WitchModBlocks.LEDGER.get())) {
                continue; // stale registry entry (removed by piston/explosion) — skip
            }
            streamInto(level, from, lp, blessing);
        }
    }

    private static void streamInto(ServerLevel level, Vec3 from, BlockPos lp, boolean blessing) {
        Vec3 to = Vec3.atCenterOf(lp).add(0.0, 0.35, 0.0);
        Vec3 src = from.add(0.0, 1.0, 0.0);
        DustParticleOptions dust = blessing ? BLESS_DUST : CURSE_DUST;

        // ENCHANT glyphs: spawned AT the ledger with velocity pointing back to the hex, so vanilla flies them
        // FROM the hex INTO the ledger (the enchanting-table-into-book effect).
        for (int i = 0; i < 16; i++) {
            double ex = src.x + (level.random.nextDouble() - 0.5) * 0.9;
            double ey = src.y + level.random.nextDouble();
            double ez = src.z + (level.random.nextDouble() - 0.5) * 0.9;
            level.sendParticles(ParticleTypes.ENCHANT, to.x, to.y, to.z, 0, ex - to.x, ey - to.y, ez - to.z, 1.0);
        }
        // A faint coloured trail down the line + an arrival puff at the block.
        for (int i = 0; i <= 8; i++) {
            Vec3 at = src.lerp(to, i / 8.0);
            level.sendParticles(dust, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        level.sendParticles(dust, to.x, to.y, to.z, 12, 0.12, 0.15, 0.12, 0.0);
        // A subtle pencil-scratch as the entry is written in.
        level.playSound(null, lp, com.oliver.witchmod.data.WitchModSounds.LEDGER_WRITE.get(), SoundSource.BLOCKS, 0.7F, 1.0F);
    }
}
