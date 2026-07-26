package com.oliver.witchmod.effects.blessings;

import java.util.HashMap;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModMobEffects;

/**
 * You are spiritually tethered to whoever is nearest (master-spec Soul Bond, sacrificial item TOTEM OF
 * UNDYING). The nearest living thing within {@code soulBondRadius} is marked with the {@code Soul Bound}
 * status effect and wreathed in constant golden particles; while bound, it takes {@code soulBondDamageShare}
 * (40%) of every hit YOU take, in your place, and a golden trail flicks out from you to it so everyone sees
 * who paid.
 *
 * <p>The bond continuously re-picks the nearest thing, which is what gives it teeth: in a one-on-one it
 * latches onto your OPPONENT (so hitting you up close feeds 40% back into them), and a pet trailing you into
 * a fight becomes the bound and eats your incoming — so it discourages exactly those two things.
 *
 * <p>This class (running on the caster) owns everything: it maintains the bond, applies/refreshes the marker,
 * and spits the constant particles. The damage split and the golden trail live in {@code BlessingEventHandler}
 * on the caster's damage event; it reads the current bond via {@link #boundEntity}.
 */
public final class BlessingSoulBond extends Effect {
    /** caster -> the entity id it currently has bound, so the bond can move and be cleaned up. */
    private static final Map<UUID, Integer> BOUND = new HashMap<>();

    /** Gold, matching the Totem of Undying and the trail. */
    public static final DustParticleOptions GOLD =
            new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.24F), 1.0F);

    public BlessingSoulBond() {
        super(EffectCategory.BLESSING, EffectCostTier.MODERATE, 63, () -> Items.TOTEM_OF_UNDYING);
    }

    /** The living entity {@code caster} currently has bound, or null if none (out of range / gone). */
    @Nullable
    public static LivingEntity boundEntity(ServerLevel level, ServerPlayer caster) {
        Integer id = BOUND.get(caster.getUUID());
        if (id == null) {
            return null;
        }
        return level.getEntity(id) instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    @Override
    public void onRemove(ServerPlayer target) {
        clearBound(target.serverLevel(), target);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        ServerLevel level = target.serverLevel();

        // The bond STICKS: only re-pick when the current bound is gone or has left the radius entirely, so it
        // doesn't just snap to whoever hit you last (which would make it a worse Thorns). A bound that stays
        // in range keeps the tether however close someone else gets.
        if (ticksRemaining % Config.SOULBOND_REBIND_INTERVAL.get() == 0) {
            LivingEntity current = boundEntity(level, target);
            double radius = Config.SOULBOND_RADIUS.get();
            boolean stillValid = current != null && current.distanceToSqr(target) <= radius * radius;
            if (!stillValid) {
                if (current != null) {
                    current.removeEffect(WitchModMobEffects.SOUL_BOUND);
                }
                LivingEntity nearest = findNearest(level, target);
                if (nearest != null) {
                    BOUND.put(target.getUUID(), nearest.getId());
                } else {
                    BOUND.remove(target.getUUID());
                }
            }
        }

        // Keep the marker refreshed and pour the constant golden particles onto the bound entity.
        LivingEntity bound = boundEntity(level, target);
        if (bound != null) {
            int refresh = Config.SOULBOND_REBIND_INTERVAL.get() + 40;
            bound.addEffect(new MobEffectInstance(WitchModMobEffects.SOUL_BOUND, refresh, 0, false, false, true));
            if (ticksRemaining % Config.SOULBOND_PARTICLE_INTERVAL.get() == 0) {
                emitBondParticles(level, bound);
            }
        }
    }

    @Nullable
    private static LivingEntity findNearest(ServerLevel level, ServerPlayer caster) {
        double radius = Config.SOULBOND_RADIUS.get();
        AABB area = caster.getBoundingBox().inflate(radius);
        return level.getEntitiesOfClass(LivingEntity.class, area, e -> isBindable(e, caster)).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(caster)))
                .orElse(null);
    }

    /** Anything alive that isn't the caster, an armour stand, or a spectator. */
    private static boolean isBindable(LivingEntity entity, ServerPlayer caster) {
        if (entity == caster || !entity.isAlive() || entity instanceof ArmorStand) {
            return false;
        }
        return !(entity instanceof Player player && player.isSpectator());
    }

    private static void emitBondParticles(ServerLevel level, LivingEntity bound) {
        double y = bound.getY() + bound.getBbHeight() * 0.6;
        level.sendParticles(GOLD, bound.getX(), y, bound.getZ(), 3,
                bound.getBbWidth() * 0.5, bound.getBbHeight() * 0.35, bound.getBbWidth() * 0.5, 0.0);
    }

    private static void clearBound(ServerLevel level, ServerPlayer caster) {
        LivingEntity bound = boundEntity(level, caster);
        if (bound != null) {
            bound.removeEffect(WitchModMobEffects.SOUL_BOUND);
        }
        BOUND.remove(caster.getUUID());
    }
}
