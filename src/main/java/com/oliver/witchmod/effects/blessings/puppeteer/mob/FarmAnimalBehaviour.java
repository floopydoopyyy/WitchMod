package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;
import com.oliver.witchmod.entities.ExplosiveEggEntity;

import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;

/** puppeteer: the farm animal puppet. */
public final class FarmAnimalBehaviour implements PuppetBehaviour {
    public static final FarmAnimalBehaviour INSTANCE = new FarmAnimalBehaviour();

    private FarmAnimalBehaviour() {}

    /** sheep: eat the grass underfoot (or the tuft you're standing in) and grow your wool back, like the real thing. */
    public static void graze(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos at = player.blockPosition();
        BlockState here = level.getBlockState(at);
        boolean ate = false;
        if (here.is(Blocks.SHORT_GRASS) || here.is(Blocks.TALL_GRASS)) {
            level.destroyBlock(at, false);
            ate = true;
        } else if (level.getBlockState(at.below()).is(Blocks.GRASS_BLOCK)) {
            level.levelEvent(2001, at.below(), net.minecraft.world.level.block.Block.getId(Blocks.GRASS_BLOCK.defaultBlockState()));
            level.setBlock(at.below(), Blocks.DIRT.defaultBlockState(), 2);
            ate = true;
        }
        if (ate) {
            CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
            tag.putBoolean("Sheared", false);
            player.setData(WitchModAttachments.PUPPET_DATA, tag);
        }
    }

    /** farm animals: someone holding your food drags you over to them, nose first. */
    public static void lure(ServerPlayer player, PuppetType type) {
        TagKey<Item> food = type.lureFood();
        double range = Config.PUPPETEER_ANIMAL_LURE_RANGE.get();
        if (food == null || range <= 0.0) {
            return;
        }
        Player lurer = null;
        double best = range * range;
        for (Player other : player.level().players()) {
            if (other == player || other.isSpectator() || isPuppet(other)) {
                continue;
            }
            double d = other.distanceToSqr(player);
            if (d < best && (other.getMainHandItem().is(food) || other.getOffhandItem().is(food))) {
                best = d;
                lurer = other;
            }
        }
        if (lurer == null || best < 2.25) {
            return;
        }
        Vec3 pull = lurer.position().subtract(player.position()).multiply(1.0, 0.0, 1.0).normalize()
                .scale(Config.PUPPETEER_ANIMAL_LURE_STRENGTH.get());
        player.setDeltaMovement(player.getDeltaMovement().add(pull));
        player.hurtMarked = true;
    }

