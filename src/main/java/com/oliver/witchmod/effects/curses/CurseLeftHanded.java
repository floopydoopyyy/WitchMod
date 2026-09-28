package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * everything's on the wrong side: your held item renders in the opposite hand, and trying to type in chat
 * comes out as letter spam. A small aim wobble is thrown in as an extra coordination issue
 * ({@code leftHandedDriftDegrees}). All client-side off the synced
 * {@link WitchModAttachments#LEFT_HANDED_ACTIVE} flag — see {@code client/ClientCurseHandler}.
 */
public final class CurseLeftHanded extends Effect {
    public CurseLeftHanded() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.SHEARS);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.LEFT_HANDED_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.LEFT_HANDED_ACTIVE, -1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (target.getData(WitchModAttachments.LEFT_HANDED_ACTIVE) < 0) {
            target.setData(WitchModAttachments.LEFT_HANDED_ACTIVE, 1); // self-heal after a relog
        }
    }
}
