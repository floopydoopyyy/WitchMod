package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.EffectState;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;

/**
 * puppeteer: the bee. slow flight (no sprint-flying), no fall damage; drawn by flowers. left-click STINGS — poison, a
 * stronger dose than vanilla's — and, as for a real bee, it's its one sting: the stinger's gone and the bee dies before
 * long. right-click a flower to gather nectar (you carry the pollen); right-click a hive or nest to go inside it (hidden
 * in the block, looking out, untouchable — the silverfish's hiding) and right-click again to come out; going in with
 * nectar adds a level of honey. hurt, a nearby bee angered, or your hive broken: you're shown who did it (a trail only
 * you see) and get a burst of speed — and real bees nearby take your side. PUPPET_RAGE_END is that rush (synced: the
 * angry look and a faster flight).
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class BeeBehaviour implements PuppetBehaviour {
    public static final BeeBehaviour INSTANCE = new BeeBehaviour();

    private BeeBehaviour() {}

    /** the hive a bee puppet is inside, if it is; and the last hive it went into (its home). */
    private static final Map<UUID, BlockPos> INSIDE = EffectState.sessionConcurrent("puppeteer/bee/inside");
    private static final Map<UUID, BlockPos> HOME = EffectState.sessionConcurrent("puppeteer/bee/home");

    /** ticks since the sting (it's dying), and the last thing it was alerted to (so an alert isn't repeated). */
    private static final Map<UUID, Integer> SINCE_STING = EffectState.sessionConcurrent("puppeteer/bee/since_sting");
    private static final Map<UUID, UUID> ALERTED_TO = EffectState.sessionConcurrent("puppeteer/bee/alerted_to");

    /** where you're hidden, in the saved puppet data (so a crash can't leave you in the hive). */
    private static final String INSIDE_KEY = "witchmod_in_hive";

    public static boolean beePuppet(Player player) {
        return PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.BEE;
    }

    public static boolean hasNectar(Player player) {
        return player.getData(WitchModAttachments.PUPPET_DATA).getBoolean("HasNectar");
    }

    public static boolean hasStung(Player player) {
        return player.getData(WitchModAttachments.PUPPET_DATA).getBoolean("HasStung");
    }

    /** angry: alerted, in its burst of speed (both sides — the model's angry look, the faster flight). */
    public static boolean angry(Player player) {
        return beePuppet(player) && player.level().getGameTime() < player.getData(WitchModAttachments.PUPPET_RAGE_END);
    }

    public static boolean inHive(Player player) {
        return beePuppet(player) && player.getData(WitchModAttachments.PUPPET_HIDDEN);
    }

    private static void setFlag(ServerPlayer player, String key, boolean value) {
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        tag.putBoolean(key, value);
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
    }

    /** the block you're aiming at within reach. */
    @Nullable
    private static BlockPos aimedBlock(Player player) {
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : null;
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        ServerLevel level = player.serverLevel();
        FarmAnimalBehaviour.lure(player, type); // flowers draw it in
        // its one sting spent, it's dying: weaker and weaker, then gone
        if (hasStung(player)) {
            int since = SINCE_STING.merge(player.getUUID(), 1, Integer::sum);
            int life = Math.max(20, Config.PUPPETEER_BEE_STING_LIFE_TICKS.get());
            if (since >= life) {
                Possession.release(player, player.damageSources().generic(), false); // (the bee comes back only to die)
                return;
            }
            if (since % 20 == 0) {
                float max = player.getData(WitchModAttachments.PUPPET_MAX_HEALTH);
                float left = max * (1.0F - since / (float) life);
                if (left < player.getData(WitchModAttachments.PUPPET_HEALTH)) {
                    player.setData(WitchModAttachments.PUPPET_HEALTH, Math.max(0.5F, left));
                    player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR,
                            player.getX(), player.getY() + 0.5, player.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
                }
            }
        }
        // inside a hive: held there, out of sight; out you come if the hive goes
        if (player.getData(WitchModAttachments.PUPPET_HIDDEN)) {
            BlockPos hive = INSIDE.computeIfAbsent(player.getUUID(), k -> {
                CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
                return tag.contains(INSIDE_KEY) ? BlockPos.of(tag.getLong(INSIDE_KEY)) : player.blockPosition();
            });
            if (!level.getBlockState(hive).is(BlockTags.BEEHIVES)) {
                leaveHive(player);
            } else {
                Vec3 spot = Vec3.atBottomCenterOf(hive);
                if (player.position().distanceToSqr(spot) > 0.01) {
                    player.teleportTo(spot.x, spot.y, spot.z);
                }
                player.setDeltaMovement(Vec3.ZERO);
            }
        }
        // a real bee nearby gone angry at something: you're told, and rush to it
        if (player.tickCount % 20 == 0) {
            double r = Config.PUPPETEER_BEE_ALERT_RADIUS.get();
            for (Bee bee : level.getEntitiesOfClass(Bee.class, player.getBoundingBox().inflate(r),
                    b -> b.isAlive() && b.isAngry() && b.getTarget() != null && b.getTarget() != player)) {
                rouse(player, bee.getTarget(), "witchmod.puppeteer.bee_alert_swarm");
                break;
            }
        }
    }

    /**
     * shown who it is (a trail only you see), a burst of speed, and the angry look — once per new culprit while the
     * rush lasts.
     */
    private static void rouse(ServerPlayer player, LivingEntity culprit, String why) {
        long now = player.level().getGameTime();
        boolean fresh = !angry(player) || !culprit.getUUID().equals(ALERTED_TO.get(player.getUUID()));
        int ticks = Config.PUPPETEER_BEE_RAGE_TICKS.get();
        player.setData(WitchModAttachments.PUPPET_RAGE_END, now + ticks);
        if (!fresh) {
            return;
        }
        ALERTED_TO.put(player.getUUID(), culprit.getUUID());
        Puppets.alert(player, culprit, ticks, new Vector3f(1.0F, 0.85F, 0.1F));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, Config.PUPPETEER_BEE_RAGE_SPEED_LEVEL.get() - 1, false, false, true));
        player.displayClientMessage(Component.translatable(why, culprit.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEE_LOOP_AGGRESSIVE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * right-click: inside a hive, come out; aiming at a hive or nest, go in (adding honey if you carry nectar); at a
     * flower, gather its nectar.
     */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        if (inHive(player)) {
            leaveHive(player);
            return;
        }
        BlockPos at = aimedBlock(player);
        if (at == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(at);
        if (state.is(BlockTags.BEEHIVES) && level.getBlockEntity(at) instanceof BeehiveBlockEntity hive && !hive.isFull()) {
            enterHive(player, at, state);
        } else if (state.is(BlockTags.FLOWERS) && !hasNectar(player)) {
            setFlag(player, "HasNectar", true);
            level.playSound(null, at, SoundEvents.BEE_POLLINATE, SoundSource.PLAYERS, 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.FALLING_NECTAR, at.getX() + 0.5, at.getY() + 0.8, at.getZ() + 0.5, 6, 0.25, 0.2, 0.25, 0.0);
            Puppets.countAction(player);
        }
    }

    /** into the hive: hidden inside the block (you can still look out); with nectar, a level more honey. */
    private static void enterHive(ServerPlayer player, BlockPos at, BlockState state) {
        ServerLevel level = player.serverLevel();
        if (hasNectar(player) && state.getBlock() instanceof BeehiveBlock && state.hasProperty(BeehiveBlock.HONEY_LEVEL)) {
            int honey = state.getValue(BeehiveBlock.HONEY_LEVEL);
            if (honey < BeehiveBlock.MAX_HONEY_LEVELS) {
                level.setBlock(at, state.setValue(BeehiveBlock.HONEY_LEVEL, honey + 1), 3);
            }
            setFlag(player, "HasNectar", false);
        }
        Vec3 spot = Vec3.atBottomCenterOf(at);
        player.teleportTo(spot.x, spot.y, spot.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.setInvisible(true);
        player.setData(WitchModAttachments.PUPPET_HIDDEN, true);
        INSIDE.put(player.getUUID(), at);
        HOME.put(player.getUUID(), at);
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        tag.putLong(INSIDE_KEY, at.asLong());
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        level.playSound(null, at, SoundEvents.BEEHIVE_ENTER, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** out of the hive, out of its front (wherever it faces). */
    private static void leaveHive(ServerPlayer player) {
        BlockPos hive = INSIDE.remove(player.getUUID());
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        if (hive == null && tag.contains(INSIDE_KEY)) {
            hive = BlockPos.of(tag.getLong(INSIDE_KEY));
        }
        tag.remove(INSIDE_KEY);
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        player.setInvisible(false);
        player.setData(WitchModAttachments.PUPPET_HIDDEN, false);
        if (hive == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(hive);
        BlockPos out = state.hasProperty(BeehiveBlock.FACING) ? hive.relative(state.getValue(BeehiveBlock.FACING)) : hive.above();
        Vec3 spot = Vec3.atBottomCenterOf(out);
        player.teleportTo(spot.x, spot.y, spot.z);
        level.playSound(null, hive, SoundEvents.BEEHIVE_EXIT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** inside a hive nothing can reach you. */
    @Override
    public boolean untargetable(ServerPlayer player, PuppetType type) {
        return player.getData(WitchModAttachments.PUPPET_HIDDEN);
    }

    /** no fall damage (it flies); nothing gets at it inside a hive. */
    @Override
    public void incomingDamage(ServerPlayer player, PuppetType type, LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypeTags.IS_FALL)
                || player.getData(WitchModAttachments.PUPPET_HIDDEN) && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    /** hit: shown who, a rush of speed — and the bees nearby take your side, as they do for each other. */
    @Override
    public void hurtBy(ServerPlayer player, PuppetType type, DamageSource source, LivingEntity attacker) {
        rouse(player, attacker, "witchmod.puppeteer.bee_alert_hit");
        double r = Config.PUPPETEER_BEE_ALERT_RADIUS.get();
        for (Bee bee : player.serverLevel().getEntitiesOfClass(Bee.class, player.getBoundingBox().inflate(r),
                b -> b.isAlive() && !b.hasStung() && b != attacker)) {
            bee.setPersistentAngerTarget(attacker.getUUID());
            bee.startPersistentAngerTimer();
            bee.setTarget(attacker);
        }
    }

    /** the sting: a stronger poison than vanilla's — and it's the bee's last (its stinger's gone; it dies soon after). */
    @Override
    public void dealtDamage(ServerPlayer player, PuppetType type, LivingDamageEvent.Post event) {
        if (event.getSource().getDirectEntity() != player || event.getEntity() == player || SPECIAL_DAMAGE.contains(player.getUUID())) {
            return;
        }
        event.getEntity().addEffect(new MobEffectInstance(MobEffects.POISON, Config.PUPPETEER_BEE_POISON_TICKS.get(),
                Config.PUPPETEER_BEE_POISON_LEVEL.get() - 1), player);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEE_STING, SoundSource.PLAYERS, 1.0F, 1.0F);
        setFlag(player, "HasStung", true);
    }

    /** a bee only stings once; and never from inside its hive. */
    @Override
    public boolean blocksMelee(Player player, PuppetType type, Entity target) {
        return hasStung(player) || player.getData(WitchModAttachments.PUPPET_HIDDEN);
    }

    /** its hive broken: whoever did it is shown to every bee puppet that calls it home (and one inside comes out). */
    @SubscribeEvent
    static void onHiveBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !event.getState().is(BlockTags.BEEHIVES)) {
            return;
        }
        for (ServerPlayer bee : level.players()) {
            if (beePuppet(bee) && event.getPos().equals(HOME.get(bee.getUUID())) && bee != event.getPlayer()) {
                if (event.getPos().equals(INSIDE.get(bee.getUUID()))) {
                    leaveHive(bee);
                }
                rouse(bee, event.getPlayer(), "witchmod.puppeteer.bee_alert_hive");
            }
        }
    }

    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        Puppets.grantBatFlight(player);
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        if (beePuppet(player) && (player.getData(WitchModAttachments.PUPPET_HIDDEN) || data.contains(INSIDE_KEY))) {
            leaveHive(player);
            data.remove(INSIDE_KEY);
        }
        INSIDE.remove(player.getUUID());
        HOME.remove(player.getUUID());
        SINCE_STING.remove(player.getUUID());
        ALERTED_TO.remove(player.getUUID());
    }

    /** sting; and right-click — out of the hive, into one, or a flower's nectar. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        if (player.getData(WitchModAttachments.PUPPET_HIDDEN)) {
            out.add(tap(Key.USE), "witchmod.puppeteer.action.bee_leave_hive", 0L);
            return;
        }
        BlockPos at = aimedBlock(player);
        BlockState state = at == null ? null : player.level().getBlockState(at);
        if (state != null && state.is(BlockTags.BEEHIVES)) {
            out.add(tap(Key.USE), hasNectar(player) ? "witchmod.puppeteer.action.bee_deliver" : "witchmod.puppeteer.action.bee_enter_hive", 0L);
        } else if (state != null && state.is(BlockTags.FLOWERS) && !hasNectar(player)) {
            out.add(tap(Key.USE), "witchmod.puppeteer.action.bee_pollinate", 0L);
        }
        if (!hasStung(player)) {
            out.add(tap(Key.ATTACK), "witchmod.puppeteer.action.bee_sting", 0L);
        } else {
            out.note(Component.translatable("witchmod.puppeteer.bee_dying").withStyle(ChatFormatting.RED));
        }
    }
}
