package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * the ground stops gripping you: everything feels like ice, so you slide about and can't stop on a dime. The
 * slide is applied CLIENT-side (movement is client-authoritative) off the synced
 * {@link WitchModAttachments#ICE_SKATES_ACTIVE} flag — see {@code client/ClientCurseHandler}.
 */
public final class CurseIceSkates extends Effect {
    public CurseIceSkates() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.PACKED_ICE);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.ICE_SKATES_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.ICE_SKATES_ACTIVE, -1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (target.getData(WitchModAttachments.ICE_SKATES_ACTIVE) < 0) {
            target.setData(WitchModAttachments.ICE_SKATES_ACTIVE, 1); // self-heal after a relog
        }
    }
}
