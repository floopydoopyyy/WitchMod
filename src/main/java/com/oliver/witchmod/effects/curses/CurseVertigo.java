package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * heights get to you: the higher you climb, the wobblier the camera gets. Purely a client-side view wobble
 * (it never changes where you're actually aiming), scaled by altitude, off the synced
 * {@link WitchModAttachments#VERTIGO_ACTIVE} flag — see {@code client/ClientCurseHandler}.
 */
public final class CurseVertigo extends Effect {
    public CurseVertigo() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.SCAFFOLDING);
    }

    /** discovered when the wobble first bites — i.e. the first time you're up high enough to feel it. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.VERTIGO_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.VERTIGO_ACTIVE, -1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (target.getData(WitchModAttachments.VERTIGO_ACTIVE) < 0) {
            target.setData(WitchModAttachments.VERTIGO_ACTIVE, 1); // self-heal after a relog
        }
        // above the camera-sway altitude the client is swaying — that's the discovery moment (the server
        // mirrors the client's altitude test rather than being told).
        if (target.getY() > Config.VERTIGO_CAMERA_START_Y.get()) {
            markDiscoveredByVictim(target);
        }
    }
}
