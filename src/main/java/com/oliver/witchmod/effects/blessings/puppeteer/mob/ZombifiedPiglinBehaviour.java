package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.mob.ZombieBehaviour.*;

/** puppeteer: the zombified piglin puppet. */
public final class ZombifiedPiglinBehaviour implements PuppetBehaviour {
    public static final ZombifiedPiglinBehaviour INSTANCE = new ZombifiedPiglinBehaviour();

    private ZombifiedPiglinBehaviour() {}

    /**
     * zombified piglin: Convert — the pig you right-click, and every pig around it, turns into a zombified piglin the
     * way lightning would do it (golden sword, babies stay babies, names kept).
     */
    private static void convertPigs(ServerPlayer player, net.minecraft.world.entity.animal.Pig clicked) {
        ServerLevel level = player.serverLevel();
        double r = Config.PUPPETEER_PIGLIN_CONVERT_RADIUS.get();
        List<net.minecraft.world.entity.animal.Pig> pigs = new ArrayList<>(level.getEntitiesOfClass(
                net.minecraft.world.entity.animal.Pig.class, clicked.getBoundingBox().inflate(r), Entity::isAlive));
        if (!pigs.contains(clicked)) {
            pigs.add(clicked);
        }
        for (net.minecraft.world.entity.animal.Pig pig : pigs) {
            if (!EventHooks.canLivingConvert(pig, EntityType.ZOMBIFIED_PIGLIN, timer -> {})) {
                continue;
            }
            net.minecraft.world.entity.monster.ZombifiedPiglin zp = EntityType.ZOMBIFIED_PIGLIN.create(level);
            if (zp == null) {
                continue;
            }
            zp.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
            zp.moveTo(pig.getX(), pig.getY(), pig.getZ(), pig.getYRot(), pig.getXRot());
            zp.setNoAi(pig.isNoAi());
            zp.setBaby(pig.isBaby());
            if (pig.hasCustomName()) {
                zp.setCustomName(pig.getCustomName());
                zp.setCustomNameVisible(pig.isCustomNameVisible());
            }
            zp.setPersistenceRequired();
            EventHooks.onLivingConvert(pig, zp);
            level.addFreshEntity(zp);
            pig.discard();
            level.sendParticles(ParticleTypes.SMOKE, zp.getX(), zp.getY() + 0.6, zp.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        }
        level.playSound(null, clicked.blockPosition(), SoundEvents.ZOMBIFIED_PIGLIN_ANGRY, SoundSource.PLAYERS, 1.5F, 0.8F);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    /**
     * zombified piglin: no rally — but hitting something, or being hit, turns the whole pack within range on it, and
     * sends you and them into a frenzy (Speed II).
     */
    private static void piglinCall(ServerPlayer player, LivingEntity foe) {
        if (foe == player || !foe.isAlive()) {
            return;
        }
        double r = Config.PUPPETEER_PIGLIN_CALL_RADIUS.get();
        int ticks = Config.PUPPETEER_PIGLIN_FRENZY_SECONDS.get() * 20;
        for (net.minecraft.world.entity.monster.ZombifiedPiglin zp : player.serverLevel().getEntitiesOfClass(
                net.minecraft.world.entity.monster.ZombifiedPiglin.class, player.getBoundingBox().inflate(r),
                z -> z != foe && z.isAlive())) {
            zp.setTarget(foe);
            zp.setPersistentAngerTarget(foe.getUUID());
            zp.startPersistentAngerTimer();
            zp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1, false, true));
        }
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1, false, true));
    }

    /** convert: the pig you right-click (and the pigs around it) turn into zombified piglins. */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        if (!Puppets.ready(player)) {
            return;
        }
        if (!(target instanceof net.minecraft.world.entity.animal.Pig pig)) {
            player.displayClientMessage(Component.translatable("witchmod.puppeteer.convert_hint").withStyle(ChatFormatting.GRAY), true);
            return; // no pig, no cooldown spent
        }
        Puppets.cooldown(player, Config.PUPPETEER_PIGLIN_CONVERT_COOLDOWN_TICKS.get());
        convertPigs(player, pig);
        Puppets.countAction(player);
    }

    /** hit something and the whole pack turns on it. */
    @Override
    public void dealtDamage(ServerPlayer player, PuppetType type, LivingDamageEvent.Post event) {
        if (event.getEntity() != player && event.getNewDamage() > 0.0F) {
            piglinCall(player, event.getEntity());
        }
    }

    /** hit one, the pack answers. */
    @Override
    public void hurtBy(ServerPlayer player, PuppetType type, DamageSource source, LivingEntity attacker) {
        piglinCall(player, attacker);
    }
}
