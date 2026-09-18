package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * sure-footed and forgiving. Parkour is easier:
 * <ul>
 *   <li><b>Coyote time</b> — you can still jump for {@code coyoteTicks} after walking off a ledge.</li>
 *   <li><b>Edge magnetism</b> — while airborne you're gently biased toward ledge edges you'd otherwise just
 *       miss, so jumps land.</li>
 * </ul>
 * Both are client-side off the synced flag (movement is client-authoritative — see
 * {@code ClientCurseHandler.tickCoyote}). The prototype's flat Jump Boost is dropped.
 */
public final class BlessingCoyote extends Effect {
    public BlessingCoyote() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.BEEF);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.COYOTE_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.COYOTE_ACTIVE, -1);
    }
}
