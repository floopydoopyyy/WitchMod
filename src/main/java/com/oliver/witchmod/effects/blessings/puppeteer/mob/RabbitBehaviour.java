package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Lunges.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.WolfBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the rabbit puppet. */
public final class RabbitBehaviour implements PuppetBehaviour {
    public static final RabbitBehaviour INSTANCE = new RabbitBehaviour();

    private RabbitBehaviour() {}

    // --- killer rabbit -----------------------------------------------------------------------------

    /** a killer rabbit's scrap in progress: its victim, where both are pinned, and hits landed so far. */
    public record Maul(LivingEntity target, Vec3 at, Vec3 targetAt, long start, int[] hits) {}

    private static final Map<UUID, Maul> MAULS = EffectState.sessionConcurrent("puppeteer/rabbit/mauls");

    private static final ResourceLocation RABBIT_JUMP_ID = EffectUtil.modifierId("puppeteer_rabbit_jump");

    private static final ResourceLocation RABBIT_FALL_ID = EffectUtil.modifierId("puppeteer_rabbit_fall");

    /** killer rabbit (and a frenzied wolf): Maul (right-click) — a straight lunge (the shared lunge machinery, no wind-up). */
    public static void startMaulLunge(ServerPlayer player) {
        if (LUNGES.containsKey(player.getUUID()) || MAULS.containsKey(player.getUUID())) {
            return;
        }
        boolean wolf = possessed(player) == PuppetType.WOLF;
        long now = player.level().getGameTime();
        if (wolf) { // a wolf's maul is its second move: the pounce keeps its own cooldown
            player.setData(WitchModAttachments.PUPPET_ACTION2_READY, now + Config.PUPPETEER_WOLF_MAUL_COOLDOWN_TICKS.get());
        } else {
            cooldown(player, Config.PUPPETEER_KILLER_RABBIT_MAUL_COOLDOWN_TICKS.get());
        }
        LUNGES.put(player.getUUID(), new Lunge(now, ConcurrentHashMap.newKeySet()));
        player.setData(WitchModAttachments.PUPPET_LUNGE_START, now);
        player.serverLevel().playSound(null, player.blockPosition(),
                wolf ? SoundEvents.WOLF_GROWL : SoundEvents.RABBIT_JUMP, SoundSource.PLAYERS, 1.2F, wolf ? 1.0F : 0.7F);
    }

    /** locked in a scrap right now. */
    public static boolean mauling(Player player) {
        return MAULS.containsKey(player.getUUID());
    }

    /** the lunge reached something: both of you are pinned in a frenzied scrap (PUPPET_FUSE > 0 holds your client still). */
    private static void startMaul(ServerPlayer player, LivingEntity target) {
        MAULS.put(player.getUUID(), new Maul(target, player.position(), target.position(), player.level().getGameTime(), new int[1]));
        player.setData(WitchModAttachments.PUPPET_FUSE, 1);
        player.serverLevel().playSound(null, target.blockPosition(), SoundEvents.RABBIT_ATTACK, SoundSource.PLAYERS, 1.5F, 0.8F);
    }

