package com.oliver.witchmod.blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * drives the bell's swing + rung/inactive state (mirrors vanilla {@code BellBlockEntity}). the greyed render
 * mirrors the dimension-wide {@link AmethystBellData} clock (the ring authority) via a persisted+synced field.
 * also runs the client shockwave/burst and, per caught player, the consume ramp + camera shake before
 * {@link AmethystBellEffects} resolves the gamble.
 */
public final class AmethystBellBlockEntity extends BlockEntity {
    private static final int SHOCKWAVE_TICKS = 16;

    /** A caught player and the fate the bell rolled for them at ring time. */
    public record Pending(UUID id, AmethystBellEffects.Outcome outcome) {}

    public int ticks;
    public boolean shaking;
    public Direction clickDirection = Direction.NORTH;

    // Rung/inactive state (persisted + synced for the greyed render).
    private long inactiveUntil = 0L;

    // Transient FX + scheduling (short-lived — not worth persisting across a reload).
    private int shockwaveTick = -1;
    private long ringTick = 0L;
    private List<Pending> pending = List.of();

    public AmethystBellBlockEntity(BlockPos pos, BlockState state) {
        super(WitchModBlockEntities.AMETHYST_BELL.get(), pos, state);
    }

    /** Start (or restart) the swing toward {@code direction}, and kick off the client shockwave. Fired on BOTH
     * sides via the ring block-event, so the client (which renders the shockwave) starts it too. */
    public void onHit(Direction direction) {
        this.clickDirection = direction;
        if (this.shaking) {
            this.ticks = 0;
        }
        this.shaking = true;
        this.shockwaveTick = 0;
    }

    /** Whether the bell is greying-out/unresponsive right now (client-safe: uses the synced field). */
    public boolean isInactive() {
        return level != null && level.getGameTime() < inactiveUntil;
    }

    public long inactiveUntil() {
        return inactiveUntil;
    }

    /** Set the greyed field directly (used to reconcile a fresh block entity with the SavedData authority). */
    public void setInactiveUntil(long until) {
        this.inactiveUntil = until;
        setChanged();
        sync();
    }

    /** Server: a successful ring — grey out for 30 min, start the shockwave, and schedule the per-player ramp. */
    public void ringActivate(List<Pending> caught) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        long now = sl.getGameTime();
        this.inactiveUntil = now + Config.BELL_INACTIVE_TICKS.get();
        this.ringTick = now;
        this.pending = new ArrayList<>(caught);
        setChanged();
        sync();
    }

    private void sync() {
        if (level instanceof ServerLevel sl) {
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AmethystBellBlockEntity be) {
        if (be.shaking) {
            be.ticks++;
            if (be.ticks >= 50) {
                be.shaking = false;
                be.ticks = 0;
            }
        }
        // Expanding pink shockwave + central burst — CLIENT-rendered (addParticle no-ops on the server), so
        // the heaviest, most-repeated particle work never touches the server.
        if (be.shockwaveTick >= 0) {
            if (be.shockwaveTick == 0) {
                be.spawnCentralBurst(level);
            }
            be.spawnShockwaveRing(level, be.shockwaveTick);
            be.shockwaveTick++;
            if (be.shockwaveTick > SHOCKWAVE_TICKS) {
                be.shockwaveTick = -1;
            }
        }
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        // Mirror the dimension-wide recharge clock so EVERY bell greys/un-greys together, not just the rung one
        // (throttled + only re-synced on a change). AmethystBellData is the ring authority; this is render-only.
        if ((sl.getGameTime() & 15L) == 0L) {
            long dim = AmethystBellData.get(sl).until();
            if (be.inactiveUntil != dim) {
                be.setInactiveUntil(dim);
            }
        }
        // Server: drive the ramping camera shake and resolve the pre-decided gamble. The consume DUST is
        // client-rendered off the synced AMETHYST_CONSUME_* attachments (set by the block on ring) — no
        // particle packets are sent here.
        if (!be.pending.isEmpty()) {
            long now = sl.getGameTime();
            long elapsed = now - be.ringTick;
            List<Pending> still = new ArrayList<>();
            for (Pending pd : be.pending) {
                boolean fizzle = pd.outcome() == AmethystBellEffects.Outcome.FIZZLE;
                int duration = fizzle ? AmethystBellEffects.fizzleRampTicks() : AmethystBellEffects.applyRampTicks();
                float progress = Mth.clamp(elapsed / (float) duration, 0F, 1F);
                ServerPlayer p = sl.getServer().getPlayerList().getPlayer(pd.id());
                if (p != null) {
                    int win = Math.max(1, Math.round(Config.BELL_SHAKE_TICKS.get() * progress * (fizzle ? 0.5F : 1.0F)));
                    p.setData(WitchModAttachments.AMETHYST_BELL_SHAKE_END, now + win);
                }
                if (elapsed >= duration) {
                    if (p != null) {
                        AmethystBellEffects.apply(sl, p, pd.outcome());
                        p.setData(WitchModAttachments.AMETHYST_CONSUME_END, 0L);
                        p.setData(WitchModAttachments.AMETHYST_CONSUME_FIZZLE, 0);
                    }
                } else {
                    still.add(pd);
                }
            }
            be.pending = still;
        }
    }

    /** One-shot central burst on ring — client-rendered. */
    private void spawnCentralBurst(Level level) {
        double cx = worldPosition.getX() + 0.5, cy = worldPosition.getY() + 0.85, cz = worldPosition.getZ() + 0.5;
        level.addParticle(ParticleTypes.FLASH, cx, cy, cz, 0, 0, 0);
        net.minecraft.util.RandomSource r = level.getRandom();
        for (int i = 0; i < 40; i++) {
            level.addParticle(AmethystBellEffects.AMETHYST, cx + (r.nextDouble() - 0.5) * 0.7, cy + 0.1 + (r.nextDouble() - 0.5) * 0.8,
                    cz + (r.nextDouble() - 0.5) * 0.7, 0, 0.02, 0);
            level.addParticle(AmethystBellEffects.PINK, cx + (r.nextDouble() - 0.5) * 0.8, cy + 0.1 + (r.nextDouble() - 0.5) * 0.9,
                    cz + (r.nextDouble() - 0.5) * 0.8, 0, 0.05, 0);
        }
        for (int i = 0; i < 32; i++) {
            double a = i / 32.0 * Math.PI * 2;
            level.addParticle(ParticleTypes.WITCH, cx + Math.cos(a) * 0.45, cy, cz + Math.sin(a) * 0.45,
                    Math.cos(a) * 0.28, 0.08, Math.sin(a) * 0.28);
        }
    }

    private void spawnShockwaveRing(Level level, int t) {
        double cx = worldPosition.getX() + 0.5;
        double cy = worldPosition.getY() + 0.25;
        double cz = worldPosition.getZ() + 0.5;
        double radius = 0.6 + t * 0.95;
        int points = (int) (radius * 6) + 8;
        for (int i = 0; i < points; i++) {
            double a = i / (double) points * Math.PI * 2;
            double x = cx + Math.cos(a) * radius;
            double z = cz + Math.sin(a) * radius;
            level.addParticle(AmethystBellEffects.PINK, x, cy, z, 0.0, 0.02, 0.0);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("InactiveUntil", inactiveUntil);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inactiveUntil = tag.getLong("InactiveUntil");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putLong("InactiveUntil", inactiveUntil);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
