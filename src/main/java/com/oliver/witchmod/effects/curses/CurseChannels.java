package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * your left and right audio are swapped — sounds come from the wrong side, which is quietly maddening in a
 * fight. Done by mirroring the sound listener's orientation (negating its UP vector flips the derived
 * left/right axis) in a small client mixin ({@code mixin/ListenerMixin}), gated on the synced
 * {@link WitchModAttachments#CHANNELS_ACTIVE} flag. Only positional (in-world) sounds swap; UI/music don't.
 */
public final class CurseChannels extends Effect {
    public CurseChannels() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.NOTE_BLOCK);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.CHANNELS_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.CHANNELS_ACTIVE, -1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (target.getData(WitchModAttachments.CHANNELS_ACTIVE) < 0) {
            target.setData(WitchModAttachments.CHANNELS_ACTIVE, 1); // self-heal after a relog
        }
    }
}
