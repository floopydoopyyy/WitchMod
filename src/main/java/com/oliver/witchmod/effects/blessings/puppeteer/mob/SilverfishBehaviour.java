package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Holds.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.GuardianBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.ZombieBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the silverfish puppet. */
public final class SilverfishBehaviour implements PuppetBehaviour {
    public static final SilverfishBehaviour INSTANCE = new SilverfishBehaviour();

    private SilverfishBehaviour() {}

    // --- silverfish ---------------------------------------------------------------------------------

    /** a silverfish puppet burrowing in: the block it's going into. */
    private static final Map<UUID, BlockPos> EMBEDDING = EffectState.sessionConcurrent("puppeteer/silverfish/embedding");

    /** a silverfish puppet hidden in stone: the block, and since when. */
    public record Hideout(BlockPos pos, long since) {}

    public static final Map<UUID, Hideout> HIDEOUTS = EffectState.sessionConcurrent("puppeteer/silverfish/hideouts");

    /** where a hidden silverfish is, in its saved puppet data — so even a crash can't leave you entombed (see unhide). */
    private static final String HIDDEN_KEY = "witchmod_hidden";

    public static boolean hidden(Player player) {
        return player.getData(WitchModAttachments.PUPPET_HIDDEN);
    }

    /**
     * silverfish right-click. hidden: BURST out. looking at stone it could infest (within reach): EMBED — burrow in over
     * puppeteerSilverfishEmbedTicks. otherwise: CALL reinforcements.
     */
    private static void silverfishAction(ServerPlayer player) {
        if (hidden(player)) {
            burst(player);
            return;
        }
        if (EMBEDDING.containsKey(player.getUUID())) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        net.minecraft.world.phys.BlockHitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(eye,
                eye.add(player.getLookAngle().scale(player.blockInteractionRange())),
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && net.minecraft.world.level.block.InfestedBlock.isCompatibleHostBlock(player.level().getBlockState(hit.getBlockPos()))) {
            EMBEDDING.put(player.getUUID(), hit.getBlockPos());
            player.setData(WitchModAttachments.PUPPET_FUSE, 1);
            return;
        }
        if (ready(player)) {
            callSilverfish(player);
        }
    }

