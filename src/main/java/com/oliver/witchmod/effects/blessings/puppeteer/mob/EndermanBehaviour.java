package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.List;

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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;

/** puppeteer: the enderman puppet. */
public final class EndermanBehaviour implements PuppetBehaviour {
    public static final EndermanBehaviour INSTANCE = new EndermanBehaviour();

    private EndermanBehaviour() {}

    // --- enderman -----------------------------------------------------------------------------------

    private static final String CARRIED_KEY = "carriedBlockState"; // the enderman's own key — so it comes back holding it

    /** a carried block's contents (a chest's items, a sign's text...), so it's put down exactly as it was picked up. */
    private static final String CARRIED_BE_KEY = "witchmod_carried_block_entity";

    /** everyone who's stared at each enderman puppet → until when (they're purple on its screen; teleporting near one is free). */
    private static final java.util.Map<java.util.UUID, java.util.Map<java.util.UUID, Long>> STARED =
            com.oliver.witchmod.data.EffectState.sessionConcurrent("puppeteer/enderman/stared");

    public static boolean enraged(Player player) {
        return player.getData(WitchModAttachments.PUPPET_RAGE_END) > player.level().getGameTime();
    }

    /**
     * vanilla's stare check (EnderMan.isLookingAtMe): {@code looker} is looking {@code at} in the eye — a narrow cone,
     * tighter the further away, with line of sight. a carved pumpkin on the looker's head makes it safe, as in vanilla.
     */
    public static boolean staresAt(Player looker, LivingEntity at) {
        if (looker == at || looker.isSpectator() || looker.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN)) {
            return false;
        }
        Vec3 view = looker.getViewVector(1.0F).normalize();
        Vec3 to = new Vec3(at.getX() - looker.getX(), at.getEyeY() - looker.getEyeY(), at.getZ() - looker.getZ());
        double dist = to.length();
        if (dist > 64.0 || dist < 1.0E-3) {
            return false;
        }
        return view.dot(to.normalize()) > 1.0 - 0.025 / dist && looker.hasLineOfSight(at);
    }

    /**
     * enderman upkeep: water and rain hurt; anyone who looks you in the eye enrages you (Speed, more and faster
     * teleport charges, the dropped jaw); teleport charges recharge one at a time.
     */
    private static void tickEnderman(ServerPlayer player, int ticksRemaining) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (ticksRemaining % 10 == 0 && player.isInWaterRainOrBubble()) {
            player.hurt(player.damageSources().drown(), 1.0F);
        }
        if (ticksRemaining % 4 == 0) {
            for (ServerPlayer other : level.players()) {
                if (other != player && staresAt(other, player)) {
                    STARED.computeIfAbsent(player.getUUID(), k -> new java.util.concurrent.ConcurrentHashMap<>())
                            .put(other.getUUID(), now + Config.PUPPETEER_ENDERMAN_RAGE_SECONDS.get() * 20L);
                    if (!enraged(player)) {
                        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_SCREAM, SoundSource.PLAYERS, 1.5F, 1.0F);
                        player.playNotifySound(SoundEvents.ENDERMAN_STARE, SoundSource.PLAYERS, 1.0F, 1.0F);
                    }
                    player.setData(WitchModAttachments.PUPPET_RAGE_END, now + Config.PUPPETEER_ENDERMAN_RAGE_SECONDS.get() * 20L);
                    break;
                }
            }
        }
        boolean rage = enraged(player);
        int speed = Config.PUPPETEER_ENDERMAN_RAGE_SPEED.get();
        if (rage && speed > 0 && ticksRemaining % 10 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, speed - 1, false, false));
        }
        int max = rage ? Config.PUPPETEER_ENDERMAN_RAGE_CHARGES.get() : 1;
        int charges = player.getData(WitchModAttachments.PUPPET_TP_CHARGES);
        if (charges > max) {
            player.setData(WitchModAttachments.PUPPET_TP_CHARGES, max); // calmed down: back to one
        } else if (charges < max && ready(player)) {
            player.setData(WitchModAttachments.PUPPET_TP_CHARGES, charges + 1);
            if (charges + 1 < max) {
                cooldown(player, teleportCooldown(player));
            }
        }
    }

    private static int teleportCooldown(ServerPlayer player) {
        return enraged(player) ? Config.PUPPETEER_ENDERMAN_RAGE_COOLDOWN_TICKS.get() : Config.PUPPETEER_ENDERMAN_TELEPORT_COOLDOWN_TICKS.get();
    }

    /** enderman right-click: the smart teleport, spending a charge (the recharge starts if it wasn't already running). */
    private static void endermanTeleport(ServerPlayer player) {
        int charges = player.getData(WitchModAttachments.PUPPET_TP_CHARGES);
        if (charges <= 0) {
            return;
        }
        Vec3 spot = teleportTarget(player);
        if (spot == null) {
            player.displayClientMessage(Component.translatable("witchmod.puppeteer.enderman_no_spot").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        blink(player, spot);
        if (nearStarer(player, spot)) {
            // landed right by someone who stared you down: that one's free
            player.playNotifySound(SoundEvents.ENDERMAN_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.6F);
            return;
        }
        player.setData(WitchModAttachments.PUPPET_TP_CHARGES, charges - 1);
        if (ready(player)) {
            cooldown(player, teleportCooldown(player));
        }
    }

    /** someone who's stared at you, within puppeteerEndermanStarerRefundRadius of {@code spot}. */
    private static boolean nearStarer(ServerPlayer player, Vec3 spot) {
        java.util.Map<java.util.UUID, Long> stared = STARED.get(player.getUUID());
        if (stared == null) {
            return false;
        }
        long now = player.level().getGameTime();
        double r = Config.PUPPETEER_ENDERMAN_STARER_REFUND_RADIUS.get();
        stared.values().removeIf(t -> t < now);
        for (java.util.UUID id : stared.keySet()) {
            Player starer = player.level().getPlayerByUUID(id);
            if (starer != null && starer.isAlive() && starer.distanceToSqr(spot) <= r * r) {
                return true;
            }
        }
        return false;
    }

    /**
     * where a teleport goes (shared by the server and your client's marker): along your look — and when your look runs
     * into a wall (a side face, up to puppeteerEndermanThroughWallBlocks thick), to the far side of it, if you'd fit
     * there. never into a block.
     */
    @Nullable
    public static Vec3 teleportTarget(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double range = Config.PUPPETEER_ENDERMAN_TELEPORT_RANGE.get();
        Vec3 end = eye.add(look.scale(range));
        net.minecraft.world.phys.BlockHitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS && hit.getDirection().getAxis().isHorizontal()) {
            // a wall: look for open air just beyond it, along the same line
            double into = hit.getLocation().distanceTo(eye);
            int thick = Config.PUPPETEER_ENDERMAN_THROUGH_WALL_BLOCKS.get();
            for (double d = into + 0.5; d <= Math.min(range, into + thick + 1.5); d += 0.5) {
                BlockPos at = BlockPos.containing(eye.add(look.scale(d)));
                if (player.level().getBlockState(at).getCollisionShape(player.level(), at).isEmpty()) {
                    Vec3 beyond = landingSpot(player, at, 4);
                    if (beyond != null) {
                        return beyond;
                    }
                }
            }
        }
        BlockPos aim = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? BlockPos.containing(end)
                : hit.getBlockPos().relative(hit.getDirection());
        return landingSpot(player, aim, (int) Math.ceil(range));
    }

    /**
     * where you'd land teleporting at {@code aim}: biased to the ground — looks down from there (up to {@code down}
     * blocks) for the first spot you fit standing on something solid, then a little up; never into water.
     */
    @Nullable
    private static Vec3 landingSpot(Player player, BlockPos aim, int down) {
        for (int dy = 0; dy >= -down; dy--) {
            Vec3 at = standable(player, aim.offset(0, dy, 0));
            if (at != null) {
                return at;
            }
        }
        for (int dy = 1; dy <= 3; dy++) {
            Vec3 at = standable(player, aim.offset(0, dy, 0));
            if (at != null) {
                return at;
            }
        }
        return null;
    }

    @Nullable
    public static Vec3 standable(Player player, BlockPos pos) {
        net.minecraft.world.level.Level level = player.level();
        if (!level.isInWorldBounds(pos) || !level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) {
            return null;
        }
        BlockPos below = pos.below();
        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
            return null;
        }
        Vec3 at = Vec3.atBottomCenterOf(pos);
        return level.noCollision(player, player.getBoundingBox().move(at.subtract(player.position()))) ? at : null;
    }

    /** the teleport itself: vanilla's enderman sound and portal puffs at both ends. */
    public static void blink(ServerPlayer player, Vec3 to) {
        ServerLevel level = player.serverLevel();
        Vec3 from = player.position();
        level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1.0, from.z, 32, 0.3, 1.0, 0.3, 0.5);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.teleportTo(to.x, to.y, to.z);
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        level.sendParticles(ParticleTypes.PORTAL, to.x, to.y + 1.0, to.z, 32, 0.3, 1.0, 0.3, 0.5);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** a projectile would hit you: like the real thing you blink a few blocks away instead (free). */
    public static boolean dodge(ServerPlayer player) {
        for (int attempt = 0; attempt < 16; attempt++) {
            BlockPos aim = BlockPos.containing(player.getX() + (player.getRandom().nextDouble() - 0.5) * 16.0,
                    player.getY() + player.getRandom().nextInt(5) - 2, player.getZ() + (player.getRandom().nextDouble() - 0.5) * 16.0);
            Vec3 spot = landingSpot(player, aim, 6);
            if (spot != null) {
                blink(player, spot);
                return true;
            }
        }
        return false;
    }

    /**
     * enderman crouch + right-click on a block: pick it up (anything a real enderman can carry) or put the one you're
     * carrying down there. it's kept in the puppet data under the enderman's own key, so it shows in your hands and
     * the real enderman is still holding it when you step out. @return false when you weren't aiming at a block (so
     * crouch + right-click at nothing still leaves).
     */
    private static boolean endermanCarry(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(eye,
                eye.add(player.getLookAngle().scale(player.blockInteractionRange())),
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return false;
        }
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        BlockPos pos = hit.getBlockPos();
        if (tag.contains(CARRIED_KEY)) {
            BlockPos at = level.getBlockState(pos).canBeReplaced() ? pos : pos.relative(hit.getDirection());
            BlockState carried = net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),
                    tag.getCompound(CARRIED_KEY));
            carried = net.minecraft.world.level.block.Block.updateFromNeighbourShapes(carried, level, at);
            if (!player.mayBuild() || !level.mayInteract(player, at) || !level.getBlockState(at).canBeReplaced()
                    || !carried.canSurvive(level, at) || !level.isUnobstructed(carried, at, net.minecraft.world.phys.shapes.CollisionContext.empty())) {
                return true; // nowhere to put it there — but don't leave the puppet over it
            }
            putDown(level, at, carried, tag);
            level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.BLOCK_PLACE, at,
                    net.minecraft.world.level.gameevent.GameEvent.Context.of(player, carried));
            level.playSound(null, at, carried.getSoundType(level, at, player).getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
        } else {
            BlockState state = level.getBlockState(pos);
            if (!carriable(player, pos, state)) {
                player.displayClientMessage(Component.translatable("witchmod.puppeteer.enderman_cant_carry").withStyle(ChatFormatting.GRAY), true);
                return true;
            }
            tag.put(CARRIED_KEY, net.minecraft.nbt.NbtUtils.writeBlockState(state));
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                // its contents go with it (in the puppet data — so a crash keeps them too), emptied out of the world
                tag.put(CARRIED_BE_KEY, be.saveWithFullMetadata(level.registryAccess()));
                net.minecraft.world.Clearable.tryClear(be);
            }
            level.removeBlock(pos, false);
            level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.BLOCK_DESTROY, pos,
                    net.minecraft.world.level.gameevent.GameEvent.Context.of(player, state));
            level.playSound(null, pos, state.getSoundType(level, pos, player).getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
        }
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        player.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    /**
     * what it can lift: anything softer than obsidian (puppeteerEndermanMaxCarryHardness) that you could break here —
     * not bedrock-like blocks, fluids, or the two-part ones (beds, doors, tall plants) — and the break's allowed
     * (claims, spawn protection: the same check as mining it).
     */
    private static boolean carriable(ServerPlayer player, BlockPos pos, BlockState state) {
        ServerLevel level = player.serverLevel();
        if (state.isAir() || !state.getFluidState().isEmpty() && state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock
                || !player.mayBuild() || !level.mayInteract(player, pos)) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F || hardness >= Config.PUPPETEER_ENDERMAN_MAX_CARRY_HARDNESS.get()) {
            return false;
        }
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                || state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART)
                || state.is(net.minecraft.world.level.block.Blocks.PISTON_HEAD) || state.is(net.minecraft.world.level.block.Blocks.MOVING_PISTON)) {
            return false;
        }
        return !net.neoforged.neoforge.common.CommonHooks.fireBlockBreak(level, player.gameMode.getGameModeForPlayer(), player, pos, state)
                .isCanceled();
    }

    /** set the carried block down at {@code at}, contents and all, and forget it. */
    private static void putDown(ServerLevel level, BlockPos at, BlockState carried, CompoundTag tag) {
        level.setBlock(at, carried, 3);
        if (tag.contains(CARRIED_BE_KEY)) {
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(at);
            if (be != null) {
                be.loadWithComponents(tag.getCompound(CARRIED_BE_KEY), level.registryAccess());
                be.setChanged();
            }
        }
        tag.remove(CARRIED_KEY);
        tag.remove(CARRIED_BE_KEY);
    }

    /**
     * stepping out (or dying, or logging out, or after a crash) carrying something with contents: it's put down right
     * there (or close by) — never left in the mob's arms, where its contents would be lost. if there's truly nowhere,
     * it drops as an item with everything that was in it.
     */
    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        STARED.remove(player.getUUID());
        if (!data.contains(CARRIED_BE_KEY) || !data.contains(CARRIED_KEY)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockState carried = net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),
                data.getCompound(CARRIED_KEY));
        BlockPos base = player.blockPosition();
        for (BlockPos at : BlockPos.withinManhattan(base, 3, 2, 3)) {
            if (level.getBlockState(at).canBeReplaced() && level.getFluidState(at).isEmpty() && carried.canSurvive(level, at)) {
                putDown(level, at.immutable(), net.minecraft.world.level.block.Block.updateFromNeighbourShapes(carried, level, at), data);
                return;
            }
        }
        // nowhere at all: the block and everything in it spill out
        net.minecraft.world.Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), new net.minecraft.world.item.ItemStack(carried.getBlock()));
        net.minecraft.nbt.ListTag items = data.getCompound(CARRIED_BE_KEY).getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            net.minecraft.world.item.ItemStack stack = net.minecraft.world.item.ItemStack.parseOptional(level.registryAccess(), items.getCompound(i));
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), stack);
            }
        }
        data.remove(CARRIED_KEY);
        data.remove(CARRIED_BE_KEY);
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickEnderman(player, ticksRemaining);
    }

    /** teleport (on its charges, not the plain cooldown). */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        endermanTeleport(player);
    }

    /** crouch + right-click on a block: pick up / put down (at nothing: step out, as ever). */
    @Override
    public boolean crouchUse(ServerPlayer player, PuppetType type) {
        return endermanCarry(player);
    }

    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        player.setData(WitchModAttachments.PUPPET_TP_CHARGES, 1);
        player.setData(WitchModAttachments.PUPPET_RAGE_END, 0L);
    }

    /** blinks out of a projectile's way, like a real enderman. */
    @Override
    public void incomingDamage(ServerPlayer player, PuppetType type, LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypeTags.IS_PROJECTILE) && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && Config.PUPPETEER_ENDERMAN_DODGE_PROJECTILES.get() && dodge(player)) {
            event.setCanceled(true);
        }
    }

    /** teleport charges (usable whenever one's ready, even while the next recharges), carrying, and the rage. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        int charges = player.getData(WitchModAttachments.PUPPET_TP_CHARGES);
        if (charges > 0) {
            out.add(List.of(tap(Key.USE)), Component.translatable("witchmod.puppeteer.action.teleport_charges", charges), 0L);
        } else {
            out.add(tap(Key.USE), type.move().labelKey(), PuppetControls.ready(player));
        }
        out.add(List.of(tap(Key.SNEAK), tap(Key.USE)), Component.translatable("witchmod.puppeteer.action.carry"), 0L);
        if (enraged(player)) {
            long left = player.getData(WitchModAttachments.PUPPET_RAGE_END) - player.level().getGameTime();
            out.note(Component.translatable("witchmod.puppeteer.enraged", (left + 19) / 20).withStyle(ChatFormatting.DARK_PURPLE));
        }
    }
}
