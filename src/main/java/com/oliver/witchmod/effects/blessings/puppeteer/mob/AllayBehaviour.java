package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Holds.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.SlimeBehaviour.*;

/** puppeteer: the allay puppet. */
public final class AllayBehaviour implements PuppetBehaviour {
    public static final AllayBehaviour INSTANCE = new AllayBehaviour();

    private AllayBehaviour() {}

    // --- allay --------------------------------------------------------------------------------------

    /** allay upkeep: keep its free flight granted (no sprint-fly, enforced client-side), and build the mitosis charge. */
    private static void tickAllay(ServerPlayer player) {
        if (!player.getAbilities().mayfly && !player.isCreative() && !player.isSpectator()) {
            grantBatFlight(player);
        }
        // the mitosis charge builds ONLY while you hold right-click within earshot of a playing jukebox (and off cooldown);
        // let go (or move out of earshot) at a full charge to split. a short hold instead does the tap action (fireHeld).
        boolean holding = HELD.contains(player.getUUID());
        int hold = player.getData(WitchModAttachments.PUPPET_FUSE);
        if (holding && ready2(player) && nearMusic(player)) {
            if (hold == 0) {
                player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.PLAYERS, 0.6F, 1.2F);
            }
            player.setData(WitchModAttachments.PUPPET_FUSE, Math.min(hold + 1, Config.PUPPETEER_ALLAY_MITOSIS_CHARGE_TICKS.get()));
        } else if (hold != 0 && !holding) {
            player.setData(WitchModAttachments.PUPPET_FUSE, 0);
        }
    }

    /** whether a playing jukebox is within earshot (the allay needs music to split). */
    public static boolean nearMusic(Player player) {
        int r = (int) Math.ceil(Config.PUPPETEER_ALLAY_MUSIC_RADIUS.get());
        BlockPos at = player.blockPosition();
        net.minecraft.world.level.Level level = player.level();
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-r, -r, -r), at.offset(r, r, r))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(net.minecraft.world.level.block.Blocks.JUKEBOX)
                    && state.getValue(net.minecraft.world.level.block.JukeboxBlock.HAS_RECORD)) {
                return true;
            }
        }
        return false;
    }

    /** a quick right-click: take one random item from a public container you're eyeing, else pick up / drop ground items. */
    private static void allayTap(ServerPlayer player) {
        net.minecraft.world.phys.HitResult hit = player.pick(5.0, 1.0F, false);
        if (hit instanceof net.minecraft.world.phys.BlockHitResult block) {
            net.minecraft.world.level.block.entity.BlockEntity be = player.level().getBlockEntity(block.getBlockPos());
            // a public container (chests, barrels, hoppers, furnaces...) — ender chests aren't Containers, so they're out
            if (be instanceof net.minecraft.world.Container container && !container.isEmpty()) {
                takeRandomItem(player, container);
                return;
            }
        }
        allayPickupOrDrop(player);
    }

    /** pull one item out of a random non-empty slot and hand it to the allay (held), or drop it if the allay's hands are full. */
    private static void takeRandomItem(ServerPlayer player, net.minecraft.world.Container container) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) {
                slots.add(i);
            }
        }
        if (slots.isEmpty()) {
            return;
        }
        int slot = slots.get(player.getRandom().nextInt(slots.size()));
        ItemStack taken = container.removeItem(slot, 1);
        if (taken.isEmpty()) {
            return;
        }
        container.setChanged();
        if (allayHeld(player).isEmpty()) {
            setAllayHeld(player, taken);
        } else if (!player.getInventory().add(taken)) {
            player.drop(taken, false);
        }
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** right-click with nothing to take: drop what the allay holds, or pick up the nearest ground item (up to a stack). */
    private static void allayPickupOrDrop(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ItemStack held = allayHeld(player);
        if (!held.isEmpty()) {
            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(level,
                    player.getX(), player.getY() + 0.5, player.getZ(), held);
            drop.setDeltaMovement(player.getLookAngle().scale(0.2));
            drop.setPickUpDelay(20);
            level.addFreshEntity(drop);
            setAllayHeld(player, ItemStack.EMPTY);
            level.playSound(null, player.blockPosition(), SoundEvents.ALLAY_THROW, SoundSource.PLAYERS, 1.0F, 1.0F);
            return;
        }
        double r = Config.PUPPETEER_ALLAY_PICKUP_RADIUS.get();
        net.minecraft.world.entity.item.ItemEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                player.getBoundingBox().inflate(r), i -> i.isAlive() && !i.getItem().isEmpty())) {
            double d = item.distanceToSqr(player);
            if (d < best) {
                best = d;
                nearest = item;
            }
        }
        if (nearest == null) {
            return;
        }
        ItemStack stack = nearest.getItem();
        ItemStack take = stack.copyWithCount(Math.min(stack.getCount(), stack.getMaxStackSize()));
        setAllayHeld(player, take);
        stack.shrink(take.getCount());
        if (stack.isEmpty()) {
            nearest.discard();
        } else {
            nearest.setItem(stack);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** near music, a full hold splits off a real allay copy (carrying a copy of what you hold), on a long cooldown. */
    private static void allayMitosis(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        player.setData(WitchModAttachments.PUPPET_ACTION2_READY,
                level.getGameTime() + Config.PUPPETEER_ALLAY_MITOSIS_COOLDOWN_TICKS.get());
        net.minecraft.world.entity.animal.allay.Allay clone = EntityType.ALLAY.create(level);
        if (clone != null) {
            clone.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
            ItemStack held = allayHeld(player);
            if (!held.isEmpty()) {
                clone.setItemInHand(InteractionHand.MAIN_HAND, held.copyWithCount(1));
            }
            level.addFreshEntity(clone);
        }
        level.sendParticles(ParticleTypes.NOTE, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.4, 0.4, 0.4, 1.0);
        level.playSound(null, player.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.setData(WitchModAttachments.PUPPET_FUSE, 0);
    }

    private static final String ALLAY_HELD_KEY = "witchmod_allay_held";

    public static ItemStack allayHeld(Player player) {
        CompoundTag data = player.getData(WitchModAttachments.PUPPET_DATA);
        if (!data.contains(ALLAY_HELD_KEY, Tag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.parseOptional(player.registryAccess(), data.getCompound(ALLAY_HELD_KEY));
    }

    private static void setAllayHeld(Player player, ItemStack stack) {
        CompoundTag data = player.getData(WitchModAttachments.PUPPET_DATA);
        if (stack.isEmpty()) {
            data.remove(ALLAY_HELD_KEY);
        } else {
            data.put(ALLAY_HELD_KEY, stack.save(player.registryAccess()));
        }
        player.setData(WitchModAttachments.PUPPET_DATA, data);
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickAllay(player);
    }

    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        if (charge >= Config.PUPPETEER_ALLAY_MITOSIS_CHARGE_TICKS.get() && Puppets.ready2(player) && nearMusic(player)) {
            allayMitosis(player); // a full hold within earshot of music: split off a copy
        } else {
            allayTap(player); // a tap: take from a container you're eyeing, else pick up / drop ground items
        }
    }

    /** the allay never hits. */
    @Override
    public boolean dealsMelee(ServerPlayer player, PuppetType type) {
        return false;
    }

    @Override
    public void possess(ServerPlayer player, PuppetType type, Mob mob, CompoundTag data) {
        Puppets.grantBatFlight(player);
    }

    /** an allay leaves holding something: drop it so it isn't lost. */
    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        ItemStack carried = allayHeld(player);
        if (!carried.isEmpty()) {
            player.drop(carried, false);
            setAllayHeld(player, ItemStack.EMPTY);
        }
    }

    private static boolean hearsMusic(Player player) {
        return Puppets.hearsMusic(player, Config.PUPPETEER_ALLAY_MUSIC_RADIUS.get());
    }

    /** a tap picks up / drops (or takes from a container); mitosis only shows while music's playing nearby. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        out.add(tap(Key.USE), allayHeld(player).isEmpty() ? "witchmod.puppeteer.action.allay_pickup"
                : "witchmod.puppeteer.action.allay_drop", 0L);
        if (hearsMusic(player)) {
            out.add(hold(Key.USE), type.move().labelKey(), PuppetControls.ready2(player));
        }
    }
}
