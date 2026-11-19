package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.EffectState;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.PuppetControls.*;

/**
 * puppeteer: the armadillo. an armadillo's pace; lured by spider eyes; no bite. drops a scute now and then, and a brush
 * still takes one off you, as from the real one. it's skittish: whatever would scare a real one (the undead, a
 * sprinting or riding player, within its 7x2x7 range) glows for you alone, with a warning in the corner. hold
 * right-click to CURL UP — no cooldown, rooted, vanilla's damage cut ((damage - 1) / 2) and far less knockback — and now
 * and then you peek out. the curl runs through vanilla's states (rolling, scared, unrolling) with their animations;
 * PUPPET_DASH_POWER is the state (synced, for the model); a peek bumps PUPPET_ACTION_COUNT.
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class ArmadilloBehaviour implements PuppetBehaviour {
    public static final ArmadilloBehaviour INSTANCE = new ArmadilloBehaviour();

    private ArmadilloBehaviour() {}

    /** vanilla's states, in id order. */
    public static final int IDLE = 0, ROLLING = 1, SCARED = 2, UNROLLING = 3;

    /** ticks in the current curl state, and when the next scute drops. */
    private static final Map<UUID, Integer> IN_STATE = EffectState.sessionConcurrent("puppeteer/armadillo/in_state");
    private static final Map<UUID, Long> SCUTE_AT = EffectState.sessionConcurrent("puppeteer/armadillo/scute_at");

    public static int curlState(Player player) {
        return PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.ARMADILLO
                ? player.getData(WitchModAttachments.PUPPET_DASH_POWER) : IDLE;
    }

    /** rolling up, rolled, or unrolling: the shell's protection (and you can't move). */
    public static boolean curled(Player player) {
        return curlState(player) != IDLE;
    }

    /** vanilla's isScaredBy: the undead, or a sprinting / riding player, within its 7x2x7 box. */
    public static boolean scares(Player armadillo, Entity entity) {
        if (entity == armadillo || !(entity instanceof LivingEntity living) || !living.isAlive()
                || !armadillo.getBoundingBox().inflate(7.0, 2.0, 7.0).intersects(entity.getBoundingBox())) {
            return false;
        }
        if (entity.getType().is(EntityTypeTags.UNDEAD)) {
            return true;
        }
        return entity instanceof Player p && !p.isSpectator() && (p.isSprinting() || p.isPassenger());
    }

    private static void setState(ServerPlayer player, int state) {
        player.setData(WitchModAttachments.PUPPET_DASH_POWER, state);
        IN_STATE.put(player.getUUID(), 0);
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        FarmAnimalBehaviour.lure(player, type); // spider eyes draw it in
        tickScute(player);
        int state = curlState(player);
        int in = IN_STATE.merge(player.getUUID(), 1, Integer::sum);
        boolean hold = Holds.HELD.contains(player.getUUID()) && canStayRolledUp(player);
        switch (state) {
            case IDLE, UNROLLING -> {
                if (hold) {
                    setState(player, ROLLING);
                    player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ARMADILLO_ROLL, SoundSource.PLAYERS, 1.0F, 1.0F);
                } else if (state == UNROLLING && in > 30) {
                    setState(player, IDLE);
                    player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ARMADILLO_UNROLL_FINISH, SoundSource.PLAYERS, 1.0F, 1.0F);
                }
            }
            default -> {
                if (!hold) {
                    setState(player, UNROLLING);
                    player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ARMADILLO_UNROLL_START, SoundSource.PLAYERS, 1.0F, 1.0F);
                } else if (state == ROLLING && in > 10) {
                    setState(player, SCARED);
                } else if (state == SCARED && player.getRandom().nextInt(Math.max(1, Config.PUPPETEER_ARMADILLO_PEEK_CHANCE.get())) == 0) {
                    // a peek out of the shell, as the real one does when it thinks the coast is clear
                    player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ARMADILLO_PEEK, SoundSource.PLAYERS, 1.0F, 1.0F);
                    Puppets.countAction(player);
                }
            }
        }
        if (curled(player)) {
            player.setDeltaMovement(0.0, Math.min(player.getDeltaMovement().y, 0.0), 0.0); // rooted (your client holds you too)
        }
    }

    /** vanilla's canStayRolledUp: not swimming, not riding or ridden. */
    private static boolean canStayRolledUp(ServerPlayer player) {
        return !player.isInLiquid() && !player.isPassenger() && !player.isVehicle();
    }

    /** a grown armadillo sheds a scute every 5-10 minutes, as the real one does. */
    private static void tickScute(ServerPlayer player) {
        if (player.getData(WitchModAttachments.PUPPET_DATA).getInt("Age") < 0) {
            return;
        }
        long now = player.level().getGameTime();
        int min = Config.PUPPETEER_ARMADILLO_SCUTE_MIN_TICKS.get();
        int max = Math.max(min + 1, Config.PUPPETEER_ARMADILLO_SCUTE_MAX_TICKS.get());
        long at = SCUTE_AT.computeIfAbsent(player.getUUID(), k -> now + min + player.getRandom().nextInt(max - min));
        if (now >= at) {
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ARMADILLO_SCUTE_DROP, SoundSource.PLAYERS, 1.0F,
                    (player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.2F + 1.0F);
            player.spawnAtLocation(Items.ARMADILLO_SCUTE);
            SCUTE_AT.put(player.getUUID(), now + min + player.getRandom().nextInt(max - min));
        }
    }

    /** curled: vanilla's damage cut, (damage - 1) / 2. */
    @Override
    public void incomingDamage(ServerPlayer player, PuppetType type, LivingIncomingDamageEvent event) {
        if (curled(player)) {
            event.setAmount(Math.max(0.0F, (event.getAmount() - 1.0F) / 2.0F));
        }
    }

    /** curled up, you barely budge when hit. */
    @SubscribeEvent
    static void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof Player player && curled(player)) {
            event.setStrength(event.getStrength() * Config.PUPPETEER_ARMADILLO_CURL_KNOCKBACK.get().floatValue());
        }
    }

    /** a brush takes a scute off a grown one (wearing the brush, as vanilla's does). */
    @Override
    public boolean interact(Player user, InteractionHand hand, ItemStack stack, ServerPlayer puppet, PuppetType type) {
        if (!stack.canPerformAction(net.neoforged.neoforge.common.ItemAbilities.BRUSH_BRUSH)
                || puppet.getData(WitchModAttachments.PUPPET_DATA).getInt("Age") < 0) {
            return false;
        }
        if (!user.level().isClientSide) {
            puppet.spawnAtLocation(Items.ARMADILLO_SCUTE);
            puppet.serverLevel().playSound(null, puppet.blockPosition(), SoundEvents.ARMADILLO_BRUSH, SoundSource.PLAYERS, 1.0F, 1.0F);
            stack.hurtAndBreak(16, user, LivingEntity.getSlotForHand(hand));
        }
        return true;
    }

    /** the curl charges nothing — holding is just being curled. */
    @Override
    public boolean canCharge(ServerPlayer player, PuppetType type) {
        return false;
    }

    /** letting go just unrolls (tick): nothing fires. */
    @Override
    public void releaseHeld(ServerPlayer player, PuppetType type, int charge) {
    }

    @Override
    public boolean dealsMelee(ServerPlayer player, PuppetType type) {
        return false;
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        IN_STATE.remove(player.getUUID());
        SCUTE_AT.remove(player.getUUID());
    }

    /** hold to curl; a warning while something scary's about. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
        out.addMove(player, type);
        if (player.level().getEntities(player, player.getBoundingBox().inflate(7.0, 2.0, 7.0), e -> scares(player, e)).size() > 0) {
            out.note(Component.translatable("witchmod.puppeteer.armadillo_danger").withStyle(ChatFormatting.GOLD));
        }
    }

    /** a real armadillo's states, for the model. */
    public static Armadillo.ArmadilloState vanillaState(int state) {
        return Armadillo.ArmadilloState.values()[Math.max(0, Math.min(3, state))];
    }
}
