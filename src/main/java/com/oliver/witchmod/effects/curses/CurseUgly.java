package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Your face is not your own any more (master-spec Ugly). For the duration, every client — including your
 * own — renders you wearing one of the mod's ugly skins instead of yours.
 *
 * <p><b>The server picks a NUMBER, not a skin.</b> It can't pick a skin: they live in a client-side resource
 * folder ({@code assets/witchmod/textures/entity/ugly/}) that the server never sees and that anyone is
 * free to add to. So this rolls a plain random value, and each client reduces it modulo however many skins
 * it can list — which lands every client on the same face for that player without any of them having to
 * agree in advance, and without the server caring how many files exist.
 *
 * <p>All the actual work is client-side in {@code client/UglySkinManager}, which re-asserts the override
 * every tick. That's what makes it survive relogging, dying, changing dimension and other players coming
 * into view later: there's no one-off "apply" moment to miss.
 */
public final class CurseUgly extends Effect {
    public CurseUgly() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.CARVED_PUMPKIN);
    }

    /** Hard to miss, given it's your own face (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public String debugForce(ServerPlayer target, String arg) {
        target.setData(WitchModAttachments.UGLY_SKIN, target.getRandom().nextInt(1 << 20));
        return "rolled a new ugly face";
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        // Non-negative and otherwise arbitrary; the client does the modulo.
        target.setData(WitchModAttachments.UGLY_SKIN, target.getRandom().nextInt(1 << 20));
        markDiscoveredByVictim(target);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.UGLY_SKIN, -1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        // Self-heal: keeps the same face across a relog rather than re-rolling, and restores it if the value
        // was ever lost. The roll only happens when there genuinely isn't one.
        if (target.getData(WitchModAttachments.UGLY_SKIN) < 0) {
            target.setData(WitchModAttachments.UGLY_SKIN, target.getRandom().nextInt(1 << 20));
        }
    }
}
