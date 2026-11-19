package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.SquidBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the cat puppet. */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class CatBehaviour implements PuppetBehaviour {
    public static final CatBehaviour INSTANCE = new CatBehaviour();

    private CatBehaviour() {}

    // --- cat ----------------------------------------------------------------------------------------

    /** cat puppets → firework rocket ids near them last tick; one vanishing means it went off → a forced Scare. */
    private static final Map<UUID, Set<Integer>> CAT_ROCKETS = EffectState.sessionConcurrent("puppeteer/cat/cat_rockets");

    /** left-click: a meow (its own light cooldown on ACTION2). */
    public static void catMeow(ServerPlayer player) {
        if (!ready2(player)) {
            return;
        }
        player.setData(WitchModAttachments.PUPPET_ACTION2_READY,
                player.level().getGameTime() + Config.PUPPETEER_CAT_MEOW_COOLDOWN_TICKS.get());
        player.serverLevel().playSound(null, player.blockPosition(), possessed(player) == PuppetType.OCELOT ? SoundEvents.OCELOT_AMBIENT : SoundEvents.CAT_AMBIENT, SoundSource.PLAYERS,
                1.2F, 0.9F + player.getRandom().nextFloat() * 0.2F);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    /** the startled cat: flung backwards and up with a loud comical hiss, like a cat that's just been spooked. */
    private static void catScared(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 back = new Vec3(-look.x, 0.0, -look.z);
        back = back.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : back.normalize().scale(Config.PUPPETEER_CAT_SCARE_BACK.get());
        player.setDeltaMovement(back.x, Config.PUPPETEER_CAT_SCARE_UP.get(), back.z);
        player.hurtMarked = true;
        player.resetFallDistance();
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.CAT_HISS, SoundSource.PLAYERS, 1.5F, 1.0F);
        level.playSound(null, player.blockPosition(), SoundEvents.CAT_AMBIENT, SoundSource.PLAYERS, 1.5F, 1.6F);
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.3, player.getZ(), 8, 0.2, 0.1, 0.2, 0.02);
    }

    /** cat upkeep: scaring creepers off, and being spooked airborne by a nearby firework going off. */
    private static void tickCat(ServerPlayer player) {
        scareCreepers(player);
        catLoudScare(player);
    }

    /** nearby creepers deflate and back away, as a real cat scares them. */
    public static void scareCreepers(ServerPlayer player) {
        double r = Config.PUPPETEER_CAT_CREEPER_SCARE_RADIUS.get();
        if (r <= 0 || player.tickCount % 5 != 0) {
            return;
        }
        for (net.minecraft.world.entity.monster.Creeper creeper : player.serverLevel().getEntitiesOfClass(
                net.minecraft.world.entity.monster.Creeper.class, player.getBoundingBox().inflate(r), LivingEntity::isAlive)) {
            creeper.setTarget(null);
            creeper.setSwellDir(-1); // stop fusing and deflate
            // actually RUN away, like vanilla's AvoidEntityGoal: path to a spot away from the cat, fast.
            Vec3 flee = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(creeper, 16, 7, player.position());
            if (flee != null) {
                creeper.getNavigation().moveTo(flee.x, flee.y, flee.z, 1.4);
            }
        }
    }

    /** a firework rocket going off nearby spooks the cat (explosions are handled in onExplosion). */
    private static void catLoudScare(ServerPlayer player) {
        double r = Config.PUPPETEER_CAT_SCARE_LOUD_RADIUS.get();
        if (r <= 0) {
            return;
        }
        Set<Integer> present = ConcurrentHashMap.newKeySet();
        for (net.minecraft.world.entity.projectile.FireworkRocketEntity rocket : player.serverLevel().getEntitiesOfClass(
                net.minecraft.world.entity.projectile.FireworkRocketEntity.class, player.getBoundingBox().inflate(r))) {
            present.add(rocket.getId());
        }
        Set<Integer> was = CAT_ROCKETS.put(player.getUUID(), present);
        if (was == null || !ready(player)) {
            return;
        }
        for (int id : was) {
            if (!present.contains(id)) { // one that was close is gone — it went off
                cooldown(player, Config.PUPPETEER_CAT_SCARE_COOLDOWN_TICKS.get());
                catScared(player);
                return;
            }
        }
    }

    /** a nearby explosion spooks a cat puppet into the air — its forced Scare, respecting the cooldown. */
    @SubscribeEvent
    public static void onExplosion(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        double r = Config.PUPPETEER_CAT_SCARE_LOUD_RADIUS.get();
        if (r <= 0 || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Vec3 at = event.getExplosion().center();
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(at, at).inflate(r))) {
            if (possessed(p) == PuppetType.CAT && ready(p)) {
                cooldown(p, Config.PUPPETEER_CAT_SCARE_COOLDOWN_TICKS.get());
                catScared(p);
            }
        }
    }

    /** whether a crouched cat puppet is sitting on top of {@code chestPos} (blocking others from opening it). */
    public static boolean catSittingOnChest(net.minecraft.world.level.Level level, BlockPos chestPos) {
        for (Player p : level.players()) {
            if (p instanceof ServerPlayer sp && possessed(sp) == PuppetType.CAT && sp.isCrouching()
                    && sp.blockPosition().equals(chestPos.above())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickCat(player);
    }

    /** scare: flung backwards and up with a loud hiss. */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        if (!Puppets.ready(player)) {
            return;
        }
        Puppets.cooldown(player, Config.PUPPETEER_CAT_SCARE_COOLDOWN_TICKS.get());
        catScared(player);
        Puppets.countAction(player);
    }

    /** left-click: a meow. */
    @Override
    public void swing(ServerPlayer player, PuppetType type) {
        catMeow(player);
    }

    /** no bite at all. */
    @Override
    public boolean blocksMelee(Player player, PuppetType type, Entity target) {
        return true;
    }

    /** cats always land on their feet. */
    @Override
    public void incomingDamage(ServerPlayer player, PuppetType type, LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypeTags.IS_FALL)) {
            event.setCanceled(true);
        }
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        CAT_ROCKETS.remove(player.getUUID());
    }

    /** left-click: a meow. */
    @Override
    public boolean usesLeftClick(PuppetType type) {
        return true;
    }

    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        out.addMove(player, type);
        out.add(tap(Key.ATTACK), "witchmod.puppeteer.action.meow", PuppetControls.ready2(player));
    }
}
