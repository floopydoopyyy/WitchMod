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
 * tracks placed ledger blocks (so a landed hex reacts without a world scan) and streams glyphs + a chime into
 * each ledger within {@link Config#LEDGER_RANGE} when a cast is logged. the registry is per-session (filled on
 * place/open, cleared on break) — a ledger only glows once placed/opened this session, but its log-reading always works.
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

    // the registry is in-memory, so a restart/reload would lose it — re-register ledgers as their chunks load
    // (same as the Warding Totem) so a placed ledger keeps recording without needing to be opened first.
    @SubscribeEvent
    static void onChunkLoad(net.neoforged.neoforge.event.level.ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        net.minecraft.world.level.chunk.ChunkAccess chunk = event.getChunk();
        net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int si = 0; si < sections.length; si++) {
            net.minecraft.world.level.chunk.LevelChunkSection sec = sections[si];
            if (sec == null || sec.hasOnlyAir()) {
                continue;
            }
            int y0 = level.getMinBuildHeight() + si * 16;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        if (sec.getBlockState(x, y, z).getBlock() instanceof LedgerBlock) {
                            register(level, new BlockPos(baseX + x, y0 + y, baseZ + z));
                        }
                    }
                }
            }
        }
    }

    /** push a logged cast into every registered Ledger within range of where it happened (event-driven, no scan). */
    public static void record(com.oliver.witchmod.data.LedgerLog.Entry e) {
        if (e.pos().isEmpty()) {
            return;
        }
        net.minecraft.core.GlobalPos where = e.pos().get();
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ServerLevel level = server.getLevel(where.dimension());
        if (level == null) {
            return;
        }
        Set<BlockPos> set = LEDGERS.get(level.dimension());
        if (set == null || set.isEmpty()) {
            return;
        }
        double r2 = (double) Config.LEDGER_RANGE.get() * Config.LEDGER_RANGE.get();
        LedgerBlockEntity.Row row = new LedgerBlockEntity.Row(e.casterName().orElse("(system)"), e.targetName(),
                e.effectId(), e.result(), e.gameTime(), e.scribbled(), e.modifier().orElse(""));
        for (BlockPos lp : set) {
            if (where.pos().distSqr(lp) > r2) {
                continue;
            }
            if (level.getBlockEntity(lp) instanceof LedgerBlockEntity be) {
                be.record(row);
            }
        }
    }

    /** streams particles + a chime into every Ledger within range of a landed hex at {@code from}. */
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
