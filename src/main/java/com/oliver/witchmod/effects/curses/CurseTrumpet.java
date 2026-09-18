package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * A cartoon fat-trumpet scores your every step. A looping
 * "big character walking in" trumpet plays whenever you MOVE, cuts out the instant you stop, speeds up
 * slightly while you sprint, and — the counterplay — goes completely silent while you crouch. The joke is
 * the old cartoon gag of a large character entering to a trumpet; in play it means you give your position
 * away to anyone in earshot unless you move quietly.
 *
 * <p><b>All the audio lives client-side.</b> The server only owns a synced {@code TRUMPET_ACTIVE} flag; the
 * loop itself is a looping tickable {@code SoundInstance} that each nearby client spins up at the cursed
 * player's position (see {@code client/TrumpetSoundManager}/{@code TrumpetSoundInstance}). That's what makes
 * it start/stop <i>instantly</i> and loop seamlessly — a fire-and-forget {@code playSound} can neither be
 * stopped mid-blare nor kept perfectly in sync with the loop. The flag is synced to trackers (not just the
 * owner), so everybody in range hears it, not only the victim.
 *
 * <p>Discovery is on apply: the audio is client-authoritative, so the server can't cleanly catch the first
 * note, and a trumpet blaring the moment you take a step gives itself away instantly anyway.
 */
public final class CurseTrumpet extends Effect {
    public CurseTrumpet() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 15, () -> Items.COOKIE);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.TRUMPET_ACTIVE, 1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        // self-heal: the flag is transient-feeling for the client, but the curse itself persists across a
        // relog/world reload where onApply never re-runs — re-assert it so the music doesn't go silent.
        if (target.getData(WitchModAttachments.TRUMPET_ACTIVE) != 1) {
            target.setData(WitchModAttachments.TRUMPET_ACTIVE, 1);
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.TRUMPET_ACTIVE, -1);
    }
}
