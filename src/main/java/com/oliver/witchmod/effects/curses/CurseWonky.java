package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * you can't walk in a straight line any more.
 *
 * <p>A subtle sideways wander is added to your movement whenever you're actually moving, and amplified while
 * sprinting. It's meant to be quietly disorienting rather than an obvious shove — you keep drifting off the
 * line you're trying to hold and constantly correcting.
 *
 * <p>All the actual work is client-side ({@code ClientCurseHandler}, off the synced
 * {@link WitchModAttachments#WONKY_ACTIVE} flag), because player movement is client-authoritative — a
 * server-side nudge would just be corrected away on the next client tick.
 */
public final class CurseWonky extends Effect {
    public CurseWonky() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 15, () -> Items.FEATHER);
    }

    /** you notice it the moment you try to walk somewhere and can't hold the line (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.WONKY_ACTIVE, 1);
        markDiscoveredByVictim(target);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.WONKY_ACTIVE, -1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (target.getData(WitchModAttachments.WONKY_ACTIVE) < 0) {
            target.setData(WitchModAttachments.WONKY_ACTIVE, 1); // self-heal after a relog
        }
    }
}
