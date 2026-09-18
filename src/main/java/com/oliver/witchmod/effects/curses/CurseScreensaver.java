package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * hahaha funny ping pong
 * maths sucks!!!!!!!
 */
public final class CurseScreensaver extends Effect {
    public CurseScreensaver() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 15, () -> Items.PAINTING);
    }

    @Override
    public String debugForce(ServerPlayer target, String arg) {
        target.setData(WitchModAttachments.SCREENSAVER_ACTIVE, 1);
        return "active — the client runs the DVD-bounce episodes on its own schedule (episodic, client-timed)";
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.SCREENSAVER_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.SCREENSAVER_ACTIVE, -1);
    }

    @Override
    public java.util.Optional<String> scryingDetail(ServerPlayer target) {
        // the bounce episodes are timed client-side, so the server only knows the curse is running.
        return java.util.Optional.of("episodes come and go");
    }
}
