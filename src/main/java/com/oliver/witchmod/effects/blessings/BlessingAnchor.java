package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/**
 * You're planted — literally immovable by external sources (master-spec Anchor, sacrificial item CHAIN).
 * Nothing knocks you back: not melee, not projectiles, not explosions.
 *
 * <p>Done with the two vanilla knockback-resistance attributes cranked to their maximum: {@code
 * KNOCKBACK_RESISTANCE = 1.0} zeroes attack/melee knockback and {@code EXPLOSION_KNOCKBACK_RESISTANCE = 1.0}
 * zeroes explosion (and wind-charge) knockback. {@code BlessingEventHandler.onAnchorKnockback} also cancels
 * {@code LivingKnockBackEvent} outright as a guarantee for any attack path. The modifiers are TRANSIENT, so
 * {@code onTick} re-applies them if a world reload dropped them (the standard trap — {@code onApply} doesn't
 * re-run on reload).
 */
public final class BlessingAnchor extends Effect {
    private static final ResourceLocation KB_ID = EffectUtil.modifierId("blessing_anchor_kb");
    private static final ResourceLocation EXPLOSION_KB_ID = EffectUtil.modifierId("blessing_anchor_explosion_kb");

    public BlessingAnchor() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.CHAIN);
    }

    /** You find out the first time something that should have shoved you simply... doesn't (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        applyModifiers(target);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        // Self-heal: re-apply if a reload dropped the transient modifiers.
        if (target.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null
                && target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).getModifier(KB_ID) == null) {
            applyModifiers(target);
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeModifier(target, Attributes.KNOCKBACK_RESISTANCE, KB_ID);
        EffectUtil.removeModifier(target, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, EXPLOSION_KB_ID);
    }

    private static void applyModifiers(ServerPlayer target) {
        EffectUtil.addModifier(target, Attributes.KNOCKBACK_RESISTANCE, KB_ID, 1.0,
                AttributeModifier.Operation.ADD_VALUE);
        EffectUtil.addModifier(target, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, EXPLOSION_KB_ID, 1.0,
                AttributeModifier.Operation.ADD_VALUE);
    }
}
