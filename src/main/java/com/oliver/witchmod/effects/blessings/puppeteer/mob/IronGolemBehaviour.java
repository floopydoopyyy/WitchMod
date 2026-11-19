package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Holds.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the iron golem puppet. */
public final class IronGolemBehaviour implements PuppetBehaviour {
    public static final IronGolemBehaviour INSTANCE = new IronGolemBehaviour();

    private IronGolemBehaviour() {}

    private static final ResourceLocation GOLEM_KB_RESIST_ID = EffectUtil.modifierId("puppeteer_golem_kb_resist");

    public static final ResourceLocation GOLEM_ATTACK_SPEED_ID = EffectUtil.modifierId("puppeteer_golem_attack_speed");

    /** golem-swing victims → upward fling, applied at the end of the tick (after vanilla's own hit knockback). */
    public static final Map<LivingEntity, Double> PENDING_FLING = new ConcurrentHashMap<>();

    /** a golem's attack charge, caught before vanilla resets it, so its swing damage scales like a weapon's. */
    public static final Map<UUID, Float> SWING_STRENGTH = EffectState.sessionConcurrent("puppeteer/iron_golem/swing_strength");

    // --- iron golem ---------------------------------------------------------------------------------

    /**
     * iron golem: Offer Poppy (hold right-click). you hold a poppy out (the real golem's pose) to whoever you're looking
     * at; held for puppeteerGolemPoppyHoldTicks, they take it — brief Strength I and Resistance I. nobody there: you
     * just keep holding it out.
     */
    private static void tickOffer(ServerPlayer player) {
        int hold = player.getData(WitchModAttachments.PUPPET_FUSE);
        if (!HELD.contains(player.getUUID()) || !ready(player)) {
            if (hold > 0 && !HELD.contains(player.getUUID())) {
                player.setData(WitchModAttachments.PUPPET_FUSE, 0);
            }
            return;
        }
        int need = Config.PUPPETEER_GOLEM_POPPY_HOLD_TICKS.get();
        int next = Math.min(hold + chargeStep(player), need);
        player.setData(WitchModAttachments.PUPPET_FUSE, next);
        if (next < need) {
            return;
        }
        LivingEntity taker = lookTarget(player, 4.0);
        if (taker == null) {
            return; // still holding it out
        }
        int ticks = Config.PUPPETEER_GOLEM_POPPY_SECONDS.get() * 20;
        taker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0), player);
        taker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 0), player);
        player.serverLevel().sendParticles(ParticleTypes.HEART, taker.getX(), taker.getY() + taker.getBbHeight() + 0.3, taker.getZ(),
                2, 0.2, 0.1, 0.2, 0.0);
        player.serverLevel().playSound(null, taker.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.4F);
        player.setData(WitchModAttachments.PUPPET_FUSE, 0);
        cooldown(player, Config.PUPPETEER_GOLEM_POPPY_COOLDOWN_TICKS.get());
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickOffer(player);
        if (player.isUnderWater()) {
            player.setAirSupply(player.getMaxAirSupply()); // golems don't breathe
        }
    }

    /** lowering the poppy before it was taken: nothing given, nothing spent. */
    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
    }

    /** immovable (no knockback at all), and swings on a weapon-style cooldown. */
    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        EffectUtil.addModifier(player, Attributes.KNOCKBACK_RESISTANCE, GOLEM_KB_RESIST_ID, 1.0, AttributeModifier.Operation.ADD_VALUE);
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) {
            EffectUtil.addModifier(player, Attributes.ATTACK_SPEED, GOLEM_ATTACK_SPEED_ID,
                    Config.PUPPETEER_GOLEM_ATTACK_SPEED.get() - attackSpeed.getBaseValue(), AttributeModifier.Operation.ADD_VALUE);
        }
    }

    /** the swing's charge, caught before vanilla resets it (its damage scales like a weapon's). */
    @Override
    public void meleeSwung(Player player, PuppetType type) {
        recordSwing(player);
    }

    /** shared with the vindicator: remember how charged this swing was. */
    public static void recordSwing(Player player) {
        if (!player.level().isClientSide) {
            SWING_STRENGTH.put(player.getUUID(), player.getAttackStrengthScale(0.5F)); // before vanilla resets it
        }
    }

    /** vanilla's weapon cooldown scaling on a golem / vindicator swing. */
    public static float swingScaled(float amount, @Nullable Float strength) {
        return strength == null ? amount : amount * (0.2F + strength * strength * 0.8F);
    }

    @Override
    public float meleeDamage(ServerPlayer player, PuppetType type, @Nullable Float swingStrength) {
        return swingScaled(type.attackDamage(), swingStrength);
    }

    /**
     * the golem's swing flings its target skywards, with the golem's clang. queued for the end of the tick: vanilla
     * applies the hit's own knockback AFTER this event, and that flattens any upward push given here.
     */
    @Override
    public void dealtDamage(ServerPlayer player, PuppetType type, LivingDamageEvent.Post event) {
        if (event.getSource().getDirectEntity() == player && event.getEntity() != player && event.getNewDamage() > 0.0F
                && !Puppets.SPECIAL_DAMAGE.contains(player.getUUID())) {
            PENDING_FLING.put(event.getEntity(), Config.PUPPETEER_GOLEM_FLING.get());
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    /** patching up a golem with iron, like the real thing. */
    @Override
    public boolean interact(Player user, InteractionHand hand, ItemStack stack, ServerPlayer puppet, PuppetType type) {
        if (stack.is(Items.IRON_INGOT)
                && puppet.getData(WitchModAttachments.PUPPET_HEALTH) < puppet.getData(WitchModAttachments.PUPPET_MAX_HEALTH)) {
            if (!user.level().isClientSide) {
                stack.consume(1, user);
                Puppets.scaleHeal(puppet, Config.PUPPETEER_GOLEM_IRON_HEAL.get().floatValue());
                puppet.serverLevel().playSound(null, puppet.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.0F,
                        1.0F + (puppet.getRandom().nextFloat() - puppet.getRandom().nextFloat()) * 0.2F);
            }
            return true;
        }
        return false;
    }

    @Override
    public void incomingDamage(ServerPlayer player, PuppetType type, LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypeTags.IS_FALL)) {
            event.setCanceled(true);
        }
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        EffectUtil.removeModifier(player, Attributes.KNOCKBACK_RESISTANCE, GOLEM_KB_RESIST_ID);
        EffectUtil.removeModifier(player, Attributes.ATTACK_SPEED, GOLEM_ATTACK_SPEED_ID); // (the vindicator's too)
        SWING_STRENGTH.remove(player.getUUID());
    }
}
