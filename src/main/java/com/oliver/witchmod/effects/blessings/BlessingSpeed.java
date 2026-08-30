package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Blessing of Speed (sacrificial item SUGAR): your SPRINT is much faster (~60%) and leaves a cool trailing
 * spark behind you. This is its OWN {@code MOVEMENT_SPEED} modifier applied only while sprinting — NOT the
 * vanilla Speed effect — so it stacks cleanly with Speed potions and other blessings (e.g. Ninja).
 */
public final class BlessingSpeed extends Effect {
    private static final ResourceLocation SPRINT_ID = EffectUtil.modifierId("blessing_speed_sprint");

    public BlessingSpeed() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.SUGAR);
    }

    /** You find out the first time you tear off sprinting — not the instant it lands (preserve the mystery). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.SPEED_ACTIVE, 1); // client cancels the sprint-FOV zoom off this
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.SPEED_ACTIVE, -1);
        EffectUtil.removeModifier(target, Attributes.MOVEMENT_SPEED, SPRINT_ID);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (target.getData(WitchModAttachments.SPEED_ACTIVE) != 1) {
            target.setData(WitchModAttachments.SPEED_ACTIVE, 1);
        }
        AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        if (target.isSprinting()) {
            if (!speed.hasModifier(SPRINT_ID)) {
                speed.addOrUpdateTransientModifier(new AttributeModifier(SPRINT_ID,
                        Config.SPEED_SPRINT_BONUS.get(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            markDiscoveredByVictim(target); // you notice the moment you first tear off sprinting
            trail(target);
        } else if (speed.hasModifier(SPRINT_ID)) {
            speed.removeModifier(SPRINT_ID); // only faster WHILE sprinting; normal walk is untouched
        }
    }

    /** A cool spark/firework streak trailing off the runner. */
    private static void trail(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        Vec3 back = target.position().add(target.getLookAngle().scale(-0.35));
        level.sendParticles(ParticleTypes.FIREWORK, back.x, back.y + 0.9, back.z, 2, 0.15, 0.25, 0.15, 0.02);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, back.x, back.y + 0.5, back.z, 3, 0.2, 0.15, 0.2, 0.03);
    }
}
