package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.FishBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the spider puppet. */
public final class SpiderBehaviour implements PuppetBehaviour {
    public static final SpiderBehaviour INSTANCE = new SpiderBehaviour();

    private SpiderBehaviour() {}

    /** spiders that have used their one mid-jump pounce (cleared when they land). */
    private static final Set<UUID> AIR_POUNCED = EffectState.sessionSet("puppeteer/spider/air_pounced");

    /** a pounce in flight: its bite is live from {@code start} until {@code until} (or landing). */
    private record PounceBite(long start, long until) {}

    private static final Map<UUID, PounceBite> POUNCE_BITES = EffectState.sessionConcurrent("puppeteer/spider/pounce_bites");

    /** a leap that has already struck someone (its bite or a swing) — one victim per pounce, mid-jump pounce included. */
    private static final Set<UUID> LEAP_STRUCK = EffectState.sessionSet("puppeteer/spider/leap_struck");

    /** when each leap began, so landing a tick later doesn't count as the next one. */
    private static final Map<UUID, Long> LEAP_START = EffectState.sessionConcurrent("puppeteer/spider/leap_start");

    /** the tick a swing last landed a hit (a pounce started on the same click has already struck). */
    private static final Map<UUID, Long> STRUCK_AT = EffectState.sessionConcurrent("puppeteer/spider/struck_at");

    /**
     * a pounce in flight bites the first thing it crashes into (not just whatever was in reach at the click). the server
     * sees your client-side leap late, so the box reaches a little ahead along your aim.
     */
    private static void tickPounceBite(ServerPlayer player) {
        PounceBite bite = POUNCE_BITES.get(player.getUUID());
        if (bite == null) {
            return;
        }
        long now = player.level().getGameTime();
        if (now > bite.until() || (now - bite.start() > 4 && player.onGround())) {
            POUNCE_BITES.remove(player.getUUID());
            return;
        }
        Vec3 look = player.getLookAngle();
        net.minecraft.world.phys.AABB box = player.getBoundingBox().inflate(0.5).expandTowards(look.x * 0.8, 0.0, look.z * 0.8);
        for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && !e.isSpectator() && e.isPickable() && e.invulnerableTime <= 10
                        && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            POUNCE_BITES.remove(player.getUUID());
            LEAP_STRUCK.add(player.getUUID());
            e.hurt(player.damageSources().playerAttack(player), (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE));
            player.swing(InteractionHand.MAIN_HAND, true);
            player.serverLevel().playSound(null, e.blockPosition(), possessed(player) == PuppetType.WOLF
                    ? SoundEvents.WOLF_GROWL : SoundEvents.SPIDER_STEP, SoundSource.PLAYERS, 1.0F, 1.2F);
            return;
        }
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickAirPounce(player);
    }

    /** a pounce's bite in flight, and the mid-jump pounce coming back on landing (shared with the wolf). */
    public static void tickAirPounce(ServerPlayer player) {
        tickPounceBite(player);
        if (player.onGround()) {
            AIR_POUNCED.remove(player.getUUID());
            if (player.level().getGameTime() - LEAP_START.getOrDefault(player.getUUID(), 0L) > 4
                    && !POUNCE_BITES.containsKey(player.getUUID())) {
                LEAP_STRUCK.remove(player.getUUID()); // landed: the next leap can strike again
            }
        }
    }

    /**
     * a swing landed a hit: that's this leap's one victim. a bite still in flight is called off, and a pounce on the
     * same click (or a mid-jump pounce after it) won't bite anyone else.
     */
    public static void meleeLanded(ServerPlayer player) {
        long now = player.level().getGameTime();
        STRUCK_AT.put(player.getUUID(), now);
        if (POUNCE_BITES.remove(player.getUUID()) != null || !player.onGround()) {
            LEAP_STRUCK.add(player.getUUID());
        }
    }

    @Override
    public void swing(ServerPlayer player, PuppetType type) {
        pounce(player, type);
    }

    /**
     * a swing pounces you forward the way a spider (or wolf) leaps at its prey (on the ground, off cooldown) — and a
     * swing MID-JUMP pounces again, harder, once per jump, so pouncing doubles as travel.
     */
    public static void pounce(ServerPlayer player, PuppetType type) {
        boolean wolf = type == PuppetType.WOLF;
        boolean air = !player.onGround();
        if (air ? AIR_POUNCED.contains(player.getUUID()) || player.isInWater() || player.onClimbable() : !Puppets.ready(player)) {
            return;
        }
        double base = wolf ? Config.PUPPETEER_WOLF_POUNCE_POWER.get() : Config.PUPPETEER_SPIDER_POUNCE_POWER.get();
        double power = base * (air ? Config.PUPPETEER_SPIDER_AIR_POUNCE_POWER.get() : 1.0);
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(power);
        Vec3 now = player.getDeltaMovement();
        double groundLift = wolf ? Config.PUPPETEER_WOLF_POUNCE_LIFT.get() : Config.PUPPETEER_SPIDER_POUNCE_LIFT.get();
        double lift = air ? Math.max(now.y, 0.0) + Config.PUPPETEER_SPIDER_AIR_POUNCE_LIFT.get() : groundLift;
        long at = player.level().getGameTime();
        if (air) {
            AIR_POUNCED.add(player.getUUID()); // cleared on landing (tick)
        } else {
            // a fresh leap — unless the click that started it already landed a hit
            LEAP_START.put(player.getUUID(), at);
            if (STRUCK_AT.getOrDefault(player.getUUID(), Long.MIN_VALUE) == at) {
                LEAP_STRUCK.add(player.getUUID());
            } else {
                LEAP_STRUCK.remove(player.getUUID());
            }
        }
        player.setDeltaMovement(flat.x + now.x * 0.2, lift, flat.z + now.z * 0.2);
        player.hurtMarked = true;
        Puppets.cooldown(player, Config.PUPPETEER_SPIDER_POUNCE_COOLDOWN_TICKS.get());
        if (!LEAP_STRUCK.contains(player.getUUID())) { // one victim per pounce
            POUNCE_BITES.put(player.getUUID(), new PounceBite(at, at + Config.PUPPETEER_POUNCE_BITE_TICKS.get()));
        }
        player.serverLevel().playSound(null, player.blockPosition(), wolf ? SoundEvents.WOLF_GROWL : SoundEvents.SPIDER_AMBIENT,
                SoundSource.PLAYERS, 1.0F, wolf ? 1.0F : 1.2F);
    }

    /** a landed swing is this leap's one victim; a cave spider's bite poisons. */
    @Override
    public void dealtDamage(ServerPlayer player, PuppetType type, LivingDamageEvent.Post event) {
        if (event.getSource().getDirectEntity() == player && event.getEntity() != player
                && !Puppets.SPECIAL_DAMAGE.contains(player.getUUID())) {
            meleeLanded(player);
        }
        if (type == PuppetType.CAVE_SPIDER && event.getSource().getDirectEntity() == player) {
            int seconds = Config.PUPPETEER_CAVE_SPIDER_POISON_SECONDS.get();
            if (seconds > 0) {
                event.getEntity().addEffect(new MobEffectInstance(MobEffects.POISON, seconds * 20), player);
            }
        }
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        AIR_POUNCED.remove(player.getUUID());
        POUNCE_BITES.remove(player.getUUID());
        LEAP_STRUCK.remove(player.getUUID());
        LEAP_START.remove(player.getUUID());
        STRUCK_AT.remove(player.getUUID());
    }

    /** every swing pounces. */
    @Override
    public boolean usesLeftClick(PuppetType type) {
        return true;
    }
}
