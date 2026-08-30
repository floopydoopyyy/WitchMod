package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * You're a cow now (sacrificial item LEATHER). Right-clicking nothing with an empty bucket milks YOURSELF into
 * a milk bucket, and other players can milk you the same way (right-click you with an empty bucket). That's the
 * whole blessing — no other use. The milking is handled in {@code BlessingEventHandler} (a right-click-item hook
 * for self-milking and an entity-interact hook for being milked).
 */
public final class BlessingCow extends Effect {
    public BlessingCow() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 21, () -> Items.LEATHER);
    }

    /** Discovered the first time you actually get milked. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        // No persistent state — everything is event-driven.
    }
}
