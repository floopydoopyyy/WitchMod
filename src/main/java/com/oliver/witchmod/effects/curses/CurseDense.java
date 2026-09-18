package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * dense (Iron Block) — the MERGER of the old Heavy and Heavyweight curses (both retired). You're crushingly
 * heavy in every sense: you fall faster and crater the ground on a hard landing (Heavy), AND any floor with
 * air beneath it gives way under your weight when you loiter on it (Heavyweight).
 *
 * <p>Implemented by DELEGATING to the two original curse classes (kept only as behaviour holders, no longer
 * registered) — {@link CurseHeavy} and {@link CurseHeavyweight}. Their client flags/attachments
 * ({@code HEAVY_ACTIVE}, {@code HEAVYWEIGHT_SHAKE_END}) and their config keys ({@code heavy*} /
 * {@code heavyweight*}) are still what drive them, so nothing else moves; their internal discovery calls were
 * pointed at {@code Curses.DENSE} so the merged curse is what gets discovered.
 */
public final class CurseDense extends Effect {
    private final CurseHeavy heavy = new CurseHeavy();
    private final CurseHeavyweight heavyweight = new CurseHeavyweight();

    public CurseDense() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 30, () -> Items.IRON_BLOCK);
    }

    @Override
    public boolean discoversOnTrigger() {
        return true; // both halves discover on their first trigger, via Curses.DENSE
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        heavy.onApply(target, caster, durationTicks);
        heavyweight.onApply(target, caster, durationTicks);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        heavy.onTick(target, ticksRemaining);
        heavyweight.onTick(target, ticksRemaining);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        heavy.onRemove(target);
        heavyweight.onRemove(target);
    }
}
