package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.RabbitBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the wolf puppet. */
public final class WolfBehaviour implements PuppetBehaviour {
    public static final WolfBehaviour INSTANCE = new WolfBehaviour();

    private WolfBehaviour() {}

    // --- wolf ---------------------------------------------------------------------------------------

    private static final ResourceLocation WOLF_FRENZY_ATTACK_ID = EffectUtil.modifierId("puppeteer_wolf_frenzy_attack");

    /** one fading scent footprint where a mob walked; the prey's (whoever hit a frenzied wolf) burn brighter/red. */
    private record ScentPrint(double x, double y, double z, long tick, boolean prey) {}

    /** each wolf puppet's scent trail — re-sent to its own client so it lingers (a limited Bloodhound). */
    private static final Map<UUID, List<ScentPrint>> WOLF_SCENT = EffectState.sessionConcurrent("puppeteer/wolf/wolf_scent");

    /** a frenzied wolf → the mob that last hit it, whose scent is burned in bright for the pursuit. */
    private static final Map<UUID, UUID> WOLF_PREY = EffectState.sessionConcurrent("puppeteer/wolf/wolf_prey");

    /** whether a wolf puppet is rabid (PUPPET_RAGE_END doubles as the frenzy-until tick, synced for the angry render). */
    public static boolean frenzied(ServerPlayer player) {
        return possessed(player) == PuppetType.WOLF
                && player.level().getGameTime() < player.getData(WitchModAttachments.PUPPET_RAGE_END);
    }

