package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Holds.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.GhastBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.GuardianBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.SilverfishBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.SkeletonBehaviour.*;

/** puppeteer: the blaze puppet. */
public final class BlazeBehaviour implements PuppetBehaviour {
    public static final BlazeBehaviour INSTANCE = new BlazeBehaviour();

    private BlazeBehaviour() {}

    /** a blaze in a fight lights up: refreshed by hitting, being hit and firing (PUPPET_RAGE_END doubles as its timer). */
    private static void blazeCombat(ServerPlayer player) {
        player.setData(WitchModAttachments.PUPPET_RAGE_END,
                player.level().getGameTime() + Config.PUPPETEER_BLAZE_COMBAT_SECONDS.get() * 20L);
    }

    /**
     * blaze upkeep: water and rain hurt, the right-click wind-up charges (sped by Dexterous), the volley streams out,
     * and charging slows you.
     */
    private static void tickBlaze(ServerPlayer player, int ticksRemaining) {
        // keep the blaze able to fly — re-grant each tick in case anything strips mayfly (it flies like the breeze).
        if (!player.getAbilities().mayfly && !player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = true;
            player.setData(WitchModAttachments.PUPPET_GRANTED_FLIGHT, true);
            player.onUpdateAbilities();
        }
        if (ticksRemaining % 10 == 0 && player.isInWaterRainOrBubble()) {
            player.hurt(player.damageSources().drown(), 1.0F);
        }
        tickHold(player, PuppetType.BLAZE);
        tickStream(player, PuppetType.BLAZE);
        chargeSlow(player);
    }

    /**
     * blaze: let go of the wind-up — at least puppeteerBlazeWindupTicks or it fizzles; then a volley of its fireballs,
     * more the longer you held (min..max fireballs over the charge), one every 3 ticks.
     */
    private static void blazeRelease(ServerPlayer player, int charge) {
        int windup = Config.PUPPETEER_BLAZE_WINDUP_TICKS.get();
        if (charge > 0 && charge < windup && ready(player) && !STREAMS.containsKey(player.getUUID())) {
            // a TAP: the real blaze's burst — three of its fireballs, six ticks apart, at its own speed
            STREAMS.put(player.getUUID(), new Stream(Config.PUPPETEER_BLAZE_TAP_FIREBALLS.get(), player.level().getGameTime(), 6, false));
            cooldown(player, Config.PUPPETEER_BLAZE_VOLLEY_COOLDOWN_TICKS.get());
            blazeCombat(player);
            return;
        }
        if (charge < windup || !ready(player) || STREAMS.containsKey(player.getUUID())) {
            return;
        }
        int full = Math.max(windup + 1, Config.PUPPETEER_BLAZE_CHARGE_TICKS.get());
        float t = Mth.clamp((charge - windup) / (float) (full - windup), 0.0F, 1.0F);
        int min = Config.PUPPETEER_BLAZE_MIN_FIREBALLS.get();
        int max = Math.max(min, Config.PUPPETEER_BLAZE_MAX_FIREBALLS.get());
        int count = min + Math.round(t * (max - min));
        STREAMS.put(player.getUUID(), new Stream(count, player.level().getGameTime(), 3, true));
        cooldown(player, Config.PUPPETEER_BLAZE_VOLLEY_COOLDOWN_TICKS.get());
        blazeCombat(player);
    }

    /** one of the blaze's own small fireballs (sets things alight), with a little scatter, and its shot sound. */
    public static void blazeFireball(ServerPlayer player, boolean enhanced) {
        ServerLevel level = player.serverLevel();
        Vec3 look = scattered(player, 4.0F);
        net.minecraft.world.entity.projectile.SmallFireball fireball =
                new net.minecraft.world.entity.projectile.SmallFireball(level, player, look.scale(enhanced ? 1.4 : 1.0)); // enhanced: quicker
        Vec3 from = player.getEyePosition().add(look.scale(0.9));
        fireball.setPos(from.x, from.y - 0.2, from.z);
        level.addFreshEntity(fireball);
        level.levelEvent(null, 1018, player.blockPosition(), 0); // the blaze's shot
        player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickBlaze(player, ticksRemaining);
    }

    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        blazeRelease(player, charge);
    }

    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        Puppets.grantBatFlight(player);
    }

    /** a blaze lights up when it fights... */
    @Override
    public void dealtDamage(ServerPlayer player, PuppetType type, LivingDamageEvent.Post event) {
        if (event.getEntity() != player) {
            blazeCombat(player);
        }
    }

    /** ...and when it's hit. */
    @Override
    public void damaged(ServerPlayer player, PuppetType type, DamageSource source) {
        blazeCombat(player);
    }

    @Override
    public void incomingDamage(ServerPlayer player, PuppetType type, LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypeTags.IS_FALL)) {
            event.setCanceled(true);
        }
    }

    /** a snowball stings a blaze (3, like the real one). */
    @Override
    public void struckByProjectile(ServerPlayer player, PuppetType type, Projectile projectile, ProjectileImpactEvent event) {
        if (projectile instanceof net.minecraft.world.entity.projectile.Snowball ball) {
            player.hurt(ball.damageSources().thrown(ball, ball.getOwner()), 3.0F);
        }
    }
}
