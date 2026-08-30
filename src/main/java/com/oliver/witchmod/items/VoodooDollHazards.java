package com.oliver.witchmod.items;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.PlayerEssenceData;
import com.oliver.witchmod.data.WitchModDataComponents;

/**
 * "Sympathetic magic" — what happens to a bound Voodoo Doll happens to its owner. A periodic scan catches
 * bound dolls dropped into the world:
 * <ul>
 *   <li>FIRE / LAVA → the bound player bursts into flame (and the doll is destroyed).</li>
 *   <li>POWDER SNOW → the bound player rapidly freezes while the doll sits in it (non-destructive).</li>
 * </ul>
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class VoodooDollHazards {
    private static int clock;

    private VoodooDollHazards() {}

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (++clock < 10) { // ~0.5s
            return;
        }
        clock = 0;
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            List<ItemEntity> doomed = new ArrayList<>();
            for (Entity e : level.getEntities().getAll()) {
                if (!(e instanceof ItemEntity item) || !item.isAlive()) {
                    continue;
                }
                ItemStack stack = item.getItem();
                if (!(stack.getItem() instanceof ItemVoodooDoll) || !stack.has(WitchModDataComponents.BOUND_PLAYER)) {
                    continue;
                }
                PlayerEssenceData bound = stack.get(WitchModDataComponents.BOUND_PLAYER);
                ServerPlayer target = server.getPlayerList().getPlayer(bound.playerId());

                if (item.isInLava() || item.isOnFire()) {
                    if (target != null) {
                        target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(),
                                Config.VOODOO_LAVA_FIRE_TICKS.get()));
                        target.serverLevel().sendParticles(ParticleTypes.FLAME,
                                target.getX(), target.getY() + 1.0, target.getZ(), 30, 0.3, 0.6, 0.3, 0.02);
                        target.serverLevel().playSound(null, target.blockPosition(), SoundEvents.FIRECHARGE_USE,
                                SoundSource.PLAYERS, 0.9F, 1.0F);
                    }
                    burnBurst(level, item);
                    doomed.add(item);
                    continue;
                }

                // LINGERING POTION CLOUD over the doll → its effects land on the victim (a little durability).
                if (target != null) {
                    boolean fromCloud = false;
                    for (net.minecraft.world.entity.AreaEffectCloud cloud : level.getEntitiesOfClass(
                            net.minecraft.world.entity.AreaEffectCloud.class, item.getBoundingBox().inflate(0.5))) {
                        for (net.minecraft.world.effect.MobEffectInstance eff : cloudEffects(cloud)) {
                            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                    eff.getEffect(), Math.max(40, eff.getDuration() / 4), eff.getAmplifier(),
                                    false, true, true));
                        }
                        fromCloud = true;
                    }
                    if (fromCloud) {
                        target.serverLevel().sendParticles(ParticleTypes.EFFECT,
                                target.getX(), target.getY() + 1.0, target.getZ(), 8, 0.3, 0.5, 0.3, 0.0);
                        if (Config.VOODOO_POTION_DOLL_COST.get() > 0) {
                            stack.hurtAndBreak(Config.VOODOO_POTION_DOLL_COST.get(), level, null, it -> {});
                            if (stack.isEmpty()) {
                                doomed.add(item);
                                continue;
                            }
                        }
                    }
                }

                // ARROW shot into the grounded doll → the victim is stuck with the arrow.
                if (target != null) {
                    for (net.minecraft.world.entity.projectile.AbstractArrow arrow : level.getEntitiesOfClass(
                            net.minecraft.world.entity.projectile.AbstractArrow.class, item.getBoundingBox().inflate(0.7),
                            a -> a.isAlive() && a.getDeltaMovement().lengthSqr() > 0.1)) { // fast = still in flight
                        target.hurt(target.serverLevel().damageSources().arrow(arrow, arrow.getOwner()),
                                (float) Math.max(2.0, arrow.getBaseDamage()));
                        target.setArrowCount(target.getArrowCount() + 1); // it visibly sticks out of them
                        target.serverLevel().playSound(null, target.blockPosition(), SoundEvents.ARROW_HIT,
                                SoundSource.PLAYERS, 1.0F, 1.1F);
                        arrow.discard();
                        break;
                    }
                }

                if (target != null && level.getBlockState(item.blockPosition()).is(Blocks.POWDER_SNOW)) {
                    int add = 30;
                    target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 60, target.getTicksFrozen() + add));
                    target.serverLevel().sendParticles(ParticleTypes.SNOWFLAKE,
                            target.getX(), target.getY() + 1.0, target.getZ(), 12, 0.3, 0.5, 0.3, 0.01);
                }

                // WATER / RAIN / water cauldron: the victim goes visibly wet and any fire on them is put out. No cost.
                boolean wet = item.isInWaterOrRain()
                        || level.getBlockState(item.blockPosition()).is(Blocks.WATER_CAULDRON);
                if (target != null && wet) {
                    if (target.isOnFire()) {
                        target.clearFire();
                    }
                    target.serverLevel().sendParticles(ParticleTypes.SPLASH,
                            target.getX(), target.getY() + 1.0, target.getZ(), 10, 0.35, 0.5, 0.35, 0.0);
                    target.serverLevel().sendParticles(ParticleTypes.FALLING_WATER,
                            target.getX(), target.getY() + 1.6, target.getZ(), 6, 0.3, 0.2, 0.3, 0.0);
                }
            }
            for (ItemEntity item : doomed) {
                item.discard();
            }
        }
    }

    // AreaEffectCloud.potionContents is private with no getter, so read it reflectively (cached).
    private static java.lang.reflect.Field cloudPotionField;

    private static Iterable<net.minecraft.world.effect.MobEffectInstance> cloudEffects(
            net.minecraft.world.entity.AreaEffectCloud cloud) {
        try {
            if (cloudPotionField == null) {
                for (java.lang.reflect.Field f : net.minecraft.world.entity.AreaEffectCloud.class.getDeclaredFields()) {
                    if (f.getType() == net.minecraft.world.item.alchemy.PotionContents.class) {
                        f.setAccessible(true);
                        cloudPotionField = f;
                        break;
                    }
                }
            }
            if (cloudPotionField != null) {
                Object pc = cloudPotionField.get(cloud);
                if (pc instanceof net.minecraft.world.item.alchemy.PotionContents contents) {
                    return contents.getAllEffects();
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through to empty
        }
        return java.util.List.of();
    }

    private static void burnBurst(ServerLevel level, ItemEntity item) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, item.getX(), item.getY() + 0.2, item.getZ(), 12, 0.2, 0.2, 0.2, 0.02);
        level.sendParticles(ParticleTypes.FLAME, item.getX(), item.getY() + 0.2, item.getZ(), 16, 0.2, 0.2, 0.2, 0.03);
        level.playSound(null, item.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.2F);
    }
}
