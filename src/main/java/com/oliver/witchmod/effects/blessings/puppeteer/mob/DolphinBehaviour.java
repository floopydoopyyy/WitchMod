package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.EndermiteBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.FishBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the dolphin puppet. */
public final class DolphinBehaviour implements PuppetBehaviour {
    public static final DolphinBehaviour INSTANCE = new DolphinBehaviour();

    private DolphinBehaviour() {}

    // --- dolphin / axolotl --------------------------------------------------------------------------

    /** a dolphin / axolotl puppet's moisture (ticks left out of water before it dries out). */
    private static final Map<UUID, Integer> MOISTURE = EffectState.sessionConcurrent("puppeteer/dolphin/moisture");

    /** out of water (rain counts as water) the moisture runs down, then you dry out: the puppet takes dryOut damage. */
    public static void tickMoisture(ServerPlayer player, int max) {
        if (player.isInWaterRainOrBubble()) {
            MOISTURE.put(player.getUUID(), max);
            return;
        }
        int left = MOISTURE.getOrDefault(player.getUUID(), max) - 1;
        MOISTURE.put(player.getUUID(), left);
        if (left <= 0 && left % 10 == 0) {
            player.hurt(player.damageSources().dryOut(), 1.0F);
        }
    }

    /**
     * dolphins: very fast swimmers that breathe AIR — underwater your air lasts 16x as long (a real dolphin's 4800
     * ticks) but you must surface. on land you're slow and flop about, and eventually dry out. you eat fish: swim
     * into a dropped one, or be fed one.
     */
    private static void tickDolphin(ServerPlayer player) {
        boolean wet = player.isInWater();
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && wet == speed.hasModifier(FISH_LAND_ID)) {
            if (wet) {
                speed.removeModifier(FISH_LAND_ID);
            } else {
                EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, FISH_LAND_ID, -Config.PUPPETEER_DOLPHIN_LAND_SLOW.get(),
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            }
        }
        // vanilla takes 1 air a tick underwater; give 15 of every 16 back.
        if (player.isUnderWater() && player.getAirSupply() < player.getMaxAirSupply() && player.tickCount % 16 != 0) {
            player.setAirSupply(player.getAirSupply() + 1);
        }
        tickMoisture(player, Config.PUPPETEER_DOLPHIN_MOISTURE_TICKS.get());
        if (!wet && player.onGround() && !player.getAbilities().flying) {
            player.setDeltaMovement(player.getDeltaMovement().add((player.getRandom().nextFloat() * 2.0F - 1.0F) * 0.2,
                    0.5, (player.getRandom().nextFloat() * 2.0F - 1.0F) * 0.2)); // a beached dolphin's hop (vanilla)
            player.hurtMarked = true;
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.DOLPHIN_JUMP, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        // eat any fish you swim into, like a real dolphin picking one up.
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(0.8),
                i -> i.isAlive() && i.getItem().is(ItemTags.FISHES))) {
            item.getItem().shrink(1);
            if (item.getItem().isEmpty()) {
                item.discard();
            }
            dolphinEat(player);
            break;
        }
    }

    private static void dolphinEat(ServerPlayer dolphin) {
        scaleHeal(dolphin, Config.PUPPETEER_DOLPHIN_FISH_HEAL.get().floatValue());
        dolphin.getFoodData().eat(2, 0.3F);
        dolphin.serverLevel().playSound(null, dolphin.blockPosition(), SoundEvents.DOLPHIN_EAT, SoundSource.PLAYERS, 1.0F, 1.0F);
        dolphin.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, dolphin.getX(), dolphin.getY() + 0.5, dolphin.getZ(),
                3, 0.3, 0.2, 0.3, 0.0);
    }

    /** dolphin: Inspire — every player and water creature near you gets Dolphin's Grace (fast swimming). */
    private static void inspire(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double r = Config.PUPPETEER_DOLPHIN_INSPIRE_RADIUS.get();
        int ticks = Config.PUPPETEER_DOLPHIN_INSPIRE_SECONDS.get() * 20;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r),
                e -> e.isAlive() && e.distanceToSqr(player) <= r * r
                        && (e instanceof Player || e instanceof net.minecraft.world.entity.animal.WaterAnimal
                        || e instanceof net.minecraft.world.entity.animal.axolotl.Axolotl))) {
            e.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, ticks, 0), player);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.DOLPHIN_PLAY, SoundSource.PLAYERS, 1.2F, 1.0F);
        level.sendParticles(ParticleTypes.DOLPHIN, player.getX(), player.getY() + 0.5, player.getZ(), 12, 0.6, 0.4, 0.6, 0.0);
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickDolphin(player);
    }

    /** inspire: Dolphin's Grace to the players and water mobs around. */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        if (!Puppets.ready(player)) {
            return;
        }
        Puppets.cooldown(player, Config.PUPPETEER_DOLPHIN_INSPIRE_COOLDOWN_TICKS.get());
        inspire(player);
        Puppets.countAction(player);
    }

    /** feeding a dolphin, like the real thing. */
    @Override
    public boolean interact(Player user, InteractionHand hand, ItemStack stack, ServerPlayer puppet, PuppetType type) {
        if (stack.is(ItemTags.FISHES)) {
            if (!user.level().isClientSide) {
                stack.consume(1, user);
                dolphinEat(puppet);
            }
            return true;
        }
        return false;
    }

    /** (the axolotl's moisture too) */
    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        MOISTURE.remove(player.getUUID());
    }
}
