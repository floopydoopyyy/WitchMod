package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.PacingManager;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.Curses;
import com.oliver.witchmod.data.EffectManager;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import com.oliver.witchmod.WitchMod;

/**
 * every big moment gets a One Piece-style dramatic beat. The trigger
 * chance RAMPS the longer it's gone without a moment — high right when the curse lands, resetting each time
 * one fires, and firing on a non-combat tick if it maxes out. The moment is a time-stop (everyone involved
 * frozen + briefly invulnerable) with the victim's camera hijacked for cinematic cuts. All the logic lives
 * in {@link PacingManager} (server) + {@code client/ClientCurseHandler} (the camera), coordinated through
 * {@link WitchModAttachments#PACING_END_TICK} (client window) and {@code PACING_CHARGE_START} (the ramp).
 * This class just declares cost/item and wires the ramp init / teardown.
 */
@EventBusSubscriber(modid = WitchMod.MODID)
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

    /** you find out the first time a dramatic moment seizes your screen (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        PacingManager.onRemove(target); // dismiss any dramatic moment in progress + clear the camera focus
    }

    @Override
    public java.util.Optional<net.minecraft.network.chat.Component> scryingDetail(ServerPlayer target) {
        boolean midMoment = target.getData(com.oliver.witchmod.data.WitchModAttachments.PACING_END_TICK) > target.level().getGameTime();
        return java.util.Optional.of(net.minecraft.network.chat.Component.translatable(
                midMoment ? "witchmod.scry.pacing.now" : "witchmod.scry.pacing.cooldown"));
    }

    /** landing a hit rolls the ramped chance (see PacingManager). */
    @Override
    public void onHurtOther(ServerPlayer self, LivingEntity victim, LivingIncomingDamageEvent event) {
        PacingManager.onHit(self);
    }

    /** restore entities whose time-stop expired, and let a maxed-out charge auto-fire without a hit. */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        PacingManager.tickFreeze(event.getServer());
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (EffectManager.isActive(player, Curses.PACING)) {
                PacingManager.tickCharge(player);
            }
        }
    }
}
