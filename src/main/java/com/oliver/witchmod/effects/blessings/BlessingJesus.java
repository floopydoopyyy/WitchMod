package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * The water holds you up (master-spec Jesus, sacrificial item LILY PAD): you walk on the surface of water,
 * and CROUCHING drops you under it. Water only — not lava (JESUS_WORKS_ON_LAVA=false).
 *
 * <p>The actual surface-walking is applied CLIENT-side (player movement is client-authoritative) off the
 * synced {@link WitchModAttachments#JESUS_ACTIVE} flag — see {@code ClientCurseHandler.tickJesus}. The
 * local player's resulting position syncs back up, so other players see you stroll across the water.
 */
public final class BlessingJesus extends Effect {
    public BlessingJesus() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.LILY_PAD);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.JESUS_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.JESUS_ACTIVE, -1);
    }
}
