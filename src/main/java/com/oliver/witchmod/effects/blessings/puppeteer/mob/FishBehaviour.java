package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the fish puppet. */
public final class FishBehaviour implements PuppetBehaviour {
    public static final FishBehaviour INSTANCE = new FishBehaviour();

    private FishBehaviour() {}

    public static final ResourceLocation FISH_LAND_ID = EffectUtil.modifierId("puppeteer_fish_land");

    /** a fish puppet's breath out of water (its own counter — vanilla refills a player's air on land). */
    private static final Map<UUID, Integer> FISH_AIR = EffectState.sessionConcurrent("puppeteer/fish/fish_air");

    /**
     * fish: in water you're quick and never short of air; out of it you can barely move, flop about like a landed
     * fish (vanilla cadence, the disguise's flop), and your air runs out until you dry out.
     */
    public static void tickFish(ServerPlayer player, PuppetType type) {
        boolean wet = player.isInWater();
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && wet == speed.hasModifier(FISH_LAND_ID)) {
            if (wet) {
                speed.removeModifier(FISH_LAND_ID);
            } else {
                EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, FISH_LAND_ID, -Config.PUPPETEER_FISH_LAND_SLOW.get(),
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            }
        }
        if (wet) {
            FISH_AIR.put(player.getUUID(), player.getMaxAirSupply());
            player.setAirSupply(player.getMaxAirSupply());
            return;
        }
        int air = FISH_AIR.getOrDefault(player.getUUID(), player.getMaxAirSupply()) - 1;
        if (air <= -20) {
            air = 0;
            player.hurt(player.damageSources().dryOut(), 2.0F);
        }
        FISH_AIR.put(player.getUUID(), air);
        player.setAirSupply(air);
        if (type.group() == PuppetGroup.FISH && player.onGround() && !player.getAbilities().flying) {
            double power = Config.FISH_FLOP_POWER.get();
            player.setDeltaMovement(player.getDeltaMovement().add((player.getRandom().nextFloat() * 2.0F - 1.0F) * 0.05,
                    power, (player.getRandom().nextFloat() * 2.0F - 1.0F) * 0.05));
            player.hurtMarked = true;
            player.serverLevel().playSound(null, player.blockPosition(), flopOf(type), SoundSource.PLAYERS, 0.9F,
                    0.9F + player.getRandom().nextFloat() * 0.25F);
        }
    }

    /** fish: Leap — out of water a big arcing launch where you're looking; in water a quick straight dash. */
    public static void leap(ServerPlayer player, PuppetType type) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        if (player.isInWater()) {
            player.setDeltaMovement(look.scale(Config.PUPPETEER_FISH_DASH_POWER.get()));
            level.sendParticles(ParticleTypes.BUBBLE, player.getX(), player.getY() + 0.3, player.getZ(), 12, 0.3, 0.2, 0.3, 0.1);
            level.playSound(null, player.blockPosition(), SoundEvents.DOLPHIN_SWIM, SoundSource.PLAYERS, 1.0F, 1.4F);
        } else {
            Vec3 flat = new Vec3(look.x, 0.0, look.z);
            flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(Config.PUPPETEER_FISH_LEAP_POWER.get());
            player.setDeltaMovement(flat.x, Config.PUPPETEER_FISH_LEAP_LIFT.get(), flat.z);
            level.playSound(null, player.blockPosition(), flopOf(type), SoundSource.PLAYERS, 1.2F, 0.7F);
        }
        player.hurtMarked = true;
    }

    // --- pufferfish: puffing up (PUPPET_DASH_POWER is the puff level, 0-2 — synced for the dummy's look) ---

    /** a pufferfish's puff timers: ticks held puffed, and ticks since it let go. */
    private static final Map<UUID, int[]> PUFF = EffectState.sessionConcurrent("puppeteer/fish/puff");

    public static int puffLevel(net.minecraft.world.entity.player.Player player) {
        return PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.PUFFERFISH
                ? player.getData(WitchModAttachments.PUPPET_DASH_POWER) : 0;
    }

    /**
     * the vanilla puff, on right-click held: half-puffed at once, full a couple of seconds in; let go and it sags back
     * in two steps. puffed, anything touching you is stung — damage and poison by puff level, as the real fish does.
     */
    private static void tickPuff(ServerPlayer player, PuppetType type) {
        Holds.tickHold(player, type);
        int[] timers = PUFF.computeIfAbsent(player.getUUID(), k -> new int[2]);
        int level = player.getData(WitchModAttachments.PUPPET_DASH_POWER);
        boolean holding = Holds.HELD.contains(player.getUUID()) && player.getData(WitchModAttachments.PUPPET_FUSE) >= Holds.MIN_CHARGE;
        int next = level;
        if (holding) {
            timers[1] = 0;
            timers[0]++;
            if (level == 0) {
                next = 1;
            } else if (level == 1 && timers[0] > Config.PUPPETEER_PUFFERFISH_PUFF_FULL_TICKS.get()) {
                next = 2;
            }
        } else if (level > 0) {
            timers[0] = 0;
            timers[1]++;
            if (level == 2 && timers[1] > Config.PUPPETEER_PUFFERFISH_DEFLATE_HALF_TICKS.get()) {
                next = 1;
            } else if (level == 1 && timers[1] > Config.PUPPETEER_PUFFERFISH_DEFLATE_TICKS.get()) {
                next = 0;
            }
        }
        if (next != level) {
            player.setData(WitchModAttachments.PUPPET_DASH_POWER, next);
            player.serverLevel().playSound(null, player.blockPosition(),
                    next > level ? SoundEvents.PUFFER_FISH_BLOW_UP : SoundEvents.PUFFER_FISH_BLOW_OUT, SoundSource.PLAYERS, 1.0F, 1.0F);
            level = next;
        }
        if (level > 0) {
            stingTouching(player, level);
        }
    }

    /** whatever's touching a puffed pufferfish is stung (as vanilla's touch / playerTouch). */
    private static void stingTouching(ServerPlayer player, int level) {
        ServerLevel world = player.serverLevel();
        float damage = Config.PUPPETEER_PUFFERFISH_STING_DAMAGE.get().floatValue() + (level - 1);
        int poison = Config.PUPPETEER_PUFFERFISH_STING_POISON_TICKS.get() * level;
        for (net.minecraft.world.entity.LivingEntity e : world.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                player.getBoundingBox().inflate(0.3), e -> e != player && e.isAlive() && !e.isSpectator()
                        && !e.getType().is(net.minecraft.tags.EntityTypeTags.NOT_SCARY_FOR_PUFFERFISH)
                        && !(e instanceof net.minecraft.world.entity.player.Player p && p.isCreative()))) {
            boolean hurt;
            SPECIAL_DAMAGE.add(player.getUUID());
            try {
                hurt = e.hurt(player.damageSources().playerAttack(player), damage);
            } finally {
                SPECIAL_DAMAGE.remove(player.getUUID());
            }
            if (!hurt) {
                continue;
            }
            if (poison > 0) {
                e.addEffect(new MobEffectInstance(MobEffects.POISON, poison, 0), player);
            }
            if (e instanceof ServerPlayer stung) {
                stung.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                        net.minecraft.network.protocol.game.ClientboundGameEventPacket.PUFFER_FISH_STING, 0.0F));
            } else {
                world.playSound(null, e.blockPosition(), SoundEvents.PUFFER_FISH_STING, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickFish(player, type);
        FarmAnimalBehaviour.lure(player, type); // a tadpole follows slimeballs
        if (type == PuppetType.PUFFERFISH) {
            tickPuff(player, type);
        }
    }

    /** leap / dash. */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        tryLeap(player, type);
    }

    private static void tryLeap(ServerPlayer player, PuppetType type) {
        if (type.move() == PuppetMove.NONE || !Puppets.ready(player)) { // (a tadpole has no leap)
            return;
        }
        Puppets.cooldown(player, Config.PUPPETEER_FISH_LEAP_COOLDOWN_TICKS.get());
        leap(player, type);
        Puppets.countAction(player);
    }

    /** a tadpole can't bite. */
    @Override
    public boolean dealsMelee(ServerPlayer player, PuppetType type) {
        return type != PuppetType.TADPOLE;
    }

    /** a pufferfish can puff up whatever its leap's cooldown. */
    @Override
    public boolean canCharge(ServerPlayer player, PuppetType type) {
        return true;
    }

    /** a pufferfish's tap is the leap; a hold was the puff (it sags back on its own). */
    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        if (charge < Holds.MIN_CHARGE) {
            tryLeap(player, type);
        }
    }

    /** a pufferfish: tap to leap, hold to puff up. */
    @Override
    public void controls(net.minecraft.world.entity.player.Player player, PuppetType type, PuppetControls out) {
        if (type != PuppetType.PUFFERFISH) {
            out.addMove(player, type);
            return;
        }
        out.add(PuppetControls.tap(PuppetControls.Key.USE), PuppetMove.LEAP.labelKey(), PuppetControls.ready(player));
        out.add(PuppetControls.hold(PuppetControls.Key.USE), PuppetMove.PUFF.labelKey(), 0L);
    }

    /** a fish just flops: no fall damage. */
    @Override
    public void incomingDamage(ServerPlayer player, PuppetType type, LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypeTags.IS_FALL)) {
            event.setCanceled(true);
        }
    }

    /** a pufferfish's sting poisons. */
    @Override
    public void dealtDamage(ServerPlayer player, PuppetType type, LivingDamageEvent.Post event) {
        if (type == PuppetType.PUFFERFISH && event.getSource().getDirectEntity() == player) {
            int seconds = Config.PUPPETEER_PUFFERFISH_POISON_SECONDS.get();
            if (seconds > 0) {
                event.getEntity().addEffect(new MobEffectInstance(MobEffects.POISON, seconds * 20), player);
            }
        }
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        FISH_AIR.remove(player.getUUID());
        PUFF.remove(player.getUUID());
        // (the land slow is shared with the guardian and dolphin)
        com.oliver.witchmod.data.EffectUtil.removeModifier(player, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, FISH_LAND_ID);
    }
}