    private static void tickSilverfish(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos burrow = EMBEDDING.get(player.getUUID());
        if (burrow != null) {
            BlockState state = level.getBlockState(burrow);
            if (!net.minecraft.world.level.block.InfestedBlock.isCompatibleHostBlock(state)
                    || player.distanceToSqr(Vec3.atCenterOf(burrow)) > 36.0) {
                EMBEDDING.remove(player.getUUID()); // the stone went (or you did)
                player.setData(WitchModAttachments.PUPPET_FUSE, 0);
                return;
            }
            int fuse = player.getData(WitchModAttachments.PUPPET_FUSE) + chargeStep(player);
            player.setData(WitchModAttachments.PUPPET_FUSE, fuse);
            if (fuse % 3 == 0) {
                Vec3 c = Vec3.atCenterOf(burrow);
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state),
                        c.x, c.y, c.z, 4, 0.3, 0.3, 0.3, 0.05);
                level.playSound(null, burrow, SoundEvents.SILVERFISH_STEP, SoundSource.PLAYERS, 0.6F, 1.0F);
            }
            if (fuse >= Config.PUPPETEER_SILVERFISH_EMBED_TICKS.get()) {
                hide(player, burrow, state);
            }
            return;
        }
        Hideout hideout = HIDEOUTS.get(player.getUUID());
        if (hideout == null) {
            return;
        }
        if (!(level.getBlockState(hideout.pos()).getBlock() instanceof net.minecraft.world.level.block.InfestedBlock)) {
            burst(player); // someone broke (or changed) the block you're in — out you come
            return;
        }
        Vec3 spot = Vec3.atBottomCenterOf(hideout.pos());
        if (player.position().distanceToSqr(spot) > 0.01) {
            player.teleportTo(spot.x, spot.y, spot.z);
        }
        // the hud counts the brood off this (capped, so it stops changing once the brood is full)
        int embed = Config.PUPPETEER_SILVERFISH_EMBED_TICKS.get();
        int cap = embed + Config.PUPPETEER_SILVERFISH_BROOD_TICKS.get() * Config.PUPPETEER_SILVERFISH_BROOD_MAX.get();
        int fuse = (int) Math.min(cap, embed + level.getGameTime() - hideout.since());
        if (fuse != player.getData(WitchModAttachments.PUPPET_FUSE)) {
            player.setData(WitchModAttachments.PUPPET_FUSE, fuse);
        }
    }

    /** into the stone: it becomes its infested twin, and you vanish inside it (still able to look out). */
    public static void hide(ServerPlayer player, BlockPos pos, BlockState host) {
        ServerLevel level = player.serverLevel();
        EMBEDDING.remove(player.getUUID());
        level.setBlock(pos, net.minecraft.world.level.block.InfestedBlock.infestedStateByHost(host), 3);
        Vec3 spot = Vec3.atBottomCenterOf(pos);
        player.teleportTo(spot.x, spot.y, spot.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.setInvisible(true);
        player.setData(WitchModAttachments.PUPPET_HIDDEN, true);
        HIDEOUTS.put(player.getUUID(), new Hideout(pos, level.getGameTime()));
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        tag.putLong(HIDDEN_KEY, pos.asLong());
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        level.playSound(null, pos, SoundEvents.SILVERFISH_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    /**
     * burst out of the stone: it shatters, and the longer you were in it the bigger the brood that comes out with you
     * (one per puppeteerSilverfishBroodTicks, up to puppeteerSilverfishBroodMax), all of you with a burst of speed.
     */
    public static void burst(ServerPlayer player) {
        Hideout hideout = HIDEOUTS.remove(player.getUUID());
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        unhide(player, tag);
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        if (hideout == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        int brood = (int) Math.min(Config.PUPPETEER_SILVERFISH_BROOD_MAX.get(),
                (level.getGameTime() - hideout.since()) / Config.PUPPETEER_SILVERFISH_BROOD_TICKS.get());
        int speed = Config.PUPPETEER_SILVERFISH_BURST_SPEED_TICKS.get();
        Vec3 at = Vec3.atBottomCenterOf(hideout.pos());
        for (int i = 0; i < brood; i++) {
            net.minecraft.world.entity.monster.Silverfish fish = EntityType.SILVERFISH.create(level);
            if (fish == null) {
                break;
            }
            fish.moveTo(at.x + (level.random.nextDouble() - 0.5) * 0.6, at.y, at.z + (level.random.nextDouble() - 0.5) * 0.6,
                    level.random.nextFloat() * 360.0F, 0.0F);
            fish.finalizeSpawn(level, level.getCurrentDifficultyAt(hideout.pos()), MobSpawnType.REINFORCEMENT, null);
            if (speed > 0) {
                fish.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, speed, 1));
            }
            fish.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.6, 0.3, (level.random.nextDouble() - 0.5) * 0.6);
            level.addFreshEntity(fish);
            fish.spawnAnim();
        }
        if (speed > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, speed, 1, false, false));
        }
        level.playSound(null, hideout.pos(), SoundEvents.SILVERFISH_AMBIENT, SoundSource.PLAYERS, 1.5F, 1.2F);
        // out of the stone, the swarm goes for the closest mob (no glow — a soft rally)
        Mob closest = null;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(at, at).inflate(16.0),
                m -> m.isAlive() && !(m instanceof net.minecraft.world.entity.monster.Silverfish)
                        && !(m instanceof net.minecraft.world.entity.monster.Endermite))) {
            if (closest == null || mob.distanceToSqr(at) < closest.distanceToSqr(at)) {
                closest = mob;
            }
        }
        if (closest != null) {
            silverfishRally(player, closest);
        }
    }

    /** the silverfish around you go for {@code prey} — a zombie-style rally, minus the glow and the summoning. */
    private static void silverfishRally(ServerPlayer player, LivingEntity prey) {
        double r = Config.PUPPETEER_ZOMBIE_RALLY_RADIUS.get();
        for (net.minecraft.world.entity.monster.Silverfish fish : player.serverLevel().getEntitiesOfClass(
                net.minecraft.world.entity.monster.Silverfish.class, player.getBoundingBox().inflate(r), f -> f.isAlive() && f != prey)) {
            fish.setTarget(prey);
        }
    }

    /**
     * leave the stone (bursting out, stepping out of the puppet, or logging back in after a crash): the infested block
     * is broken so you're free, and you're visible again. the spot comes from the puppet data, so it survives a crash.
     */
    private static void unhide(ServerPlayer player, CompoundTag tag) {
        EMBEDDING.remove(player.getUUID());
        boolean wasHidden = player.getData(WitchModAttachments.PUPPET_HIDDEN) || tag.contains(HIDDEN_KEY);
        if (!wasHidden) {
            return;
        }
        HIDEOUTS.remove(player.getUUID());
        if (tag.contains(HIDDEN_KEY)) {
            BlockPos pos = BlockPos.of(tag.getLong(HIDDEN_KEY));
            tag.remove(HIDDEN_KEY);
            if (player.serverLevel().getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.InfestedBlock) {
                player.serverLevel().destroyBlock(pos, false); // no drops, no extra silverfish — you ARE the silverfish
            }
        }
        player.setInvisible(false);
        player.setData(WitchModAttachments.PUPPET_HIDDEN, false);
        player.setData(WitchModAttachments.PUPPET_FUSE, 0);
    }

    /**
     * Call reinforcements: like a hurt silverfish's own call, every infested block nearby breaks open (with mobGriefing
     * — otherwise it just reverts, as in vanilla) and its silverfish join in; every silverfish near you goes for
     * whatever you're looking at. nothing to call: a hint, and no cooldown spent.
     */
    private static void callSilverfish(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int r = Config.PUPPETEER_SILVERFISH_CALL_RADIUS.get();
        int woken = 0;
        boolean grief = EventHooks.canEntityGrief(level, player);
        for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-r, -r / 2, -r), player.blockPosition().offset(r, r / 2, r))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof net.minecraft.world.level.block.InfestedBlock infested) {
                if (grief) {
                    level.destroyBlock(pos, true, player);
                } else {
                    level.setBlock(pos, infested.hostStateByInfested(state), 3);
                }
                woken++;
            }
        }
        // seed the stone around you: up to N stone-type blocks become infested, ready for the next call.
        int infested = 0;
        int want = Config.PUPPETEER_SILVERFISH_CALL_INFEST.get();
        for (int attempt = 0; attempt < want * 12 && infested < want; attempt++) {
            BlockPos pos = player.blockPosition().offset(level.random.nextInt(r * 2 + 1) - r, level.random.nextInt(r + 1) - r / 2,
                    level.random.nextInt(r * 2 + 1) - r);
            BlockState state = level.getBlockState(pos);
            if (net.minecraft.world.level.block.InfestedBlock.isCompatibleHostBlock(state)) {
                level.setBlock(pos, net.minecraft.world.level.block.InfestedBlock.infestedStateByHost(state), 3);
                Vec3 c = Vec3.atCenterOf(pos);
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state),
                        c.x, c.y, c.z, 6, 0.4, 0.4, 0.4, 0.05);
                infested++;
            }
        }
        LivingEntity prey = lookTarget(player, 24.0);
        int helpers = 0;
        for (net.minecraft.world.entity.monster.Silverfish fish : level.getEntitiesOfClass(net.minecraft.world.entity.monster.Silverfish.class,
                player.getBoundingBox().inflate(r * 1.5), LivingEntity::isAlive)) {
            helpers++;
            if (prey != null) {
                fish.setTarget(prey);
            }
        }
        if (woken == 0 && helpers == 0 && infested == 0) {
            player.displayClientMessage(Component.translatable("witchmod.puppeteer.silverfish_no_call").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        cooldown(player, Config.PUPPETEER_SILVERFISH_CALL_COOLDOWN_TICKS.get());
        level.playSound(null, player.blockPosition(), SoundEvents.SILVERFISH_AMBIENT, SoundSource.PLAYERS, 2.0F, 0.6F);
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickSilverfish(player);
    }

    /** burst / embed / call (bursting out ignores the cooldown). */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        silverfishAction(player);
    }

    /** burrowing in (or hidden) it can't swing. */
    @Override
    public boolean blocksMelee(Player player, PuppetType type, Entity target) {
        return player.getData(WitchModAttachments.PUPPET_FUSE) > 0;
    }

    /** hidden in stone it's untouchable — and doesn't suffocate in there — until it bursts out. */
    @Override
    public void incomingDamage(ServerPlayer player, PuppetType type, LivingIncomingDamageEvent event) {
        if (hidden(player) && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    /** whatever you bite, the swarm goes for. */
    @Override
    public void dealtDamage(ServerPlayer player, PuppetType type, LivingDamageEvent.Post event) {
        if (event.getSource().getDirectEntity() == player && event.getEntity() != player
                && !(event.getEntity() instanceof net.minecraft.world.entity.monster.Silverfish)) {
            silverfishRally(player, event.getEntity());
        }
    }

    /** a silverfish hidden in stone breaks out first (also after a crash: the spot is in the data). */
    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        unhide(player, data);
    }

    /** hidden in stone: the brood that'll burst out with you grows the longer you wait. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        if (!player.getData(WitchModAttachments.PUPPET_HIDDEN)) {
            out.addMove(player, type);
            return;
        }
        int hold = player.getData(WitchModAttachments.PUPPET_FUSE);
        int brood = Math.max(0, (hold - Config.PUPPETEER_SILVERFISH_EMBED_TICKS.get())
                / Math.max(1, Config.PUPPETEER_SILVERFISH_BROOD_TICKS.get()));
        out.add(List.of(tap(Key.USE)), Component.translatable("witchmod.puppeteer.action.burst", brood), 0L);
    }
}
