package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.VillagerBehaviour.*;

/** puppeteer: the zombie puppet. */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class ZombieBehaviour implements PuppetBehaviour {
    public static final ZombieBehaviour INSTANCE = new ZombieBehaviour();

    private ZombieBehaviour() {}

    /** live drowned-puppet tridents → their thrower; they vanish quickly and rally drowned onto what they hit. */
    public static final Map<ThrownTrident, UUID> TRIDENTS = new ConcurrentHashMap<>();

    /**
     * zombies: a groan that MARKS whoever you're looking at (within puppeteerZombieRallyRange) — it glows, and every
     * zombie variant near you goes after it with a speed boost. with none around and dark enough, 1-2 of your own
     * kind claw up to help.
     */
    public static void rally(ServerPlayer player, PuppetType type) {
        ServerLevel level = player.serverLevel();
        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, player.blockPosition(), ambientOf(type), SoundSource.PLAYERS, 2.5F, 0.55F);
        LivingEntity prey = lookTarget(player, Config.PUPPETEER_ZOMBIE_RALLY_RANGE.get());
        boolean dark = level.getMaxLocalRawBrightness(player.blockPosition()) <= Config.PUPPETEER_ZOMBIE_RALLY_SPAWN_LIGHT.get();
        answerCall(level, player, prey, player.position(), Zombie.class, type.entityType(), dark, false);
    }

    /** a drowned's trident: whatever it hits is marked for nearby drowned, who rise from dark or water if none are around. */
    private static void tridentRally(ServerPlayer thrower, LivingEntity hit) {
        ServerLevel level = thrower.serverLevel();
        BlockPos leaderAt = thrower.blockPosition();
        boolean dark = level.getMaxLocalRawBrightness(leaderAt) <= Config.PUPPETEER_ZOMBIE_RALLY_SPAWN_LIGHT.get();
        boolean wet = thrower.isInWater() || BlockPos.betweenClosedStream(leaderAt.offset(-3, -2, -3), leaderAt.offset(3, 1, 3))
                .anyMatch(p -> level.getFluidState(p).is(FluidTags.WATER));
        // raised near YOU (the leader), not the target — like the other undead rallies.
        answerCall(level, thrower, hit, thrower.position(), Drowned.class, EntityType.DROWNED, dark || wet, true);
    }

    public static void infect(ServerLevel level, Villager villager) {
        if (!EventHooks.canLivingConvert(villager, EntityType.ZOMBIE_VILLAGER, timer -> {})) {
            return;
        }
        ZombieVillager zombie = villager.convertTo(EntityType.ZOMBIE_VILLAGER, false);
        if (zombie == null) {
            return;
        }
        zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(zombie.blockPosition()), MobSpawnType.CONVERSION,
                new Zombie.ZombieGroupData(false, true));
        zombie.setVillagerData(villager.getVillagerData());
        zombie.setGossips(villager.getGossips().store(NbtOps.INSTANCE));
        zombie.setTradeOffers(villager.getOffers().copy());
        zombie.setVillagerXp(villager.getVillagerXp());
        EventHooks.onLivingConvert(villager, zombie);
        level.levelEvent(null, 1026, zombie.blockPosition(), 0);
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        if (type == PuppetType.DROWNED) {
            Holds.tickHold(player, type);
            player.setAirSupply(player.getMaxAirSupply()); // drowned don't drown
        }
    }

    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        if (!Puppets.ready(player)) {
            return;
        }
        Puppets.cooldown(player, Config.PUPPETEER_ZOMBIE_RALLY_COOLDOWN_TICKS.get());
        rally(player, type);
        Puppets.countAction(player);
    }

    @Override
    public void chargeStarted(ServerPlayer player, PuppetType type) {
        if (type == PuppetType.DROWNED) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.6F, 1.4F);
        }
    }

    /** a drowned's held right-click: the trident throw. */
    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        if (type == PuppetType.DROWNED) {
            if (charge < Holds.MIN_CHARGE || !Puppets.ready(player)) {
                return;
            }
            ServerLevel level = player.serverLevel();
            ThrownTrident trident = new ThrownTrident(level, player, new ItemStack(Items.TRIDENT));
            trident.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 2.5F, 1.0F);
            trident.pickup = AbstractArrow.Pickup.DISALLOWED;
            level.addFreshEntity(trident);
            TRIDENTS.put(trident, player.getUUID());
            level.playSound(null, trident, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
            player.swing(InteractionHand.MAIN_HAND, true);
            Puppets.cooldown(player, Config.PUPPETEER_DROWNED_TRIDENT_COOLDOWN_TICKS.get());
        }
        Puppets.countAction(player);
    }

    @Override
    public float meleeDamage(ServerPlayer player, PuppetType type, @Nullable Float swingStrength) {
        return type == PuppetType.DROWNED
                ? Config.PUPPETEER_DROWNED_MELEE_DAMAGE.get().floatValue() // its trident jab, not a bare zombie slap
                : type.attackDamage();
    }

    /** husk hits leave the victim hungry, like a real husk's. */
    @Override
    public void dealtDamage(ServerPlayer player, PuppetType type, LivingDamageEvent.Post event) {
        if (type == PuppetType.HUSK && event.getSource().getDirectEntity() == player && event.getNewDamage() > 0.0F) {
            int seconds = Config.PUPPETEER_HUSK_HUNGER_SECONDS.get();
            if (seconds > 0) {
                event.getEntity().addEffect(new MobEffectInstance(MobEffects.HUNGER, seconds * 20), player);
            }
        }
    }

    /** a zombie puppet's kills spread the infection: a villager it kills rises as a zombie villager. */
    @Override
    public void killed(ServerPlayer player, PuppetType type, LivingEntity victim) {
        if (victim instanceof Villager villager && player.level() instanceof ServerLevel level) {
            infect(level, villager);
        }
    }

    /** a drowned's trident: whatever it hits, the drowned around rally onto (even if its thrower's stepped out since). */
    @SubscribeEvent
    public static void onTridentHit(LivingDamageEvent.Post event) {
        if (event.getSource().getDirectEntity() instanceof ThrownTrident trident) {
            UUID owner = TRIDENTS.get(trident);
            if (owner != null && trident.level() instanceof ServerLevel level
                    && level.getPlayerByUUID(owner) instanceof ServerPlayer thrower && event.getEntity() != thrower) {
                tridentRally(thrower, event.getEntity());
            }
        }
    }
}
