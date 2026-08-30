package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * Speed Demon (Carrot on a Stick — ⚠ Lightning Rod was requested but is Comic Relief's item): any LIVING
 * mount you ride moves twice as fast, via a transient {@code MOVEMENT_SPEED} MULTIPLY_TOTAL modifier applied
 * while you're aboard and stripped the moment you get off (or the blessing ends).
 *
 * <p>Boats and minecarts have no movement-speed attribute (they're driven by rider input, not AI), so they're
 * not covered here — a best-effort limitation noted for a later pass.
 */
public final class BlessingSpeedDemon extends Effect {
    private static final ResourceLocation MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "speed_demon_mount");
    /** rider UUID -> the mount entity id we last boosted, so we can strip it when they change/leave mounts. */
    private static final Map<UUID, Integer> BOOSTED = new HashMap<>();

    public BlessingSpeedDemon() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.CARROT_ON_A_STICK);
    }

    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(com.oliver.witchmod.data.WitchModAttachments.SPEED_DEMON_ACTIVE, 1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (target.getData(com.oliver.witchmod.data.WitchModAttachments.SPEED_DEMON_ACTIVE) != 1) {
            target.setData(com.oliver.witchmod.data.WitchModAttachments.SPEED_DEMON_ACTIVE, 1); // self-heal after a relog
        }
        Entity vehicle = target.getVehicle();

        // Minecarts are server-authoritative, but their delta is clamped to a hardcoded max speed — so nudge
        // them an EXTRA (mult-1)× their travel each tick via a second move(), which the clamp doesn't touch.
        if (vehicle instanceof net.minecraft.world.entity.vehicle.AbstractMinecart cart) {
            net.minecraft.world.phys.Vec3 v = cart.getDeltaMovement();
            double horiz = Math.hypot(v.x, v.z);
            if (horiz > 0.01) {
                double extra = Config.SPEED_DEMON_MOUNT_MULTIPLIER.get() - 1.0;
                cart.move(net.minecraft.world.entity.MoverType.SELF, new net.minecraft.world.phys.Vec3(v.x, 0, v.z).scale(extra));
                speedParticles(target.serverLevel(), cart);
                markDiscoveredByVictim(target);
            }
        }

        LivingEntity mount = vehicle instanceof LivingEntity le ? le : null;
        Integer boostedId = BOOSTED.get(target.getUUID());

        // Left the mount (or swapped to a new one / a non-living one) — strip the old boost.
        if (boostedId != null && (mount == null || mount.getId() != boostedId)) {
            stripById(target.serverLevel(), boostedId);
            BOOSTED.remove(target.getUUID());
        }
        if (mount == null) {
            return;
        }
        AttributeInstance attr = mount.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) {
            return; // this mount doesn't move on an attribute (shouldn't happen for a LivingEntity, but guard)
        }
        if (attr.getModifier(MODIFIER_ID) == null) {
            double bonus = Config.SPEED_DEMON_MOUNT_MULTIPLIER.get() - 1.0; // MULTIPLY_TOTAL: ×(1+bonus)
            attr.addTransientModifier(new AttributeModifier(MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            BOOSTED.put(target.getUUID(), mount.getId());
            markDiscoveredByVictim(target);
        }
        speedParticles(target.serverLevel(), mount);
    }

    /** A speed trail off a boosted mount — sparks + cloud, only while it's actually moving. */
    private static void speedParticles(ServerLevel level, Entity mount) {
        if (mount.getDeltaMovement().horizontalDistanceSqr() < 0.004) {
            return; // not moving fast enough to trail
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mount.getX(), mount.getY() + 0.2, mount.getZ(), 4, 0.3, 0.1, 0.3, 0.02);
        level.sendParticles(ParticleTypes.CLOUD, mount.getX(), mount.getY() + 0.1, mount.getZ(), 3, 0.3, 0.05, 0.3, 0.02);
        if (level.random.nextInt(3) == 0) {
            level.sendParticles(ParticleTypes.FIREWORK, mount.getX(), mount.getY() + 0.4, mount.getZ(), 2, 0.25, 0.1, 0.25, 0.05);
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(com.oliver.witchmod.data.WitchModAttachments.SPEED_DEMON_ACTIVE, -1);
        Integer boostedId = BOOSTED.remove(target.getUUID());
        if (boostedId != null && target.level() instanceof ServerLevel level) {
            stripById(level, boostedId);
        }
    }

    private static void stripById(@Nullable ServerLevel level, int mountId) {
        if (level == null) {
            return;
        }
        if (level.getEntity(mountId) instanceof LivingEntity mount) {
            AttributeInstance attr = mount.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attr != null) {
                attr.removeModifier(MODIFIER_ID);
            }
        }
    }
}
