package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetCombat.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.ZombieBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the ravager puppet. */
public final class RavagerBehaviour implements PuppetBehaviour {
    public static final RavagerBehaviour INSTANCE = new RavagerBehaviour();

    private RavagerBehaviour() {}

    /** a ravager's bite in flight: the head pokes out a few ticks after the swing, then the hit lands. */
    public record Bite(LivingEntity target, long at) {}

    private static final Map<UUID, Bite> BITES = EffectState.sessionConcurrent("puppeteer/ravager/bites");

    // --- ravager ------------------------------------------------------------------------------------

    /** left-click on something in reach: a ravager's bite doesn't land at once — its head pokes out a moment later. */
    private static void startRavagerBite(ServerPlayer player, LivingEntity target) {
        if (BITES.containsKey(player.getUUID())) {
            return; // one bite in flight at a time — the head-poke paces it
        }
        BITES.put(player.getUUID(), new Bite(target, player.level().getGameTime() + Config.PUPPETEER_RAVAGER_BITE_DELAY_TICKS.get()));
    }

    /**
     * ravager upkeep: lands the delayed bite when its head-poke delay is up, and tramples leaves it pushes through
     * (like a real ravager, gated on mobGriefing).
     */
    private static void tickRavager(ServerPlayer player) {
        Bite bite = BITES.get(player.getUUID());
        if (bite != null && player.level().getGameTime() >= bite.at()) {
            BITES.remove(player.getUUID());
            LivingEntity target = bite.target();
            double reach = PuppetType.RAVAGER.reach() + 2.0; // a little slack — the head pokes out and they may have shifted
            if (target.isAlive() && target.level() == player.level() && player.distanceToSqr(target) <= reach * reach) {
                target.hurt(player.damageSources().playerAttack(player), 1.0F); // onDamageFirst sets the ravager's bite damage
                Vec3 away = target.position().subtract(player.position());
                away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle() : away.normalize();
                double kb = Config.PUPPETEER_RAVAGER_BITE_KNOCKUP.get();
                target.setDeltaMovement(target.getDeltaMovement().add(away.x * kb, kb, away.z * kb)); // tossed up and back
                target.hurtMarked = true;
                ravagerRally(player, target); // the raid piles onto your quarry
            }
        }
        if (player.horizontalCollision && player.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) {
            trampleLeaves(player);
        }
    }

    /** break any leaves the ravager is pushing through (vanilla's own ravager behaviour). */
    private static void trampleLeaves(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        net.minecraft.world.phys.AABB box = player.getBoundingBox().inflate(0.2);
        for (BlockPos pos : BlockPos.betweenClosed((int) Math.floor(box.minX), (int) Math.floor(box.minY), (int) Math.floor(box.minZ),
                (int) Math.floor(box.maxX), (int) Math.floor(box.maxY), (int) Math.floor(box.maxZ))) {
            if (level.getBlockState(pos).is(net.minecraft.tags.BlockTags.LEAVES)) {
                level.destroyBlock(pos, true, player);
            }
        }
    }

    /**
     * ravager: let go of a charged ROAR — everything around you is flung away and hurt, scaled by how long you charged
     * (your view shook and zoomed as it built). the ravager's own roar sound + animation.
     */
    private static void ravagerRoar(ServerPlayer player, int charge) {
        if (!ready(player)) {
            return;
        }
        int full = Config.PUPPETEER_RAVAGER_ROAR_CHARGE_TICKS.get();
        float frac = Mth.clamp(charge / (float) full, 0.0F, 1.0F);
        if (charge < full / 4) {
            return; // too brief to be a roar — nothing spent
        }
        ServerLevel level = player.serverLevel();
        // vanilla's roar (Ravager.roar / strongKnockback), just much harder: the closer you stand, the further you go
        double radius = Config.PUPPETEER_RAVAGER_ROAR_RADIUS.get();
        double kb = 4.0 * Config.PUPPETEER_RAVAGER_ROAR_KNOCKBACK.get() * frac;
        float dmg = Config.PUPPETEER_RAVAGER_ROAR_DAMAGE.get().floatValue() * frac;
        int stun = Math.round(Config.PUPPETEER_RAVAGER_ROAR_STUN_TICKS.get() * frac);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> e != player && e.isAlive() && !e.isSpectator())) {
            if (e instanceof net.minecraft.world.entity.raid.Raider) {
                continue; // a ravager never hurts its own — pillagers, vindicators, evokers, witches, other ravagers
            }
            if (dmg > 0.0F) {
                SPECIAL_DAMAGE.add(player.getUUID());
                try {
                    e.hurt(player.damageSources().playerAttack(player), dmg);
                } finally {
                    SPECIAL_DAMAGE.remove(player.getUUID());
                }
            }
            double dx = e.getX() - player.getX();
            double dz = e.getZ() - player.getZ();
            double d2 = Math.max(dx * dx + dz * dz, 0.001);
            double resist = Math.max(0.0, 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            Vec3 push = new Vec3(dx / d2 * kb, 0.0, dz / d2 * kb);
            double cap = Config.PUPPETEER_RAVAGER_ROAR_MAX_PUSH.get();
            if (push.length() > cap) {
                push = push.normalize().scale(cap); // right on top of you vanilla's formula goes wild
            }
            e.push(push.x * resist, 0.2 + 0.2 * frac * resist, push.z * resist);
            e.hurtMarked = true;
            if (stun > 0) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stun, 1, false, false));
            }
        }
        // vanilla's puff of breath
        Vec3 c = player.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 40, 0.0, 0.0, 0.0, 0.2);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.5F, 1.0F);
        level.gameEvent(player, net.minecraft.world.level.gameevent.GameEvent.ENTITY_ACTION, c);
        // nudge the action counter so every client plays the dummy's roar animation
        player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
        cooldown(player, Config.PUPPETEER_RAVAGER_ROAR_COOLDOWN_TICKS.get());
    }

    /** nearby pillagers/illagers pile onto whatever the ravager just bit — a soft rally (no glow, don't override a target). */
    private static void ravagerRally(ServerPlayer player, LivingEntity prey) {
        double r = Config.PUPPETEER_RAVAGER_RALLY_RADIUS.get();
        if (r <= 0.0) {
            return;
        }
        for (net.minecraft.world.entity.monster.AbstractIllager illager : player.serverLevel().getEntitiesOfClass(
                net.minecraft.world.entity.monster.AbstractIllager.class, player.getBoundingBox().inflate(r),
                i -> i.isAlive() && (i.getTarget() == null || !i.getTarget().isAlive()))) {
            illager.setTarget(prey);
        }
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        Holds.tickHold(player, type); // the roar's charge (right-click held)
        Puppets.chargeSlow(player, Holds.HELD.contains(player.getUUID()) && player.getData(WitchModAttachments.PUPPET_FUSE) > 0,
                Config.PUPPETEER_RAVAGER_ROAR_CHARGE_SLOW.get()); // it plants itself to bellow
        tickRavager(player);
    }

    /** the building growl. */
    @Override
    public void chargeStarted(ServerPlayer player, PuppetType type) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.RAVAGER_AMBIENT, SoundSource.PLAYERS, 1.2F, 0.6F);
    }

    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        ravagerRoar(player, charge);
    }

    /** the bite is delayed: the instant hit is cancelled and scheduled for when the head pokes out. */
    @Override
    public boolean takesOverMelee(ServerPlayer player, PuppetType type, Entity target) {
        if (target instanceof LivingEntity victim) {
            startRavagerBite(player, victim);
            return true;
        }
        return false;
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        BITES.remove(player.getUUID());
    }
}
