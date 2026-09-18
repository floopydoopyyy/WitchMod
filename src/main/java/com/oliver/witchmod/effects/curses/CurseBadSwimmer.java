package com.oliver.witchmod.effects.curses;

import javax.annotation.Nullable;

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
 * you never learned to swim, and water isn't going to hold you up. Liquid stops
 * behaving like liquid: you drop straight through it like it's air, land on the bottom and walk along it.
 * You can't kick your way back up — and you still can't breathe down there.
 *
 * <p>Two attribute modifiers, toggled while you're in liquid, do most of the work with real vanilla physics
 * rather than a velocity hack:
 * <ul>
 *   <li>{@link Attributes#GRAVITY} — vanilla divides gravity by 16 underwater (that's the gentle bob), so
 *       multiplying it back gives air-like sinking.</li>
 *   <li>{@link Attributes#WATER_MOVEMENT_EFFICIENCY} = 1.0 — makes water acceleration and friction lerp to
 *       exactly the LAND values, so you walk normally instead of swimming. Vanilla halves this while
 *       airborne and gives it in full when {@code onGround}, which lands perfectly: you sink, then walk
 *       properly once you're standing on the bottom.</li>
 * </ul>
 *
 * <p><b>Attributes alone can't carry it, though</b>, because vanilla skips fluid gravity ENTIRELY while
 * sprinting — a sprint-swim switched the whole curse off, so there was no middle ground between "can't stay
 * up at all" and "swimming is completely unaffected". The gravity half is therefore deliberately modest, and
 * a constant downward pull applied client-side (see {@code client/ClientCurseHandler}) bites in BOTH states
 * so that swimming is a losing battle rather than an exemption. Entering liquid adds a one-shot yank on top.
 *
 * <p>Because it's ordinary walking on an ordinary floor, stepping up blocks underwater just works via the
 * normal step height — no special casing. Breathing is untouched, so drowning still applies.
 */
public final class CurseBadSwimmer extends Effect {
    private static final ResourceLocation GRAVITY_MODIFIER_ID = EffectUtil.modifierId("curse_bad_swimmer_gravity");
    private static final ResourceLocation WATER_MODIFIER_ID = EffectUtil.modifierId("curse_bad_swimmer_water");
    private static final ResourceLocation STEP_MODIFIER_ID = EffectUtil.modifierId("curse_bad_swimmer_step");

    public CurseBadSwimmer() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.COPPER_INGOT);
    }

    /** you find out the first time you get in water and go straight to the bottom (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.BAD_SWIMMER_ACTIVE, 1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (target.getData(WitchModAttachments.BAD_SWIMMER_ACTIVE) < 0) {
            target.setData(WitchModAttachments.BAD_SWIMMER_ACTIVE, 1); // self-heal after a relog
        }
        if (target.isInWater() || target.isInLava()) {
            sinkLikeAStone(target);
            markDiscoveredByVictim(target);
        } else {
            restoreNormalPhysics(target);
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.BAD_SWIMMER_ACTIVE, -1);
        restoreNormalPhysics(target);
    }

    private static void sinkLikeAStone(ServerPlayer target) {
        AttributeInstance gravity = target.getAttribute(Attributes.GRAVITY);
        if (gravity != null && !gravity.hasModifier(GRAVITY_MODIFIER_ID)) {
            gravity.addOrUpdateTransientModifier(new AttributeModifier(GRAVITY_MODIFIER_ID,
                    Config.BAD_SWIMMER_GRAVITY_MULTIPLIER.get() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        AttributeInstance water = target.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY);
        if (water != null && !water.hasModifier(WATER_MODIFIER_ID)) {
            water.addOrUpdateTransientModifier(new AttributeModifier(WATER_MODIFIER_ID,
                    Config.BAD_SWIMMER_WATER_EFFICIENCY.get(), AttributeModifier.Operation.ADD_VALUE));
        }
        // extra STEP HEIGHT so a single block can be walked up on the bottom.
        //
        // ⚠ Step height, NOT a weaker pull. Underwater there is no impulse jump to boost: once you're
        // submerged past the fluid-jump threshold, vanilla routes jumping to jumpInFluid() — a gentle
        // sustained thrust — so the only way to clear a block by rising is to swim up, which is exactly what
        // this curse exists to forbid. Weakening the pull enough to hop a ledge would also hand back a slow
        // ascent to the surface. Raising the step instead separates the two cleanly: you can WALK over
        // terrain on the bottom, and you still cannot go UP.
        AttributeInstance step = target.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null && !step.hasModifier(STEP_MODIFIER_ID)) {
            step.addOrUpdateTransientModifier(new AttributeModifier(STEP_MODIFIER_ID,
                    Config.BAD_SWIMMER_STEP_BONUS.get(), AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** out of liquid these must come off, or normal falling would be brutal. */
    private static void restoreNormalPhysics(ServerPlayer target) {
        AttributeInstance gravity = target.getAttribute(Attributes.GRAVITY);
        if (gravity != null && gravity.hasModifier(GRAVITY_MODIFIER_ID)) {
            gravity.removeModifier(GRAVITY_MODIFIER_ID);
        }
        AttributeInstance water = target.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY);
        if (water != null && water.hasModifier(WATER_MODIFIER_ID)) {
            water.removeModifier(WATER_MODIFIER_ID);
        }
        AttributeInstance step = target.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null && step.hasModifier(STEP_MODIFIER_ID)) {
            step.removeModifier(STEP_MODIFIER_ID);
        }
    }
}
