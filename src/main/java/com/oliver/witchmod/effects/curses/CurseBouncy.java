package com.oliver.witchmod.effects.curses;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModSounds;
import com.oliver.witchmod.effects.Curses;

/**
 * boing. You're
 * made of rubber:
 * <ul>
 *   <li><b>Fall-immune and you REBOUND</b>, and repeatedly jumping BUILDS height (the movement physics —
 *       floor/wall/ceiling rebounds — run client-side off the synced flag, see {@code ClientCurseHandler.tickBouncy}).</li>
 *   <li><b>Barge into a crowd</b> and they ping off; even WALKING into something gives it a nudge, and sprinting
 *       flings it. Things that sprint into you bounce off too.</li>
 *   <li><b>A built-in "get off me"</b> — anything that melees you bounces straight back.</li>
 * </ul>
 * Custom boing sound (3 variants) supplied.
 */
public final class CurseBouncy extends Effect {
    /** entity uuid -> game tick it may be bounced again (so a pinned entity isn't launched every tick). */
    private static final Map<UUID, Long> BOUNCE_COOLDOWN = new HashMap<>();
    private static final int BOUNCE_COOLDOWN_TICKS = 8;

    private static final net.minecraft.resources.ResourceLocation JUMP_ID =
            com.oliver.witchmod.data.EffectUtil.modifierId("curse_bouncy_jump");

    public CurseBouncy() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 28, () -> Items.SLIME_BALL);
    }

    /** you find out the first time you boing off anything — a floor, a wall, or somebody (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.BOUNCY_ACTIVE, 1);
        applyJump(target);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.BOUNCY_ACTIVE, -1);
        com.oliver.witchmod.data.EffectUtil.removeModifier(target,
                net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH, JUMP_ID);
    }

    /** rubber legs: you spring HIGHER too. A transient JUMP_STRENGTH modifier, re-asserted each tick. */
    private static void applyJump(ServerPlayer target) {
        var jump = target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
        if (jump != null && !jump.hasModifier(JUMP_ID)) {
            jump.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    JUMP_ID, Config.BOUNCY_JUMP_BONUS.get(),
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        applyJump(target); // re-assert the transient jump modifier (survives reload)
        // 2 = the low-gravity synergy is live (client reads it to rebound harder); 1 = plain bouncy.
        target.setData(WitchModAttachments.BOUNCY_ACTIVE,
                com.oliver.witchmod.synergy.Synergies.BOUNCINESS.activeFor(target) ? 2 : 1);
        ServerLevel level = target.serverLevel();
        double horizSpeedSqr = target.getDeltaMovement().horizontalDistanceSqr();
        boolean sprinting = target.isSprinting() && horizSpeedSqr > 0.02 * 0.02;
        boolean walking = horizSpeedSqr > 0.004 * 0.004;
        double collisionSpeedSqr = Config.BOUNCY_COLLISION_SPEED.get() * Config.BOUNCY_COLLISION_SPEED.get();
        double force = Config.BOUNCY_ENTITY_FORCE.get();
        double walkForce = force * Config.BOUNCY_WALK_FORCE_MULT.get();

        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(0.25), e -> e != target && e.isAlive())) {
            boolean otherCharging = closingSpeedSqr(other, target) > collisionSpeedSqr;
            if (sprinting || otherCharging) {
                bounceAway(target, other, force);           // full fling
            } else if (walking) {
                bounceAway(target, other, walkForce);        // a slight nudge just from walking into them
            }
        }
    }

    /** the squared speed at which {@code mover} is closing on {@code toward} (0 if moving away). */
    private static double closingSpeedSqr(LivingEntity mover, LivingEntity toward) {
        Vec3 vel = mover.getDeltaMovement();
        Vec3 dir = toward.position().subtract(mover.position());
        if (dir.lengthSqr() < 1.0E-4) {
            return vel.horizontalDistanceSqr();
        }
        double closing = vel.dot(dir.normalize());
        return closing > 0 ? closing * closing : 0.0;
    }

    /** fling {@code victim} away from {@code source} horizontally (plus a hop), with a boing. Respects a short cooldown. */
    public static void bounceAway(ServerPlayer source, LivingEntity victim, double force) {
        long now = source.serverLevel().getGameTime();
        Long ready = BOUNCE_COOLDOWN.get(victim.getUUID());
        if (ready != null && now < ready) {
            return;
        }
        BOUNCE_COOLDOWN.put(victim.getUUID(), now + BOUNCE_COOLDOWN_TICKS);

        Vec3 away = victim.position().subtract(source.position());
        away = new Vec3(away.x, 0.0, away.z);
        if (away.lengthSqr() < 1.0E-4) {
            Vec3 look = source.getLookAngle();
            away = new Vec3(look.x, 0.0, look.z);
        }
        if (away.lengthSqr() < 1.0E-4) {
            away = new Vec3(0, 0, 1);
        }
        Vec3 push = away.normalize().scale(force).add(0.0, 0.4, 0.0);
        victim.setDeltaMovement(victim.getDeltaMovement().add(push));
        victim.hurtMarked = true;
        boing(source.serverLevel(), victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ());
        Curses.BOUNCY.get().markDiscoveredByVictim(source); // discover on entity bounces too, not just falls
    }

    private static void boing(ServerLevel level, double x, double y, double z) {
        level.playSound(null, x, y, z, WitchModSounds.BOUNCY_BOING.get(), SoundSource.PLAYERS, 0.9F,
                0.9F + level.random.nextFloat() * 0.3F);
        level.sendParticles(ParticleTypes.ITEM_SLIME, x, y + 0.2, z, 8, 0.3, 0.2, 0.3, 0.02);
    }
}
