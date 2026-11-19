package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;

/**
 * puppeteer: the turtle. quick in water (a swimmer, and it never runs out of air), slow on land; no bite — left-click is
 * its call. hold right-click on land to LAY EGGS: a long dig (the real one's digging, block dust flying, slowed right
 * down), and the longer you hold, the more eggs, up to puppeteerTurtleMaxEggs; then a long cooldown. like the real
 * eggs they only hatch on sand. cubs can't lay.
 */
public final class TurtleBehaviour implements PuppetBehaviour {
    public static final TurtleBehaviour INSTANCE = new TurtleBehaviour();

    private TurtleBehaviour() {}

    private static final ResourceLocation TURTLE_LAND_ID = EffectUtil.modifierId("puppeteer_turtle_land");

    /** digging a nest: right-click held on land (both sides — the model digs). */
    public static boolean laying(Player player) {
        return PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.TURTLE
                && player.getData(WitchModAttachments.PUPPET_FUSE) > 0;
    }

    private static boolean adult(Player player) {
        return player.getData(WitchModAttachments.PUPPET_DATA).getInt("Age") >= 0;
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        player.setAirSupply(player.getMaxAirSupply()); // it breathes under water as well as above it
        boolean land = !player.isInWater();
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && land != speed.hasModifier(TURTLE_LAND_ID)) {
            if (land) {
                EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, TURTLE_LAND_ID, -Config.PUPPETEER_TURTLE_LAND_SLOW.get(),
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            } else {
                speed.removeModifier(TURTLE_LAND_ID);
            }
        }
        Holds.tickHold(player, type);
        boolean digging = laying(player);
        Puppets.chargeSlow(player, digging, Config.PUPPETEER_TURTLE_LAY_SLOW.get());
        int dig = player.getData(WitchModAttachments.PUPPET_FUSE);
        if (digging && dig % 5 == 0) {
            // the dig: dust off the block underneath, as the real one throws it up
            BlockPos under = player.blockPosition().below();
            player.serverLevel().levelEvent(2001, player.blockPosition(), Block.getId(player.level().getBlockState(under)));
        }
    }

    /** a nest digs only for an adult, on land, off cooldown. */
    @Override
    public boolean canCharge(ServerPlayer player, PuppetType type) {
        return adult(player) && player.onGround() && !player.isInWater() && Puppets.ready(player);
    }

    @Override
    public void chargeStarted(ServerPlayer player, PuppetType type) {
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.TURTLE_LAY_EGG, SoundSource.PLAYERS, 0.4F, 0.8F);
    }

    /** let go: the eggs — one, plus more the longer you dug (up to puppeteerTurtleMaxEggs), four to a clutch. */
    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        if (charge < Holds.MIN_CHARGE || !adult(player) || !player.onGround() || player.isInWater()) {
            return;
        }
        int full = Math.max(Holds.MIN_CHARGE + 1, Config.PUPPETEER_TURTLE_LAY_CHARGE_TICKS.get());
        int max = Config.PUPPETEER_TURTLE_MAX_EGGS.get();
        double share = Math.min(1.0, (charge - Holds.MIN_CHARGE) / (double) (full - Holds.MIN_CHARGE));
        int eggs = 1 + (int) Math.floor(share * (max - 1) + 1.0E-6);
        ServerLevel level = player.serverLevel();
        BlockPos at = player.blockPosition();
        int laid = 0;
        // a clutch of up to four where you stand, then the next around you
        BlockPos[] spots = {at, at.north(), at.east(), at.south(), at.west(), at.north().east(), at.south().east(),
                at.south().west(), at.north().west()};
        for (BlockPos spot : spots) {
            if (laid >= eggs) {
                break;
            }
            if (!level.getBlockState(spot).canBeReplaced()
                    || !level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP)) {
                continue;
            }
            int clutch = Math.min(4, eggs - laid);
            BlockState egg = Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.EGGS, clutch);
            level.setBlock(spot, egg, 3);
            laid += clutch;
        }
        if (laid == 0) {
            return; // nowhere to lay: no cooldown spent
        }
        level.playSound(null, at, SoundEvents.TURTLE_LAY_EGG, SoundSource.PLAYERS, 0.3F, 0.9F + level.random.nextFloat() * 0.2F);
        Puppets.cooldown(player, Config.PUPPETEER_TURTLE_LAY_COOLDOWN_TICKS.get());
        Puppets.countAction(player);
    }

    /** left-click: its call (on land; a splash of a swim sound in water). */
    @Override
    public void swing(ServerPlayer player, PuppetType type) {
        if (!Puppets.ready2(player)) {
            return;
        }
        player.setData(WitchModAttachments.PUPPET_ACTION2_READY,
                player.level().getGameTime() + Config.PUPPETEER_TURTLE_CALL_COOLDOWN_TICKS.get());
        player.serverLevel().playSound(null, player.blockPosition(),
                player.isInWater() ? SoundEvents.TURTLE_SWIM : SoundEvents.TURTLE_AMBIENT_LAND, SoundSource.PLAYERS, 1.0F,
                adult(player) ? 1.0F : 1.4F); // a hatchling's is higher
    }

    @Override
    public boolean usesLeftClick(PuppetType type) {
        return true;
    }

    /** no bite. */
    @Override
    public boolean dealsMelee(ServerPlayer player, PuppetType type) {
        return false;
    }

    @Override
    public void clear(ServerPlayer player, net.minecraft.nbt.CompoundTag data) {
        EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, TURTLE_LAND_ID);
    }

    /** hold right-click (on land, grown): lay eggs; left-click: its call. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        if (adult(player)) {
            out.addMove(player, type);
        }
        out.add(tap(Key.ATTACK), "witchmod.puppeteer.action.turtle_call", PuppetControls.ready2(player));
    }
}
