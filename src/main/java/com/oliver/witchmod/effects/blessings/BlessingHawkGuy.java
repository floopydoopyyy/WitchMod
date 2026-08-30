package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * A dead eye (master-spec Hawk Guy — renamed from the prototype "Locked In", id {@code locked_in} →
 * {@code hawk_guy}, since display names derive from the id path; sacrificial item TARGET BLOCK). Every
 * projectile you loose subtly HOMES on whoever you were aiming at: the instant it's fired a cone raycast from
 * you picks the intended target, and the projectile then bends toward it each tick. Arrows, crossbow bolts —
 * and, for fun, crossbow FIREWORKS — all track.
 *
 * <p>The acquisition + steering live in {@code ProjectileBlessingHandler}; the mark is stored on the
 * projectile itself ({@code WitchModAttachments.HAWKGUY_TARGET}). The old prototype's flat Haste effect is
 * dropped — the spec is homing projectiles, not mining speed.
 */
public final class BlessingHawkGuy extends Effect {
    public BlessingHawkGuy() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 42, () -> Items.TARGET);
    }

    /** You find out the first time one of your shots curves onto a target (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }
}
