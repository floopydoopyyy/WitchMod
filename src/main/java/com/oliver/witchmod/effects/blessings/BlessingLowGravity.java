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

/**
 * gravity takes it easy on you — you are constantly LIGHT: big floaty hops and soft, slow descents, the whole time.
 *
 * <p>Done with real vanilla physics rather than Jump Boost / Slow Falling: a {@code GRAVITY} multiplier does
 * the actual moon-walk feel (you rise higher on the same jump and fall slower, exactly like reduced gravity),
 * a {@code JUMP_STRENGTH} multiplier launches you higher, and fall damage is cut on {@code LivingFallEvent}
 * (see {@code BlessingEventHandler}). Both modifiers are TRANSIENT, so {@link #onTick} re-applies them if a
 * reload dropped one while the blessing persisted. Discovered on apply — you feel it the instant you move.
 */
public final class BlessingLowGravity extends Effect {
    public static final ResourceLocation GRAVITY_ID = EffectUtil.modifierId("blessing_low_gravity");
    public static final ResourceLocation JUMP_ID = EffectUtil.modifierId("blessing_low_gravity_jump");

    public BlessingLowGravity() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.ENDER_EYE);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        applyModifiers(target);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        applyModifiers(target); // self-heal after a reload/respawn drops the transient modifiers
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeModifier(target, Attributes.GRAVITY, GRAVITY_ID);
        EffectUtil.removeModifier(target, Attributes.JUMP_STRENGTH, JUMP_ID);
    }

    private static void applyModifiers(ServerPlayer target) {
        AttributeInstance gravity = target.getAttribute(Attributes.GRAVITY);
        if (gravity != null && !gravity.hasModifier(GRAVITY_ID)) {
            gravity.addOrUpdateTransientModifier(new AttributeModifier(GRAVITY_ID,
                    Config.LOW_GRAVITY_GRAVITY_MULT.get() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        AttributeInstance jump = target.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null && !jump.hasModifier(JUMP_ID)) {
            jump.addOrUpdateTransientModifier(new AttributeModifier(JUMP_ID,
                    Config.LOW_GRAVITY_JUMP_MULT.get() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    public static float fallDamageMultiplier() {
        return Config.LOW_GRAVITY_FALL_DAMAGE_MULT.get().floatValue();
    }
}
