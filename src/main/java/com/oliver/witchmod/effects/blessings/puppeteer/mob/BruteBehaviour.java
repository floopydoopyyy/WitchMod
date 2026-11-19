package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Lunges.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Possession.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the brute puppet. */
public final class BruteBehaviour implements PuppetBehaviour {
    public static final BruteBehaviour INSTANCE = new BruteBehaviour();

    private BruteBehaviour() {}

    private static final ResourceLocation BRUTE_KB_RESIST_ID = EffectUtil.modifierId("puppeteer_brute_kb_resist");

    /** hoglins: warped fungus drives them off, they zombify outside the nether; both: the lunge's collisions. */
    private static void tickBrute(ServerPlayer player, PuppetType type) {
        if (type == PuppetType.HOGLIN) {
            repelFungus(player);
            CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
            if (Config.PUPPETEER_HOGLIN_ZOMBIFIES.get() && !player.level().dimensionType().piglinSafe()
                    && !tag.getBoolean("IsImmuneToZombification")
                    && HOGLIN_TIME.merge(player.getUUID(), 1, Integer::sum) >= Config.PUPPETEER_HOGLIN_ZOMBIFY_TICKS.get()) {
                zombify(player);
                return;
            }
        }
        tickLunge(player, type);
    }

    /** each hoglin puppet's nearest repellent, re-scanned every few ticks (like vanilla's NEAREST_REPELLENT sensor). */
    private static final Map<UUID, java.util.Optional<BlockPos>> REPELLENT = EffectState.sessionConcurrent("puppeteer/brute/repellent");

