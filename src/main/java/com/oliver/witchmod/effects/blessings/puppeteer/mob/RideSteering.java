package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.EffectState;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Possession.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;

/**
 * puppeteer: riding a pig or strider puppet, as the real ones are ridden. it takes a saddle; saddled, another player
 * climbs on. a rider holding its stick (carrot / warped fungus on a stick) takes over — the puppet walks where the
 * rider looks (your own client drives it, see PuppetMovement) — and right-clicking the stick boosts it, with vanilla's
 * timing and wear. without the stick in hand the rider just sits, and you steer.
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class RideSteering {
    private RideSteering() {}

    private static final ResourceLocation BOOST_ID = EffectUtil.modifierId("puppeteer_ride_boost");

    /** a stick boost in progress: when it began and how long it runs (vanilla's 140-980 ticks). */
    private record Boost(long start, int total) {}

    private static final Map<UUID, Boost> BOOSTS = EffectState.sessionConcurrent("puppeteer/ride/boosts");

    /** the stick that steers this puppet, or null if it can't be steered. */
    @Nullable
    public static Item stickFor(@Nullable PuppetType type) {
        if (type == PuppetType.PIG) {
            return Items.CARROT_ON_A_STICK;
        }
        return type == PuppetType.STRIDER ? Items.WARPED_FUNGUS_ON_A_STICK : null;
    }

    public static boolean saddled(Player puppet) {
        return puppet.getData(WitchModAttachments.PUPPET_DATA).getBoolean("Saddle");
    }

    /** the rider steering this puppet right now — saddled, and its stick in hand — or null (both sides). */
    @Nullable
    public static Player steerer(Player puppet) {
        Item stick = stickFor(PuppetType.byId(puppet.getData(WitchModAttachments.PUPPET_TYPE)));
        return stick != null && saddled(puppet) && puppet.getFirstPassenger() instanceof Player rider && rider.isHolding(stick)
                ? rider : null;
    }

    /** another player right-clicks a pig / strider puppet: a saddle goes on, or (saddled) they climb aboard. */
    public static boolean interact(Player user, InteractionHand hand, ItemStack stack, ServerPlayer puppet, PuppetType type) {
        CompoundTag tag = puppet.getData(WitchModAttachments.PUPPET_DATA);
        boolean saddle = stack.is(Items.SADDLE) && !tag.getBoolean("Saddle") && tag.getInt("Age") >= 0;
        boolean mount = !saddle && tag.getBoolean("Saddle") && !user.isSecondaryUseActive()
                && puppet.getPassengers().isEmpty() && !user.isPassenger();
        if (!saddle && !mount) {
            return false;
        }
        if (hand != InteractionHand.MAIN_HAND || !(user instanceof ServerPlayer rider)) {
            return true;
        }
        if (saddle) {
            tag.putBoolean("Saddle", true);
            stack.consume(1, user);
            puppet.setData(WitchModAttachments.PUPPET_DATA, tag);
            puppet.serverLevel().playSound(null, puppet.blockPosition(),
                    type == PuppetType.STRIDER ? SoundEvents.STRIDER_SADDLE : SoundEvents.PIG_SADDLE, SoundSource.PLAYERS, 0.5F, 1.0F);
        } else if (!isBusy(rider)) {
            rider.startRiding(puppet, true);
            HorseBehaviour.syncRiders(puppet);
        }
        return true;
    }

    /** a rider's stick right-clicked: the boost, as vanilla's (once at a time; the stick wears, a carrot one by 7). */
    @SubscribeEvent
    static void onUseStick(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer rider) || !(rider.getVehicle() instanceof ServerPlayer puppet)) {
            return;
        }
        Item stick = stickFor(possessed(puppet));
        ItemStack stack = event.getItemStack();
        if (stick == null || !stack.is(stick) || !saddled(puppet)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (BOOSTS.containsKey(puppet.getUUID())) {
            return; // already boosting
        }
        BOOSTS.put(puppet.getUUID(), new Boost(puppet.level().getGameTime(), puppet.getRandom().nextInt(841) + 140));
        int wear = stick == Items.CARROT_ON_A_STICK ? 7 : 1; // vanilla's consumeItemDamage
        rider.setItemInHand(event.getHand(),
                stack.hurtAndConvertOnBreak(wear, Items.FISHING_ROD, rider, LivingEntity.getSlotForHand(event.getHand())));
        rider.swing(event.getHand(), true);
    }

    /** the riders kept in sync, and the boost's surge: vanilla's 1 + 1.15·sin curve over its run, as a speed modifier. */
    public static void tick(ServerPlayer puppet) {
        HorseBehaviour.syncRiders(puppet); // your own client is told who's aboard (it doesn't track itself)
        Boost boost = BOOSTS.get(puppet.getUUID());
        if (boost == null) {
            return;
        }
        long t = puppet.level().getGameTime() - boost.start();
        if (t > boost.total()) {
            BOOSTS.remove(puppet.getUUID());
            EffectUtil.removeModifier(puppet, Attributes.MOVEMENT_SPEED, BOOST_ID);
            return;
        }
        double surge = 1.15 * Mth.sin((float) t / boost.total() * Mth.PI);
        EffectUtil.addModifier(puppet, Attributes.MOVEMENT_SPEED, BOOST_ID, surge, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    public static void clear(ServerPlayer puppet) {
        BOOSTS.remove(puppet.getUUID());
        EffectUtil.removeModifier(puppet, Attributes.MOVEMENT_SPEED, BOOST_ID);
    }
}