    /** a hit sends a wolf rabid: Darkness, a faster bite, a pursuit Maul on right-click, the attacker's scent burned bright. */
    private static void wolfFrenzy(ServerPlayer player, LivingEntity attacker) {
        int ticks = Config.PUPPETEER_WOLF_FRENZY_TICKS.get();
        if (ticks <= 0) {
            return;
        }
        boolean fresh = !frenzied(player);
        player.setData(WitchModAttachments.PUPPET_RAGE_END, player.level().getGameTime() + ticks);
        WOLF_PREY.put(player.getUUID(), attacker.getUUID());
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, ticks, 0, false, false));
        if (fresh) {
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.WOLF_GROWL, SoundSource.PLAYERS, 1.3F, 0.7F);
        }
    }

    /** wolf upkeep: a faster bite while rabid (and the rabid motes), plus the always-on scent sense. */
    private static void tickWolf(ServerPlayer player) {
        boolean frenzy = frenzied(player);
        AttributeInstance atk = player.getAttribute(Attributes.ATTACK_SPEED);
        if (atk != null) {
            boolean has = atk.hasModifier(WOLF_FRENZY_ATTACK_ID);
            if (frenzy && !has) {
                EffectUtil.addModifier(player, Attributes.ATTACK_SPEED, WOLF_FRENZY_ATTACK_ID,
                        Config.PUPPETEER_WOLF_FRENZY_ATTACK_SPEED.get(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            } else if (!frenzy && has) {
                atk.removeModifier(WOLF_FRENZY_ATTACK_ID);
                WOLF_PREY.remove(player.getUUID());
            }
        }
        // no public rabid FX (red motes / bristle) — it's a disguise; the frenzy shows only in your own faster bite + maul.
        wolfScent(player, frenzy);
    }

    /** the scent sense: nearby living mobs leave fading footprints shown to YOU only; the prey's burn bright red. */
    private static void wolfScent(ServerPlayer player, boolean frenzy) {
        long now = player.level().getGameTime();
        List<ScentPrint> trail = WOLF_SCENT.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        int linger = Config.PUPPETEER_WOLF_SCENT_LINGER_TICKS.get();
        if (now % Config.PUPPETEER_WOLF_SCENT_INTERVAL.get() == 0) {
            double r = Config.PUPPETEER_WOLF_SCENT_RADIUS.get();
            UUID preyId = WOLF_PREY.get(player.getUUID());
            for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r),
                    e -> e != player && e.isAlive() && !e.isSpectator() && e.onGround())) {
                trail.add(new ScentPrint(e.getX(), e.getY() + 0.06, e.getZ(), now, frenzy && e.getUUID().equals(preyId)));
            }
        }
        trail.removeIf(p -> now - p.tick() > linger);
        while (trail.size() > 300) {
            trail.remove(0);
        }
        if (now % 4 != 0) {
            return; // re-send a few times a second — enough to linger, not a flood
        }
        net.minecraft.core.particles.DustParticleOptions normal =
                new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.4F, 0.75F, 1.0F), 1.6F);
        net.minecraft.core.particles.DustParticleOptions prey =
                new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0F, 0.05F, 0.05F), 2.4F);
        for (ScentPrint p : trail) {
            // a clear marker on the ground, plus a short rising wisp, so a trail reads at a glance (the prey's a bold red).
            player.serverLevel().sendParticles(player, p.prey() ? prey : normal, false, p.x(), p.y(), p.z(), 1, 0.0, 0.0, 0.0, 0.0);
            player.serverLevel().sendParticles(player, p.prey() ? prey : normal, false, p.x(), p.y() + 0.45, p.z(), 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** mid-maul: lunging at its prey or locked in the scrap (no new charge then). */
    private static boolean mauling(ServerPlayer player) {
        return Lunges.LUNGES.containsKey(player.getUUID()) || RabbitBehaviour.mauling(player);
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        Lunges.tickLunge(player, type); // a frenzied wolf's maul (the killer bunny's, weakened)
        RabbitBehaviour.tickMaul(player);
        tickWolf(player);
        SpiderBehaviour.tickAirPounce(player);
        boolean charging = false;
        if (!mauling(player)) {
            Holds.tickHold(player, type);
            charging = Holds.HELD.contains(player.getUUID()) && player.getData(WitchModAttachments.PUPPET_FUSE) > 0;
        }
        // a brief slow while it gathers itself for the maul (the view closes in client side — PuppetCamera)
        Puppets.chargeSlow(player, charging, Config.PUPPETEER_WOLF_MAUL_CHARGE_SLOW.get());
    }

    /** the maul only gathers while rabid, off its cooldown. */
    @Override
    public boolean canCharge(ServerPlayer player, PuppetType type) {
        return frenzied(player) && Puppets.ready2(player);
    }

    @Override
    public void chargeStarted(ServerPlayer player, PuppetType type) {
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.WOLF_GROWL, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    /**
     * let go of a charged right-click (rabid only): the maul — a lunge (longer and faster the fuller the charge) that
     * latches onto the first thing it reaches for a pinned scrap, the killer bunny's, weakened.
     */
    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        if (charge < Holds.MIN_CHARGE || !frenzied(player) || !Puppets.ready2(player) || mauling(player)) {
            return;
        }
        int full = Math.max(1, Config.PUPPETEER_WOLF_MAUL_CHARGE_TICKS.get());
        player.setData(WitchModAttachments.PUPPET_DASH_POWER, (int) Math.round(Math.min(1.0, charge / (double) full) * 100));
        RabbitBehaviour.startMaulLunge(player);
    }

    /** every swing pounces (rabid ones bite harder and faster). */
    @Override
    public void swing(ServerPlayer player, PuppetType type) {
        SpiderBehaviour.pounce(player, type);
    }

    /** a landed swing is this leap's one victim. */
    @Override
    public void dealtDamage(ServerPlayer player, PuppetType type, LivingDamageEvent.Post event) {
        if (event.getSource().getDirectEntity() == player && event.getEntity() != player
                && !Puppets.SPECIAL_DAMAGE.contains(player.getUUID())) {
            SpiderBehaviour.meleeLanded(player);
        }
    }

    /** a wolf's maul's share of full reach from its charge (PUPPET_DASH_POWER). */
    public static double maulShare(Player player) {
        return 0.5 + 0.5 * Math.min(1.0, Math.max(0.0, player.getData(WitchModAttachments.PUPPET_DASH_POWER) / 100.0));
    }

    /** swing: pounce. rabid, hold right-click: maul. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        out.add(PuppetControls.tap(PuppetControls.Key.ATTACK), "witchmod.puppeteer.action.pounce", PuppetControls.ready(player));
        if (player.level().getGameTime() < player.getData(WitchModAttachments.PUPPET_RAGE_END)) {
            out.add(PuppetControls.hold(PuppetControls.Key.USE), "witchmod.puppeteer.action.maul", PuppetControls.ready2(player));
        }
    }

    @Override
    public void lungeContact(ServerPlayer player, PuppetType type, Lunges.Lunge lunge) {
        RabbitBehaviour.maulContact(player);
    }

    /** a rabid wolf hits harder. */
    @Override
    public float finalMeleeDamage(ServerPlayer player, PuppetType type, float amount) {
        return frenzied(player) ? amount * Config.PUPPETEER_WOLF_FRENZY_DAMAGE.get().floatValue() : amount;
    }

    /** a hit sends a wolf rabid. */
    @Override
    public void hurtBy(ServerPlayer player, PuppetType type, DamageSource source, LivingEntity attacker) {
        wolfFrenzy(player, attacker);
    }

    /** feeding a wolf its meat heals it, exactly like a real wolf. */
    @Override
    public boolean interact(Player user, InteractionHand hand, ItemStack stack, ServerPlayer puppet, PuppetType type) {
        if (stack.is(ItemTags.WOLF_FOOD)
                && puppet.getData(WitchModAttachments.PUPPET_HEALTH) < puppet.getData(WitchModAttachments.PUPPET_MAX_HEALTH)) {
            if (!user.level().isClientSide) {
                stack.consume(1, user);
                Puppets.scaleHeal(puppet, Config.PUPPETEER_WOLF_FEED_HEAL.get().floatValue());
                ServerLevel level = puppet.serverLevel();
                level.playSound(null, puppet.blockPosition(), SoundEvents.WOLF_PANT, SoundSource.PLAYERS, 1.0F, 1.2F);
                level.sendParticles(ParticleTypes.HEART, puppet.getX(), puppet.getY() + 0.6, puppet.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
            }
            return true;
        }
        return false;
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        EffectUtil.removeModifier(player, Attributes.ATTACK_SPEED, WOLF_FRENZY_ATTACK_ID);
        WOLF_SCENT.remove(player.getUUID());
        WOLF_PREY.remove(player.getUUID());
    }

    /** every swing pounces (or mauls, rabid). */
    @Override
    public boolean usesLeftClick(PuppetType type) {
        return true;
    }
}
