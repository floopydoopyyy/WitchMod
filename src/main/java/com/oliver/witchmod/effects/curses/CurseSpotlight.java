package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * you're always in the spotlight: a permanent {@link MobEffects#GLOWING} outline, a subtle beam of light
 * pouring down from ABOVE you (starting {@code spotlightBeamGap} up so it never blocks your view), and nearby
 * hostiles spot you more easily. Simple and constant — you can't hide.
 *
 * <p>The mob-detection boost is a transient FOLLOW_RANGE modifier with its OWN id, so it STACKS additively
 * with other curses that boost detection (e.g. Flat Footed) rather than clobbering them.
 */
public final class CurseSpotlight extends Effect {
    /** distinct from Flat Footed's id so the two follow-range boosts stack. */
    private static final ResourceLocation DETECTION_ID = EffectUtil.modifierId("curse_spotlight_detection");

    public CurseSpotlight() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 20, () -> Items.GLOWSTONE);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.SPOTLIGHT_ACTIVE, 1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        // re-assert the glow + client-render flag every tick (short glow duration so it lapses cleanly).
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20, 0, false, false, false));
        if (target.getData(WitchModAttachments.SPOTLIGHT_ACTIVE) < 0) {
            target.setData(WitchModAttachments.SPOTLIGHT_ACTIVE, 1); // self-heal after a relog
        }
        if (target.level() instanceof ServerLevel) {
            amplifyMobHearing(target);
        }
        // the light PILLAR is drawn CLIENT-side (see client/ClientCurseHandler.tickSpotlight) off the synced
        // SPOTLIGHT_ACTIVE flag, so a constant particle helix never floods the server with packets.
    }

    /** nearby hostiles get extra FOLLOW_RANGE (its own modifier id, so it stacks with e.g. Flat Footed). */
    private static void amplifyMobHearing(ServerPlayer target) {
        double radius = Config.SPOTLIGHT_DETECTION_RADIUS.get();
        double bonus = Config.SPOTLIGHT_DETECTION_BONUS.get();
        if (bonus <= 0.0 || radius <= 0.0) {
            return;
        }
        double radiusSq = radius * radius;
        AABB area = target.getBoundingBox().inflate(radius * 1.5);
        for (Mob mob : target.serverLevel().getEntitiesOfClass(Mob.class, area, m -> m instanceof Enemy && m.isAlive())) {
            AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (followRange == null) {
                continue;
            }
            boolean inRange = mob.distanceToSqr(target) <= radiusSq;
            if (inRange && !followRange.hasModifier(DETECTION_ID)) {
                followRange.addOrUpdateTransientModifier(new AttributeModifier(DETECTION_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
            } else if (!inRange && followRange.hasModifier(DETECTION_ID)) {
                followRange.removeModifier(DETECTION_ID);
            }
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.removeEffect(MobEffects.GLOWING);
        target.setData(WitchModAttachments.SPOTLIGHT_ACTIVE, -1);
    }

    @Override
    public @Nullable String debugForce(ServerPlayer target, @Nullable String arg) {
        onTick(target, 0);
        return "Spotlight is on " + target.getName().getString() + ".";
    }
}