    /**
     * the scrap: neither of you can move, and every few ticks it lands a hit — a ball of dust and fur and crits, the
     * cartoon fight cloud — until its hits are spent or the victim drops. then the victim is thrown clear.
     */
    public static void tickMaul(ServerPlayer player) {
        Maul maul = MAULS.get(player.getUUID());
        if (maul == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        LivingEntity target = maul.target();
        boolean wolf = possessed(player) == PuppetType.WOLF;
        int maxHits = (wolf ? Config.PUPPETEER_WOLF_MAUL_HITS : Config.PUPPETEER_KILLER_RABBIT_MAUL_HITS).get();
        if (!target.isAlive() || target.level() != level || maul.hits()[0] >= maxHits) {
            endMaul(player, maul);
            return;
        }
        // pinned, both of you
        if (player.position().distanceToSqr(maul.at()) > 0.01) {
            player.teleportTo(maul.at().x, maul.at().y, maul.at().z);
        }
        player.setDeltaMovement(Vec3.ZERO);
        if (target.position().distanceToSqr(maul.targetAt()) > 0.01) {
            target.teleportTo(maul.targetAt().x, maul.targetAt().y, maul.targetAt().z);
        }
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        Vec3 mid = maul.at().add(maul.targetAt()).scale(0.5).add(0.0, 0.4, 0.0);
        // the fight cloud churns the whole time
        level.sendParticles(ParticleTypes.POOF, mid.x, mid.y, mid.z, 3, 0.4, 0.3, 0.4, 0.02);
        long t = level.getGameTime() - maul.start();
        if (t % (wolf ? Config.PUPPETEER_WOLF_MAUL_HIT_INTERVAL : Config.PUPPETEER_KILLER_RABBIT_MAUL_HIT_INTERVAL).get() != 0) {
            return;
        }
        maul.hits()[0]++;
        target.invulnerableTime = 0; // every hit in the scrap lands
        SPECIAL_DAMAGE.add(player.getUUID());
        NO_KNOCKBACK.add(target.getUUID()); // pinned, not shoved
        try {
            target.hurt(player.damageSources().playerAttack(player),
                    (wolf ? Config.PUPPETEER_WOLF_MAUL_DAMAGE : Config.PUPPETEER_KILLER_RABBIT_MAUL_DAMAGE).get().floatValue());
        } finally {
            SPECIAL_DAMAGE.remove(player.getUUID());
            NO_KNOCKBACK.remove(target.getUUID());
        }
        player.swing(level.random.nextBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
        level.sendParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 10, 0.4, 0.4, 0.4, 0.4);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, mid.x, mid.y + 0.3, mid.z, 3, 0.3, 0.2, 0.3, 0.1);
        level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.RABBIT_HIDE)),
                mid.x, mid.y, mid.z, 4, 0.3, 0.3, 0.3, 0.15); // tufts of fur
        level.sendParticles(ParticleTypes.CLOUD, mid.x, mid.y, mid.z, 4, 0.5, 0.3, 0.5, 0.05);
        level.playSound(null, target.blockPosition(), level.random.nextBoolean() ? SoundEvents.RABBIT_ATTACK : SoundEvents.PLAYER_ATTACK_STRONG,
                SoundSource.PLAYERS, 1.0F, 0.8F + level.random.nextFloat() * 0.5F);
    }

    private static void endMaul(ServerPlayer player, Maul maul) {
        MAULS.remove(player.getUUID());
        player.setData(WitchModAttachments.PUPPET_FUSE, 0);
        LivingEntity target = maul.target();
        if (target.isAlive()) {
            // thrown clear of the bunny
            target.knockback(1.2, player.getX() - target.getX(), player.getZ() - target.getZ());
            target.hurtMarked = true;
        }
        Vec3 mid = maul.at().add(maul.targetAt()).scale(0.5).add(0.0, 0.4, 0.0);
        player.serverLevel().sendParticles(ParticleTypes.POOF, mid.x, mid.y, mid.z, 16, 0.5, 0.4, 0.5, 0.08);
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        if (type == PuppetType.KILLER_RABBIT) {
            Lunges.tickLunge(player, type); // the maul's lunge
            tickMaul(player);
        } else {
            FarmAnimalBehaviour.lure(player, type); // carrots draw a plain rabbit in
        }
    }

    /** the killer bunny's maul (sets its own cooldown); a plain rabbit has no move. */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        if (type != PuppetType.KILLER_RABBIT || !Puppets.ready(player)) {
            return;
        }
        startMaulLunge(player);
        Puppets.countAction(player);
    }

    @Override
    public void lungeContact(ServerPlayer player, PuppetType type, Lunges.Lunge lunge) {
        maulContact(player);
    }

    /** a maul lunge latches onto the first thing it reaches (shared with the frenzied wolf). */
    public static void maulContact(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 ahead = player.getLookAngle();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.6).expandTowards(ahead.x * 0.8, 0.0, ahead.z * 0.8),
                e -> e != player && e.isAlive() && !e.isSpectator())) {
            Lunges.endLunge(player);
            startMaul(player, e); // the first thing it reaches, it latches onto
            return;
        }
    }

    /** a killer bunny mid-scrap can't swing as well. */
    @Override
    public boolean blocksMelee(Player player, PuppetType type, Entity target) {
        return type == PuppetType.KILLER_RABBIT && player.getData(WitchModAttachments.PUPPET_FUSE) > 0;
    }

    /** springy: higher jumps, and fall damage starts higher to match. */
    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        double boost = type == PuppetType.KILLER_RABBIT ? Config.PUPPETEER_KILLER_RABBIT_JUMP_BOOST.get()
                : Config.PUPPETEER_RABBIT_JUMP_BOOST.get();
        EffectUtil.addModifier(player, Attributes.JUMP_STRENGTH, RABBIT_JUMP_ID, boost, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EffectUtil.addModifier(player, Attributes.SAFE_FALL_DISTANCE, RABBIT_FALL_ID, boost * 4.0, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        MAULS.remove(player.getUUID());
        EffectUtil.removeModifier(player, Attributes.JUMP_STRENGTH, RABBIT_JUMP_ID);
        EffectUtil.removeModifier(player, Attributes.SAFE_FALL_DISTANCE, RABBIT_FALL_ID);
    }

    /** a rabbit only hops. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        out.addMove(player, type);
        out.add(tap(Key.FORWARD), "witchmod.puppeteer.action.hop", 0L);
    }
}
