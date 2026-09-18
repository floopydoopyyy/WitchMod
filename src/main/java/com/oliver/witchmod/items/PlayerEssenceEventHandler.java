package com.oliver.witchmod.items;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.BedSpawnRegistry;
import com.oliver.witchmod.data.PlayerEssenceData;
import com.oliver.witchmod.data.WitchModDataComponents;

/**
 * bottles player essence with an empty jar, three ways: crouch+look-down = yourself, right-click a player =
 * theirs, right-click a bed = its spawn-owner (offline-aware via {@link BedSpawnRegistry}).
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class PlayerEssenceEventHandler {
    /** how steeply down you must be looking (pitch degrees) to bottle your OWN essence. */
    private static final float LOOK_DOWN_PITCH = 45.0F;

    private PlayerEssenceEventHandler() {}

    /** keep the offline-aware bed→spawn map current whenever a player sets/clears their spawn. */
    @SubscribeEvent
    static void onSetSpawn(PlayerSetSpawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BedSpawnRegistry registry = BedSpawnRegistry.get(player.server);
        BlockPos pos = event.getNewSpawn();
        if (pos != null) {
            registry.record(event.getSpawnLevel(), pos, player.getUUID(), player.getName().getString());
        } else {
            registry.clear(player.getUUID());
        }
    }

    /** right-click another player with an empty jar → a trace of their essence, bound to them. */
    @SubscribeEvent
    static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getItemStack().is(WitchModItems.JAR.get()) || event.getLevel().isClientSide()) {
            return;
        }
        Entity target = event.getTarget();
        if (!(event.getEntity() instanceof ServerPlayer user) || !(target instanceof ServerPlayer targetPlayer)) {
            return;
        }
        Vec3 source = targetPlayer.position().add(0, targetPlayer.getBbHeight() * 0.6, 0);
        essenceFx((ServerLevel) event.getLevel(), source, user, SoundEvents.EVOKER_CAST_SPELL, 1.2F);
        bindAndGive(user, targetPlayer.getUUID(), targetPlayer.getName().getString(),
                "You pull a trace of " + targetPlayer.getName().getString() + " into the jar.");
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** right-click a bed → a spawn-owner's essence; or crouch+look-down on any block → your own. */
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(WitchModItems.JAR.get()) || !(event.getEntity() instanceof ServerPlayer user)) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos pos = event.getPos();

        if (level.getBlockState(pos).is(BlockTags.BEDS)) {
            bottleFromBed(user, level, pos);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        // not a bed — the crouch + look-down gesture bottles your OWN essence off whatever's underfoot.
        if (user.isCrouching() && user.getXRot() >= LOOK_DOWN_PITCH) {
            bottleOwn(user, level);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** the same own-essence gesture, for when you're looking down at open air rather than a block. */
    @SubscribeEvent
    static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getItemStack().is(WitchModItems.JAR.get()) || !(event.getEntity() instanceof ServerPlayer user)
                || event.getLevel().isClientSide()) {
            return;
        }
        if (user.isCrouching() && user.getXRot() >= LOOK_DOWN_PITCH) {
            bottleOwn(user, (ServerLevel) event.getLevel());
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static void bottleOwn(ServerPlayer user, ServerLevel level) {
        Vec3 source = user.position().add(0, 0.1, 0);
        essenceFx(level, source, user, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.1F);
        bindAndGive(user, user.getUUID(), user.getName().getString(), "You seal a trace of yourself in the jar.");
    }

    private static void bottleFromBed(ServerPlayer user, ServerLevel level, BlockPos bedPos) {
        List<PlayerEssenceData> owners = BedSpawnRegistry.get(level.getServer()).at(level.dimension(), bedPos);
        // belt-and-suspenders: also fold in any ONLINE player whose current respawn sits on this bed (covers
        // spawns set before this feature existed), without duplicating anyone already in the registry.
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (p.getRespawnDimension() == level.dimension() && p.getRespawnPosition() != null
                    && p.getRespawnPosition().closerThan(bedPos, 2.0)
                    && owners.stream().noneMatch(o -> o.playerId().equals(p.getUUID()))) {
                owners.add(new PlayerEssenceData(p.getUUID(), p.getName().getString()));
            }
        }
        if (owners.isEmpty()) {
            user.displayClientMessage(Component.literal("No one's spawn is set at this bed.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        PlayerEssenceData chosen = owners.get(user.getRandom().nextInt(owners.size())); // random if several share it
        Vec3 source = Vec3.atCenterOf(bedPos).add(0, 0.4, 0);
        essenceFx(level, source, user, SoundEvents.SOUL_ESCAPE.value(), 0.8F);
        bindAndGive(user, chosen.playerId(), chosen.playerName(), "A trace of " + chosen.playerName() + " lingered in the bed.");
    }

    private static void bindAndGive(ServerPlayer user, java.util.UUID id, String name, String message) {
        ItemStack essence = new ItemStack(WitchModItems.PLAYER_ESSENCE.get());
        essence.set(WitchModDataComponents.BOUND_PLAYER, new PlayerEssenceData(id, name));
        user.getItemInHand(InteractionHand.MAIN_HAND).shrink(1);
        if (!user.getInventory().add(essence)) {
            user.drop(essence, false);
        }
        user.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** A burst at {@code source}, a matching sound, and a stream of particles flowing toward {@code user}. */
    private static void essenceFx(ServerLevel level, Vec3 source, ServerPlayer user, SoundEvent sound, float pitch) {
        level.playSound(null, source.x, source.y, source.z, sound, SoundSource.PLAYERS, 1.0F, pitch);
        level.playSound(null, source.x, source.y, source.z, SoundEvents.BOTTLE_FILL_DRAGONBREATH, SoundSource.PLAYERS, 0.8F, 1.0F);
        level.sendParticles(ParticleTypes.WITCH, source.x, source.y, source.z, 24, 0.3, 0.4, 0.3, 0.06);
        level.sendParticles(ParticleTypes.ENCHANT, source.x, source.y, source.z, 30, 0.3, 0.4, 0.3, 0.7);

        // the essence FLOWS toward the person bottling it — a directed stream of soul particles from source to you.
        Vec3 to = user.position().add(0, user.getBbHeight() * 0.6, 0);
        Vec3 dir = to.subtract(source);
        double dist = dir.length();
        if (dist < 1.0E-3) {
            return;
        }
        Vec3 vel = dir.normalize().scale(0.18);
        int steps = (int) Math.max(5, Math.min(28, dist * 3));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = source.lerp(to, i / (double) steps);
            level.sendParticles(ParticleTypes.SOUL, p.x, p.y, p.z, 0, vel.x, vel.y, vel.z, 1.0); // count 0 = a moving particle
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.ENCHANT, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
            }
        }
    }
}