    /**
     * placed warped fungus (vanilla's hoglin_repellents: fungus, nether portals, respawn anchors) drives a hoglin
     * away, exactly as it scares the real mob off.
     */
    private static void repelFungus(ServerPlayer player) {
        int range = (int) Math.round(Config.PUPPETEER_HOGLIN_FUNGUS_RANGE.get());
        if (range <= 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        java.util.Optional<BlockPos> near = REPELLENT.get(player.getUUID());
        if (near == null || player.tickCount % 5 == 0) {
            near = BlockPos.findClosestMatch(player.blockPosition(), range, 4,
                    p -> level.getBlockState(p).is(net.minecraft.tags.BlockTags.HOGLIN_REPELLENTS));
            REPELLENT.put(player.getUUID(), near);
        }
        near.ifPresent(at -> {
            Vec3 away = player.position().subtract(Vec3.atCenterOf(at)).multiply(1.0, 0.0, 1.0);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
            player.setDeltaMovement(player.getDeltaMovement().add(away.scale(Config.PUPPETEER_HOGLIN_FUNGUS_STRENGTH.get())));
            player.hurtMarked = true;
        });
    }

    /** the real thing: a hoglin outside the nether shakes, then becomes a zoglin — and so do you. */
    public static void zombify(ServerPlayer player) {
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ZOGLIN).toString());
        tag.remove("TimeInOverworld");
        tag.remove("IsImmuneToZombification");
        tag.remove("CannotBeHunted");
        tag.remove("Brain");
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        player.setData(WitchModAttachments.PUPPET_TYPE, PuppetType.ZOGLIN.id());
        HOGLIN_TIME.remove(player.getUUID());
        endLunge(player);
        EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, PuppetType.ZOGLIN.speedMultiplier() - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.HOGLIN_CONVERTED_TO_ZOMBIFIED,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        player.refreshDimensions();
    }

    /** the hoglin's tusk throw (vanilla HoglinBase.throwTarget): your swings toss what they hit up and away. */
    private static void tuskThrow(ServerPlayer player, PuppetType type, LivingEntity target) {
        double kb = (type == PuppetType.ZOGLIN ? Config.PUPPETEER_ZOGLIN_KNOCKBACK.get() : Config.PUPPETEER_HOGLIN_KNOCKBACK.get())
                - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (kb <= 0.0) {
            return;
        }
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        float twist = player.getRandom().nextInt(21) - 10;
        Vec3 push = new Vec3(dx, 0.0, dz).normalize().scale(kb * (player.getRandom().nextFloat() * 0.5F + 0.2F))
                .yRot(twist * Mth.DEG_TO_RAD);
        target.push(push.x, kb * player.getRandom().nextFloat() * 0.5, push.z);
        target.hurtMarked = true;
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickBrute(player, type);
    }

    /** lunge: a short wind-up, then a charge. */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        if (!Puppets.ready(player)) {
            return;
        }
        Puppets.cooldown(player, type == PuppetType.ZOGLIN ? Config.PUPPETEER_ZOGLIN_LUNGE_COOLDOWN_TICKS.get()
                : Config.PUPPETEER_HOGLIN_LUNGE_COOLDOWN_TICKS.get());
        Lunges.startLunge(player, type);
        Puppets.countAction(player);
    }

    /**
     * during the charge, whatever you run into takes the lunge's damage and is sent flying the way you're going. a
     * hoglin's charge stops on its first hit; a zoglin's ploughs on through (each thing only once).
     */
    @Override
    public void lungeContact(ServerPlayer player, PuppetType type, Lunges.Lunge lunge) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0.0, look.z);
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
        boolean zoglin = type == PuppetType.ZOGLIN;
        float damage = (zoglin ? Config.PUPPETEER_ZOGLIN_LUNGE_DAMAGE.get() : Config.PUPPETEER_HOGLIN_LUNGE_DAMAGE.get()).floatValue();
        double launch = Config.PUPPETEER_HOGLIN_LUNGE_LAUNCH.get();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.35),
                e -> e != player && e.isAlive() && !e.isSpectator() && !lunge.hit().contains(e.getUUID()))) {
            lunge.hit().add(e.getUUID());
            Puppets.SPECIAL_DAMAGE.add(player.getUUID());
            try {
                e.hurt(player.damageSources().playerAttack(player), damage);
            } finally {
                Puppets.SPECIAL_DAMAGE.remove(player.getUUID());
            }
            double power = launch * Math.max(0.0, 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            e.setDeltaMovement(dir.x * power, Math.max(e.getDeltaMovement().y, 0.0) + power * 0.4, dir.z * power);
            e.hurtMarked = true;
            player.swing(InteractionHand.MAIN_HAND, true);
            level.playSound(null, e.blockPosition(), zoglin ? SoundEvents.ZOGLIN_ATTACK : SoundEvents.HOGLIN_ATTACK,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            if (!zoglin) {
                Lunges.endLunge(player); // a hoglin's charge stops dead on the first thing it hits
                return;
            }
        }
    }

    /** hoglins shrug off knockback like the real thing; a hoglin carries on zombifying where the mob left off. */
    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        EffectUtil.addModifier(player, Attributes.KNOCKBACK_RESISTANCE, BRUTE_KB_RESIST_ID, 0.6, AttributeModifier.Operation.ADD_VALUE);
        if (type == PuppetType.HOGLIN) {
            Possession.HOGLIN_TIME.put(player.getUUID(), data.getInt("TimeInOverworld"));
        }
    }

    /** the tusk toss: whatever it hits goes flying up. */
    @Override
    public void dealtDamage(ServerPlayer player, PuppetType type, LivingDamageEvent.Post event) {
        if (event.getSource().getDirectEntity() == player && event.getEntity() != player && event.getNewDamage() > 0.0F
                && !Puppets.SPECIAL_DAMAGE.contains(player.getUUID())) {
            tuskThrow(player, type, event.getEntity());
        }
    }

    /** a baby hoglin bolts from whatever hurt it. */
    @Override
    public void hurtBy(ServerPlayer player, PuppetType type, DamageSource source, LivingEntity attacker) {
        int flee = Config.PUPPETEER_HOGLIN_BABY_FLEE_TICKS.get();
        if (type == PuppetType.HOGLIN && flee > 0 && Puppets.isBabyPuppet(player)) {
            Vec3 away = player.position().subtract(attacker.position()).multiply(1.0, 0.0, 1.0);
            away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(0.6);
            player.setDeltaMovement(player.getDeltaMovement().add(away.x, 0.2, away.z));
            player.hurtMarked = true;
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, flee, 1, false, false));
        }
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        EffectUtil.removeModifier(player, Attributes.KNOCKBACK_RESISTANCE, BRUTE_KB_RESIST_ID);
        REPELLENT.remove(player.getUUID());
    }
}
