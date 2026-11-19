package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Holds.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.BlazeBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.SkeletonBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the ghast puppet. */
public final class GhastBehaviour implements PuppetBehaviour {
    public static final GhastBehaviour INSTANCE = new GhastBehaviour();

    private GhastBehaviour() {}

    // --- ghast / blaze / breeze: charged shots ------------------------------------------------------

    /** projectile streams in progress (ghast volley, blaze volley) → shots still to come, and the tick of the next one. */
    public record Stream(int left, long next, int interval, boolean enhanced) {}

    public static final Map<UUID, Stream> STREAMS = EffectState.sessionConcurrent("puppeteer/ghast/streams");

    /** ghast upkeep: the volley charge (while left-click is held and the volley's ready), and the stream once it fires. */
    private static void tickGhast(ServerPlayer player) {
        if (ATTACK_HELD.contains(player.getUUID()) && ready2(player) && !STREAMS.containsKey(player.getUUID())) {
            int cap = Config.PUPPETEER_GHAST_VOLLEY_CHARGE_TICKS.get();
            int hold = player.getData(WitchModAttachments.PUPPET_FUSE);
            if (hold < cap) {
                player.setData(WitchModAttachments.PUPPET_FUSE, Math.min(cap, hold + chargeStep(player)));
            }
        }
        tickStream(player, PuppetType.GHAST);
        chargeSlow(player);
    }

    /** ghast: right-click — one fireball, straight away (the face and the cry come with it). */
    private static void ghastShot(ServerPlayer player) {
        cooldown(player, Config.PUPPETEER_GHAST_FIREBALL_COOLDOWN_TICKS.get());
        player.serverLevel().levelEvent(null, 1015, player.blockPosition(), 0);
        ghastFireball(player, 0.0F);
    }

    /** the ghast's own fireball (its blast power from the puppet's data), launched clear of its 4-wide body. */
    private static void ghastFireball(ServerPlayer player, float scatter) {
        ServerLevel level = player.serverLevel();
        Vec3 look = scattered(player, scatter);
        int power = Math.max(1, player.getData(WitchModAttachments.PUPPET_DATA).getByte("ExplosionPower"));
        net.minecraft.world.entity.projectile.LargeFireball fireball = new net.minecraft.world.entity.projectile.LargeFireball(level, player, look, power);
        Vec3 from = player.getEyePosition().add(look.scale(3.0)); // vanilla spawns it 4 out from the ghast's middle
        fireball.setPos(from.x, from.y - 0.5, from.z);
        level.addFreshEntity(fireball);
        level.levelEvent(null, 1016, player.blockPosition(), 0); // the ghast's shot
        player.setData(WitchModAttachments.PUPPET_ACTION_COUNT, player.getData(WitchModAttachments.PUPPET_ACTION_COUNT) + 1);
    }

    /** your aim, nudged up to {@code degrees} off at random (a volley's scatter). */
    public static Vec3 scattered(ServerPlayer player, float degrees) {
        if (degrees <= 0.0F) {
            return player.getLookAngle();
        }
        float yaw = player.getYRot() + (player.getRandom().nextFloat() * 2.0F - 1.0F) * degrees;
        float pitch = player.getXRot() + (player.getRandom().nextFloat() * 2.0F - 1.0F) * degrees * 0.6F;
        return Vec3.directionFromRotation(pitch, yaw);
    }

    /** fires the next shot of a stream when it's due (the ghast's / blaze's volley), following your aim with scatter. */
    public static void tickStream(ServerPlayer player, PuppetType type) {
        Stream stream = STREAMS.get(player.getUUID());
        long now = player.level().getGameTime();
        if (stream == null || now < stream.next()) {
            return;
        }
        if (type == PuppetType.GHAST) {
            ghastFireball(player, Config.PUPPETEER_GHAST_VOLLEY_SCATTER.get().floatValue());
        } else {
            blazeFireball(player, stream.enhanced());
        }
        int interval = type == PuppetType.GHAST ? Config.PUPPETEER_GHAST_VOLLEY_INTERVAL_TICKS.get() : stream.interval();
        if (stream.left() <= 1) {
            STREAMS.remove(player.getUUID());
        } else {
            STREAMS.put(player.getUUID(), new Stream(stream.left() - 1, now + interval, stream.interval(), stream.enhanced()));
        }
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickGhast(player);
    }

    /** one fireball (sets its own cooldown). */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        if (!Puppets.ready(player)) {
            return;
        }
        ghastShot(player);
        Puppets.countAction(player);
    }

    /** a ghast's left-click is its volley — it has no melee. */
    @Override
    public boolean blocksMelee(Player player, PuppetType type, Entity target) {
        return true;
    }

    @Override
    public boolean dealsMelee(ServerPlayer player, PuppetType type) {
        return false;
    }

    /** always flying (a ghast never really lands). */
    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        Puppets.grantBatFlight(player);
        if (player.getAbilities().mayfly) {
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
    }

    @Override
    public void incomingDamage(ServerPlayer player, PuppetType type, LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypeTags.IS_FALL)) {
            event.setCanceled(true);
        }
    }

    /** its 4x4x4 body is what gets hit (and what has to fit). */
    @Override
    public boolean fullWidth(PuppetType type) {
        return true;
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        STREAMS.remove(player.getUUID());
    }

    /** hold left-click: charge the volley. */
    @Override
    public boolean holdsAttack(PuppetType type) {
        return true;
    }

    /** fully charged: the stream goes — and only now the face and the cry. */
    @Override
    public void releaseAttackHeld(ServerPlayer player, PuppetType type, int charge) {
        if (charge >= Config.PUPPETEER_GHAST_VOLLEY_CHARGE_TICKS.get() && Puppets.ready2(player) && !STREAMS.containsKey(player.getUUID())) {
            player.serverLevel().levelEvent(null, 1015, player.blockPosition(), 0);
            STREAMS.put(player.getUUID(), new Stream(Config.PUPPETEER_GHAST_VOLLEY_COUNT.get(), player.level().getGameTime(), 0, true));
            player.setData(WitchModAttachments.PUPPET_ACTION2_READY,
                    player.level().getGameTime() + Config.PUPPETEER_GHAST_VOLLEY_COOLDOWN_TICKS.get());
        }
    }

    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        out.addMove(player, type);
        out.add(hold(Key.ATTACK), "witchmod.puppeteer.action.ghast_volley", PuppetControls.ready2(player));
    }
}
