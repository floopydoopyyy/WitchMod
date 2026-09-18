package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * shadow-quick (statement blessing, sacrificial item BLACK DYE — Ink Sac is taken by Unseen): you move faster,
 * swing far quicker, and can flip a second jump out of thin air. Every swing snaps out with a sharp woosh,
 * and the double jump kicks up a puff of ninja smoke.
 *
 * <p>Speed + attack-speed are attribute modifiers here (transient, self-healed); the double jump, the swing
 * woosh and the smoke FX are client-side ({@code client/ClientCurseHandler}) off the synced
 * {@link WitchModAttachments#NINJA_ACTIVE} flag, since jumps/animation are client-authoritative.
 */
public final class BlessingNinja extends Effect {
    public static final ResourceLocation SPEED_ID = EffectUtil.modifierId("blessing_ninja_speed");
    public static final ResourceLocation ATTACK_ID = EffectUtil.modifierId("blessing_ninja_attack");

    public BlessingNinja() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 42, () -> Items.BLACK_DYE);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.NINJA_ACTIVE, 1);
        applyModifiers(target);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (target.getData(WitchModAttachments.NINJA_ACTIVE) != 1) {
            target.setData(WitchModAttachments.NINJA_ACTIVE, 1);
        }
        applyModifiers(target); // self-heal the transient modifiers after a death/reload
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.NINJA_ACTIVE, -1);
        EffectUtil.removeModifier(target, Attributes.MOVEMENT_SPEED, SPEED_ID);
        EffectUtil.removeModifier(target, Attributes.ATTACK_SPEED, ATTACK_ID);
    }

    private static void applyModifiers(ServerPlayer target) {
        AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(SPEED_ID)) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED_ID,
                    Config.NINJA_SPRINT_SPEED.get(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        AttributeInstance attack = target.getAttribute(Attributes.ATTACK_SPEED);
        if (attack != null && !attack.hasModifier(ATTACK_ID)) {
            attack.addOrUpdateTransientModifier(new AttributeModifier(ATTACK_ID,
                    Config.NINJA_ATTACK_SPEED.get(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