    /**
     * shearing a mooshroom puppet: five mushrooms of its colour drop and the puppet BECOMES a cow puppet (the saved
     * mob is rewritten as a cow, so it also comes back as one) — exactly what shears do to a real mooshroom.
     */
    private static void shearMooshroom(ServerPlayer puppet, Player user, InteractionHand hand, ItemStack shears) {
        ServerLevel level = puppet.serverLevel();
        CompoundTag tag = puppet.getData(WitchModAttachments.PUPPET_DATA);
        Item mushroom = "brown".equals(tag.getString("Type")) ? Items.BROWN_MUSHROOM : Items.RED_MUSHROOM;
        for (int i = 0; i < 5; i++) {
            level.addFreshEntity(new ItemEntity(level, puppet.getX(), puppet.getY(1.0), puppet.getZ(), new ItemStack(mushroom)));
        }
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.COW).toString());
        tag.remove("Type");
        tag.remove("stew_effects");
        puppet.setData(WitchModAttachments.PUPPET_DATA, tag);
        puppet.setData(WitchModAttachments.PUPPET_TYPE, PuppetType.COW.id());
        level.sendParticles(ParticleTypes.EXPLOSION, puppet.getX(), puppet.getY(0.5), puppet.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, puppet.blockPosition(), SoundEvents.MOOSHROOM_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
        shears.hurtAndBreak(1, user, LivingEntity.getSlotForHand(hand));
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        if (type == PuppetType.CHICKEN) {
            Holds.tickHold(player, type);
            if (ticksRemaining % 10 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, true, false, false));
            }
        }
        lure(player, type);
        if (type == PuppetType.PIG) {
            RideSteering.tick(player); // a rider's stick boost
        }
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        RideSteering.clear(player);
    }

    /** pig / cow / mooshroom: a moo or an oink. sheep: graze. */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        if (!Puppets.ready(player)) {
            return;
        }
        if (type.move() == PuppetMove.GRAZE) {
            Puppets.cooldown(player, Config.PUPPETEER_ANIMAL_SOUND_COOLDOWN_TICKS.get());
            graze(player);
        } else if (type.move() == PuppetMove.OINK || type.move() == PuppetMove.MOO) {
            Puppets.cooldown(player, Config.PUPPETEER_ANIMAL_SOUND_COOLDOWN_TICKS.get());
            ServerLevel level = player.serverLevel();
            level.playSound(null, player.blockPosition(), Puppets.ambientOf(type), SoundSource.PLAYERS, 1.2F, 0.9F + level.random.nextFloat() * 0.2F);
        }
        Puppets.countAction(player);
    }

    /** a chicken charges its explosive egg on that egg's own cooldown. */
    @Override
    public boolean canCharge(ServerPlayer player, PuppetType type) {
        return type == PuppetType.CHICKEN ? Puppets.ready2(player) : Puppets.ready(player);
    }

    /** a chicken lays an egg (tap) or hurls an explosive one (charged). */
    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
        if (type == PuppetType.CHICKEN) {
            ServerLevel level = player.serverLevel();
            if (charge >= Holds.MIN_CHARGE) {
                if (!Puppets.ready2(player)) {
                    return;
                }
                ExplosiveEggEntity egg = new ExplosiveEggEntity(level, player);
                egg.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
                        BowItem.getPowerForTime(charge) * Config.PUPPETEER_CHICKEN_EGG_VELOCITY.get().floatValue(), 1.0F);
                level.addFreshEntity(egg);
                level.playSound(null, player.blockPosition(), SoundEvents.EGG_THROW, SoundSource.PLAYERS, 1.0F, 0.6F);
                player.setData(WitchModAttachments.PUPPET_ACTION2_READY,
                        level.getGameTime() + Config.PUPPETEER_CHICKEN_EXPLOSIVE_COOLDOWN_TICKS.get());
            } else {
                if (!Puppets.ready(player)) {
                    return;
                }
                ItemEntity egg = new ItemEntity(level, player.getX(), player.getY() + 0.3, player.getZ(), new ItemStack(Items.EGG));
                level.addFreshEntity(egg);
                level.playSound(null, player.blockPosition(), SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1.0F,
                        (level.random.nextFloat() - level.random.nextFloat()) * 0.2F + 1.0F);
                Puppets.cooldown(player, Config.PUPPETEER_CHICKEN_EGG_COOLDOWN_TICKS.get());
            }
        }
        Puppets.countAction(player);
    }

    /** a chicken flutters down: no fall damage. */
    @Override
    public void incomingDamage(ServerPlayer player, PuppetType type, LivingIncomingDamageEvent event) {
        if (type == PuppetType.CHICKEN && event.getSource().is(DamageTypeTags.IS_FALL)) {
            event.setCanceled(true);
        }
    }

    /** milking a cow / mooshroom, a bowl of stew from a mooshroom, shearing a mooshroom or a sheep. */
    @Override
    public boolean interact(Player user, InteractionHand hand, ItemStack stack, ServerPlayer puppet, PuppetType type) {
        if (type == PuppetType.MOOSHROOM && stack.is(Items.BOWL)) {
            if (!user.level().isClientSide) {
                user.setItemInHand(hand, ItemUtils.createFilledResult(stack, user, new ItemStack(Items.MUSHROOM_STEW), false));
                puppet.serverLevel().playSound(null, puppet.blockPosition(), SoundEvents.MOOSHROOM_MILK, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            return true;
        }
        if (type == PuppetType.MOOSHROOM && stack.is(Items.SHEARS)) {
            if (!user.level().isClientSide) {
                shearMooshroom(puppet, user, hand, stack);
            }
            return true;
        }
        if ((type == PuppetType.COW || type == PuppetType.MOOSHROOM) && stack.is(Items.BUCKET)) {
            if (!user.level().isClientSide) {
                user.setItemInHand(hand, ItemUtils.createFilledResult(stack, user, new ItemStack(Items.MILK_BUCKET)));
                puppet.serverLevel().playSound(null, puppet.blockPosition(), SoundEvents.COW_MILK, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            return true;
        }
        CompoundTag tag = puppet.getData(WitchModAttachments.PUPPET_DATA);
        if (type == PuppetType.SHEEP && stack.is(Items.SHEARS) && !tag.getBoolean("Sheared")) {
            if (!user.level().isClientSide) {
                ServerLevel level = puppet.serverLevel();
                DyeColor color = DyeColor.byId(tag.getByte("Color"));
                Item wool = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(color.getName() + "_wool"));
                int count = 1 + level.random.nextInt(3);
                for (int i = 0; i < count; i++) {
                    ItemEntity drop = new ItemEntity(level, puppet.getX(), puppet.getY() + 1.0, puppet.getZ(), new ItemStack(wool));
                    drop.setDeltaMovement((level.random.nextFloat() - level.random.nextFloat()) * 0.1, level.random.nextFloat() * 0.05,
                            (level.random.nextFloat() - level.random.nextFloat()) * 0.1);
                    level.addFreshEntity(drop);
                }
                tag.putBoolean("Sheared", true);
                puppet.setData(WitchModAttachments.PUPPET_DATA, tag);
                level.playSound(null, puppet.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
                stack.hurtAndBreak(1, user, LivingEntity.getSlotForHand(hand));
            }
            return true;
        }
        // a pig takes a saddle, then a rider (who steers it with a carrot on a stick)
        return type == PuppetType.PIG && RideSteering.interact(user, hand, stack, puppet, type);
    }

    /** a chicken: a tap lays an egg (its own line, first), a hold hurls an explosive one. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        if (type == PuppetType.CHICKEN) {
            out.add(tap(Key.USE), "witchmod.puppeteer.action.lay_egg", PuppetControls.ready(player));
        }
        out.addMove(player, type);
    }
}
