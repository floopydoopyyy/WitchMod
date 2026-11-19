package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * console-style aim assist, for any weapon, at any range. with your crosshair over (or near) a target your view slows
 * down so you don't slide off it, and it moves with the target — follows a strafe, a jump, your own movement past it —
 * so tracking is easy. it never locks on: turn away and it lets go. hostile mobs, angry neutral mobs and other players
 * are always targets; passive and calm neutral mobs only once you've attacked them. it's all your own client
 * ({@code client/AimAssistClient}, off the synced AIM_ASSIST_ACTIVE flag); the server just switches it on.
 */
public final class BlessingAimAssist extends Effect {
    public BlessingAimAssist() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 50, () -> Items.CROSSBOW);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.AIM_ASSIST_ACTIVE, 1);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.AIM_ASSIST_ACTIVE, -1);
    }

    /** you find out the first time you swing or shoot at something with it. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onAttack(ServerPlayer self, Entity target, AttackEntityEvent event) {
        if (target instanceof LivingEntity) {
            markDiscoveredByVictim(self);
        }
    }

    @Override
    public void onProjectileLaunched(ServerPlayer self, Projectile projectile) {
        markDiscoveredByVictim(self);
    }

    @Override
    public String debugForce(ServerPlayer target, @Nullable String arg) {
        if (target.getData(WitchModAttachments.AIM_ASSIST_ACTIVE) < 0) {
            return "aim assist isn't active on " + target.getName().getString() + " (apply it first)";
        }
        markDiscoveredByVictim(target);
        return "aim assist is on (and discovered): put your crosshair on a mob and track it";
    }
}
