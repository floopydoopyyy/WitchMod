package com.oliver.witchmod.effects.blessings;

import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * Projectiles bounce off you and go home (master-spec Reflect, sacrificial item TURTLE SHELL). An arrow,
 * trident, snowball — anything thrown or fired at you — is caught mid-flight and sent straight back at whoever
 * loosed it, {@code reflectVelocityMultiplier} faster than it came in. It does <b>not</b> home: it's a precise
 * shot at the attacker's position, so they can (and probably will have to) dodge.
 *
 * <p>The actual catch-and-return lives in {@code effects/BlessingEventHandler} on {@code ProjectileImpactEvent}
 * — the one hook that fires the instant a projectile would strike you, before it deals its damage. This class
 * just declares the blessing and defers discovery to the first projectile you turn around.
 */
public final class BlessingReflect extends Effect {
    public BlessingReflect() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.TURTLE_HELMET);
    }

    /** You find out the first time something you're shot with comes straight back. */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }
}
