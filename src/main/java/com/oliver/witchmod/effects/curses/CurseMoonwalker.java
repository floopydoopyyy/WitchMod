package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * you cannot walk forward. Every other direction works normally — back, both
 * strafes — but W does nothing: the client zeroes any forward movement input while the curse is set (see
 * {@code client/ClientCurseHandler}), off the auto-synced {@link WitchModAttachments#MOONWALKER_ACTIVE} flag,
 * because movement is client-authoritative.
 *
 * <p><b>Halved duration</b> ({@link #durationMultiplier}): being unable to advance is punishing enough that
 * a full 30–60 minutes of it would be miserable, so it's cut in half at every cast path.
 */
public final class CurseMoonwalker extends Effect {
    public CurseMoonwalker() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.END_STONE);
    }

    // discovery stays on apply (the centralised default): it's client-only, so the server can't cleanly see
    // the first blocked W-press, and pressing W and going nowhere gives it away within a second regardless.

    @Override
    public float durationMultiplier() {
        return 0.5F; // half duration override — the spec's "so detrimental" allowance
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.MOONWALKER_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.MOONWALKER_ACTIVE, -1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (target.getData(WitchModAttachments.MOONWALKER_ACTIVE) < 0) {
            target.setData(WitchModAttachments.MOONWALKER_ACTIVE, 1); // self-heal after a relog
        }
    }
}
