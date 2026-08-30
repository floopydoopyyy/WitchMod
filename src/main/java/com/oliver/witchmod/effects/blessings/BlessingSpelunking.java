package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * Blessing of Spelunking (TORCH): a miner's sixth sense. Every {@code spelunkingIntervalTicks} it scans the
 * blocks around you and, for YOUR EYES ONLY, marks every ore with a floating coloured mote that shows through
 * stone (colour-coded by ore) — so you can beeline for the good stuff. Reuses Sonar's per-player particle
 * trick (a {@link ClientboundLevelParticlesPacket} sent only down the caster's connection).
 */
public final class BlessingSpelunking extends Effect {
    public BlessingSpelunking() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.TORCH);
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        int interval = Config.SPELUNKING_INTERVAL_TICKS.get();
        if (target.tickCount % interval != 0) {
            return;
        }
        if (markOresAround(target, Config.SPELUNKING_RADIUS.get(), 3)) {
            markDiscoveredByVictim(target);
        }
    }

    /**
     * Scan a box of the given radius around the player and paint each ore with a colour-coded, see-through mote
     * sent ONLY to that player. Shared so the Sonar blessing can reveal ores in its big radius too.
     *
     * @return true if any ore was marked.
     */
    public static boolean markOresAround(ServerPlayer target, int radius, int count) {
        ServerLevel level = target.serverLevel();
        BlockPos centre = target.blockPosition();
        boolean any = false;
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-radius, -radius, -radius),
                centre.offset(radius, radius, radius))) {
            BlockState state = level.getBlockState(p);
            if (!state.is(net.neoforged.neoforge.common.Tags.Blocks.ORES)) {
                continue;
            }
            DustParticleOptions dust = oreColour(state);
            double cx = p.getX() + 0.5, cy = p.getY() + 0.5, cz = p.getZ() + 0.5;
            // A dense, bright cluster ON the ore...
            target.connection.send(new ClientboundLevelParticlesPacket(dust, true,
                    cx, cy, cz, 0.32F, 0.32F, 0.32F, 0.0F, 8 + count));
            // ...plus a short rising WISP above it, so it stands out as a clear beacon through the stone.
            for (int h = 0; h < 3; h++) {
                target.connection.send(new ClientboundLevelParticlesPacket(dust, true,
                        cx, cy + 0.6 + h * 0.5, cz, 0.05F, 0.05F, 0.05F, 0.0F, 1));
            }
            // GLOW + END_ROD render FULL-BRIGHT (unaffected by block light), so the ore stays obvious in a
            // pitch-black cave where the tinted dust would otherwise be too dark to see.
            target.connection.send(new ClientboundLevelParticlesPacket(net.minecraft.core.particles.ParticleTypes.GLOW,
                    true, cx, cy, cz, 0.25F, 0.25F, 0.25F, 0.0F, 3));
            target.connection.send(new ClientboundLevelParticlesPacket(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    true, cx, cy + 0.3, cz, 0.08F, 0.2F, 0.08F, 0.0F, 1));
            any = true;
        }
        return any;
    }

    /** Colour-code the mote by the ore so you can tell diamonds from coal at a glance. */
    private static DustParticleOptions oreColour(BlockState state) {
        String id = state.getBlockHolder().unwrapKey().map(k -> k.location().getPath()).orElse("");
        if (id.contains("diamond")) {
            return dust(0.35F, 0.95F, 0.95F); // cyan
        }
        if (id.contains("emerald")) {
            return dust(0.25F, 0.95F, 0.4F);  // green
        }
        if (id.contains("gold")) {
            return dust(1.0F, 0.85F, 0.2F);   // gold
        }
        if (id.contains("redstone")) {
            return dust(1.0F, 0.2F, 0.2F);    // red
        }
        if (id.contains("lapis")) {
            return dust(0.25F, 0.4F, 1.0F);   // blue
        }
        if (id.contains("copper")) {
            return dust(0.95F, 0.5F, 0.25F);  // orange
        }
        if (id.contains("iron")) {
            return dust(0.9F, 0.8F, 0.7F);    // tan
        }
        if (id.contains("coal")) {
            return dust(0.4F, 0.4F, 0.4F);    // grey
        }
        return dust(0.85F, 0.75F, 0.5F);      // amber default (quartz/other)
    }

    private static DustParticleOptions dust(float r, float g, float b) {
        return new DustParticleOptions(new Vector3f(r, g, b), 1.7F); // bigger motes = far easier to spot
    }
}
