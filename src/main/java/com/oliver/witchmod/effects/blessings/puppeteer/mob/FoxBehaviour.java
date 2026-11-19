package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Lunges.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.SlimeBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the fox puppet. */
public final class FoxBehaviour implements PuppetBehaviour {
    public static final FoxBehaviour INSTANCE = new FoxBehaviour();

    private FoxBehaviour() {}

    // --- fox ----------------------------------------------------------------------------------------

    private static final ResourceLocation FOX_SPRINT_ID = EffectUtil.modifierId("puppeteer_fox_sprint");

    /** fox puppets → ticks they've held food in their mouth (eaten at puppeteerFoxEatTicks). */
    private static final Map<UUID, Integer> FOX_CHEWING = EffectState.sessionConcurrent("puppeteer/fox/fox_chewing");

    /** what the fox puppet has in its mouth (the real fox's main-hand item, kept in its puppet data). */
    private static ItemStack foxHeld(ServerPlayer player) {
        ListTag hands = player.getData(WitchModAttachments.PUPPET_DATA).getList("HandItems", Tag.TAG_COMPOUND);
        return hands.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(player.registryAccess(), hands.getCompound(0));
    }

    private static void setFoxHeld(ServerPlayer player, ItemStack stack) {
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        ListTag hands = tag.getList("HandItems", Tag.TAG_COMPOUND);
        while (hands.size() < 2) {
            hands.add(new CompoundTag());
        }
        hands.set(0, stack.isEmpty() ? new CompoundTag() : stack.save(player.registryAccess()));
        tag.put("HandItems", hands);
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        FOX_CHEWING.remove(player.getUUID());
    }

    /**
     * fox upkeep: a little extra sprint, total silence while crouching, picking things up in its mouth (one item,
     * like the real fox), eating food it's held a while, and the pounce.
     */
    private static void tickFox(ServerPlayer player) {
        boolean sprinting = player.isSprinting();
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && sprinting != speed.hasModifier(FOX_SPRINT_ID)) {
            if (sprinting) {
                EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, FOX_SPRINT_ID, Config.PUPPETEER_FOX_SPRINT_BONUS.get(),
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            } else {
                speed.removeModifier(FOX_SPRINT_ID);
            }
        }
        if (player.isSilent() != player.isCrouching()) {
            player.setSilent(player.isCrouching()); // a fox stalking is a fox you don't hear
        }
        ServerLevel level = player.serverLevel();
        ItemStack held = foxHeld(player);
        if (held.isEmpty()) {
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(0.8, 0.0, 0.8),
                    i -> i.isAlive() && !i.hasPickUpDelay() && !i.getItem().isEmpty())) {
                // one in the mouth, the rest left lying — the real fox's pick-up
                setFoxHeld(player, item.getItem().split(1));
                if (item.getItem().isEmpty()) {
                    item.discard();
                }
                level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.4F);
                break;
            }
        } else if (held.has(net.minecraft.core.component.DataComponents.FOOD) && Config.PUPPETEER_FOX_EAT_TICKS.get() > 0) {
            int chew = FOX_CHEWING.merge(player.getUUID(), 1, Integer::sum);
            if (chew % 5 == 0) {
                level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, held),
                        player.getX(), player.getEyeY(), player.getZ(), 3, 0.15, 0.1, 0.15, 0.05);
            }
            if (chew >= Config.PUPPETEER_FOX_EAT_TICKS.get()) {
                net.minecraft.world.food.FoodProperties food = held.get(net.minecraft.core.component.DataComponents.FOOD);
                scaleHeal(player, food.nutrition()); // the fox mends on it, as the real one does
                player.getFoodData().eat(food.nutrition(), food.saturation());
                level.playSound(null, player.blockPosition(), SoundEvents.FOX_EAT, SoundSource.PLAYERS, 1.0F, 1.0F);
                setFoxHeld(player, ItemStack.EMPTY);
            }
        }
        tickLunge(player, PuppetType.FOX);
    }

    /** fox: let go of what's in your mouth (the drop key), tossed a little way ahead like a dropped item. */
    public static void foxDrop(ServerPlayer player) {
        if (possessed(player) != PuppetType.FOX) {
            return;
        }
        ItemStack held = foxHeld(player);
        if (held.isEmpty()) {
            return;
        }
        setFoxHeld(player, ItemStack.EMPTY);
        ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getEyeY() - 0.3, player.getZ(), held);
        dropped.setDeltaMovement(player.getLookAngle().scale(0.25));
        dropped.setPickUpDelay(40);
        player.level().addFreshEntity(dropped);
    }

    /** fox: Pounce (right-click) — a short rush (the lunge machinery, your client drives it). */
    private static void foxPounce(ServerPlayer player) {
        if (LUNGES.containsKey(player.getUUID())) {
            return;
        }
        long now = player.level().getGameTime();
        cooldown(player, Config.PUPPETEER_FOX_POUNCE_COOLDOWN_TICKS.get());
        LUNGES.put(player.getUUID(), new Lunge(now, ConcurrentHashMap.newKeySet()));
        player.setData(WitchModAttachments.PUPPET_LUNGE_START, now);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.FOX_AGGRO, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * the pounce reaches someone holding something: it's yours now — snatched from their hand into your mouth (and
     * whatever you were carrying is dropped for it). no one to rob: it bites the first thing it reaches. the rush stops.
     */
    private static void tickMug(ServerPlayer player) {
        for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.5),
                e -> e != player && e.isAlive() && !e.isSpectator() && !e.getMainHandItem().isEmpty())) {
            ItemStack loot = e.getMainHandItem().copy();
            e.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (e instanceof ServerPlayer victim) {
                victim.inventoryMenu.broadcastChanges();
            }
            foxDrop(player); // swap: what you had falls where you snatched
            setFoxHeld(player, loot);
            player.swing(InteractionHand.MAIN_HAND, true);
            player.serverLevel().playSound(null, e.blockPosition(), SoundEvents.FOX_BITE, SoundSource.PLAYERS, 1.0F, 1.2F);
            player.serverLevel().playSound(null, e.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 0.8F);
            endLunge(player);
            return;
        }
        // nothing to steal: it bites the first thing it reaches instead
        for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.5),
                e -> e != player && e.isAlive() && !e.isSpectator() && e.isPickable()
                        && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            e.hurt(player.damageSources().playerAttack(player), (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE));
            player.swing(InteractionHand.MAIN_HAND, true);
            player.serverLevel().playSound(null, e.blockPosition(), SoundEvents.FOX_BITE, SoundSource.PLAYERS, 1.0F, 1.0F);
            endLunge(player);
            return;
        }
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        tickFox(player);
    }

    /** pounce (sets its own cooldown). */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        if (!Puppets.ready(player)) {
            return;
        }
        foxPounce(player);
        Puppets.countAction(player);
    }

    @Override
    public void lungeContact(ServerPlayer player, PuppetType type, Lunges.Lunge lunge) {
        tickMug(player);
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, FOX_SPRINT_ID);
        FOX_CHEWING.remove(player.getUUID());
    }

    /** the pounce, and the drop key lets go of what's in your mouth. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        out.addMove(player, type);
        out.add(tap(Key.DROP), "witchmod.puppeteer.action.drop_item", 0L);
    }
}
