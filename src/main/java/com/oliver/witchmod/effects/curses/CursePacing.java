package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.PacingManager;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Every big moment gets a One Piece-style dramatic beat (master-spec Pacing, refactored). The trigger
 * chance RAMPS the longer it's gone without a moment — high right when the curse lands, resetting each time
 * one fires, and firing on a non-combat tick if it maxes out. The moment is a time-stop (everyone involved
 * frozen + briefly invulnerable) with the victim's camera hijacked for cinematic cuts. All the logic lives
 * in {@link PacingManager} (server) + {@code client/ClientCurseHandler} (the camera), coordinated through
 * {@link WitchModAttachments#PACING_END_TICK} (client window) and {@code PACING_CHARGE_START} (the ramp).
 * This class just declares cost/item and wires the ramp init / teardown.
 */
public final class CursePacing extends Effect {
    public CursePacing() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 17, () -> Items.TROPICAL_FISH);
    }

    @Override
    public String debugForce(ServerPlayer target, String arg) {
        PacingManager.debugTrigger(target);
        return "a dramatic time-stop moment begins";
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        PacingManager.onApply(target); // pre-charge the ramp so the chance starts high
    }

    /** You find out the first time a dramatic moment seizes your screen (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        PacingManager.onRemove(target); // dismiss any dramatic moment in progress + clear the camera focus
    }

    @Override
    public java.util.Optional<String> scryingDetail(ServerPlayer target) {
        boolean midMoment = target.getData(com.oliver.witchmod.data.WitchModAttachments.PACING_END_TICK) > target.level().getGameTime();
        return java.util.Optional.of(midMoment ? "a dramatic moment now" : "moment on cooldown");
    }
}
