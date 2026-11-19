package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

import static com.oliver.witchmod.effects.blessings.puppeteer.Possession.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.Puppets.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.SilverfishBehaviour.*;
import static com.oliver.witchmod.effects.blessings.puppeteer.mob.ZombieBehaviour.*;
import com.oliver.witchmod.data.EffectState;

/** puppeteer: the villager puppet. */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class VillagerBehaviour implements PuppetBehaviour {
    public static final VillagerBehaviour INSTANCE = new VillagerBehaviour();

    private VillagerBehaviour() {}

    // --- villager -----------------------------------------------------------------------------------

    /** villagers due to "hmm" back at you: when, and who. */
    public record Echo(long at, net.minecraft.world.entity.npc.Villager villager) {}

    public static final List<Echo> ECHOES = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** temporary villagers standing in for a villager puppet's shop → the puppet (see openShop). */
    public static final Map<net.minecraft.world.entity.npc.AbstractVillager, UUID> SHOPS = new ConcurrentHashMap<>();

    private static final String TILL_KEY = "witchmod_till";

    /** villager: Hmm — and every villager around turns to look at you, then hmms back, one after another. */
    public static void hmm(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // your own voice is the puppet's (a wandering trader keeps its own "hmm"); the villagers that answer are real.
        level.playSound(null, player.blockPosition(), ambientOf(possessed(player)), SoundSource.PLAYERS, 1.0F, 1.0F);
        double r = Config.PUPPETEER_VILLAGER_HMM_RADIUS.get();
        long now = level.getGameTime();
        for (net.minecraft.world.entity.npc.Villager v : level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,
                player.getBoundingBox().inflate(r), v -> v.isAlive() && v.distanceToSqr(player) <= r * r)) {
            v.getBrain().setMemoryWithExpiry(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET,
                    new net.minecraft.world.entity.ai.behavior.EntityTracker(player, true), 80L);
            ECHOES.add(new Echo(now + 8 + level.random.nextInt(25), v));
        }
    }

    /**
     * wandering trader: right-click drinks a potion of invisibility and the puppet vanishes entirely (no model, no
     * nametag, no bubbles — solid-snake stealth); right-click again opts back out. also makes mobs lose track of you.
     */
    private static void toggleTraderStealth(ServerPlayer player) {
        boolean on = !player.getData(WitchModAttachments.PUPPET_STEALTH);
        player.setData(WitchModAttachments.PUPPET_STEALTH, on);
        ServerLevel level = player.serverLevel();
        if (on) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false, false)); // hidden: no icon, no particles
            level.playSound(null, player.blockPosition(), SoundEvents.WANDERING_TRADER_DRINK_POTION, SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            player.removeEffect(MobEffects.INVISIBILITY);
            level.playSound(null, player.blockPosition(), SoundEvents.WANDERING_TRADER_DRINK_MILK, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    /** whether a puppet is in wandering-trader stealth (hide the dummy entirely). */
    public static boolean stealthed(Player player) {
        return player.getData(WitchModAttachments.PUPPET_STEALTH);
    }

    public static void tickEchoes(long now) {
        for (Echo e : ECHOES) {
            if (now >= e.at()) {
                ECHOES.remove(e);
                if (e.villager().isAlive() && !e.villager().isSleeping()) {
                    e.villager().playSound(e.villager().isBaby() ? SoundEvents.VILLAGER_AMBIENT : SoundEvents.VILLAGER_TRADE,
                            1.0F, 0.9F + e.villager().getRandom().nextFloat() * 0.25F);
                }
            }
        }
    }

    /**
     * another player right-clicks a villager puppet: they get its shop — the real villager's trades (it's a stand-in
     * loaded from the puppet's data), and the emeralds they pay go in the puppet's till.
     */
    private static void openShop(ServerPlayer puppet, ServerPlayer customer) {
        ServerLevel level = puppet.serverLevel();
        Entity e = EntityType.loadEntityRecursive(puppet.getData(WitchModAttachments.PUPPET_DATA).copy(), level, x -> x);
        // a villager OR a wandering trader (both are AbstractVillager merchants) can run a shop.
        if (!(e instanceof net.minecraft.world.entity.npc.AbstractVillager shop)) {
            return;
        }
        shop.moveTo(puppet.getX(), puppet.getY(), puppet.getZ());
        boolean trader = possessed(puppet) == PuppetType.WANDERING_TRADER;
        if (shop.isBaby() || shop.getOffers().isEmpty()) {
            level.playSound(null, puppet.blockPosition(), trader ? SoundEvents.WANDERING_TRADER_NO : SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 1.0F);
            customer.displayClientMessage(Component.translatable("witchmod.puppeteer.villager_no_trades", puppet.getName())
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        CompoundTag tag = puppet.getData(WitchModAttachments.PUPPET_DATA);
        if (!tag.contains("Offers")) {
            // one that never traded rolls its trades on first look — keep them, or they'd re-roll every visit.
            CompoundTag saved = new CompoundTag();
            shop.saveWithoutId(saved);
            if (saved.contains("Offers")) {
                tag.put("Offers", saved.get("Offers"));
                puppet.setData(WitchModAttachments.PUPPET_DATA, tag);
            }
        }
        shop.setTradingPlayer(customer);
        SHOPS.put(shop, puppet.getUUID());
        int shopLevel = shop instanceof net.minecraft.world.entity.npc.Villager v ? v.getVillagerData().getLevel() : 1;
        shop.openTradingScreen(customer, Component.translatable("witchmod.puppeteer.villager_shop", puppet.getName()), shopLevel);
        level.playSound(null, puppet.blockPosition(), trader ? SoundEvents.WANDERING_TRADER_TRADE : SoundEvents.VILLAGER_TRADE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** a trade at a puppet's shop: its trades / xp carry back into the puppet, and the emeralds paid go in the till. */
    @SubscribeEvent
    public static void onTrade(net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent event) {
        UUID owner = SHOPS.get(event.getAbstractVillager());
        if (owner == null || !(event.getAbstractVillager().level() instanceof ServerLevel level)
                || !(level.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer puppet)
                || (possessed(puppet) != PuppetType.VILLAGER && possessed(puppet) != PuppetType.WANDERING_TRADER)) {
            return;
        }
        CompoundTag saved = new CompoundTag();
        event.getAbstractVillager().saveWithoutId(saved);
        CompoundTag tag = puppet.getData(WitchModAttachments.PUPPET_DATA);
        for (String key : List.of("Offers", "Xp", "VillagerData")) {
            if (saved.contains(key)) {
                tag.put(key, saved.get(key));
            }
        }
        int paid = 0;
        for (ItemStack cost : List.of(event.getMerchantOffer().getCostA(), event.getMerchantOffer().getCostB())) {
            if (cost.is(Items.EMERALD)) {
                paid += cost.getCount();
            }
        }
        if (paid > 0) {
            tag.putInt(TILL_KEY, tag.getInt(TILL_KEY) + paid);
            puppet.displayClientMessage(Component.translatable("witchmod.puppeteer.villager_till", paid, tag.getInt(TILL_KEY))
                    .withStyle(ChatFormatting.GREEN), true);
        }
        puppet.setData(WitchModAttachments.PUPPET_DATA, tag);
        boolean trader = possessed(puppet) == PuppetType.WANDERING_TRADER;
        puppet.serverLevel().playSound(null, puppet.blockPosition(),
                trader ? SoundEvents.WANDERING_TRADER_YES : SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** close every customer's shop window for this puppet (it's leaving / changing). */
    private static void closeShops(ServerPlayer puppet) {
        SHOPS.entrySet().removeIf(e -> {
            if (!e.getValue().equals(puppet.getUUID())) {
                return false;
            }
            if (e.getKey().getTradingPlayer() instanceof ServerPlayer customer) {
                customer.closeContainer();
            }
            return true;
        });
    }

    /**
     * killed by a zombie, a villager puppet doesn't die: it's infected, and you carry on as a zombie villager (its
     * profession, trades and till come along — zombie villagers keep them, ready for a cure).
     */
    private static boolean infectVillager(ServerPlayer player, DamageSource source) {
        Entity killer = source.getEntity();
        boolean zombie = killer instanceof Zombie && !(killer instanceof net.minecraft.world.entity.monster.ZombifiedPiglin)
                || killer instanceof ServerPlayer p && possessed(p) != null && possessed(p).group() == PuppetGroup.ZOMBIE;
        if (!zombie || !Config.PUPPETEER_VILLAGER_INFECTION.get()) {
            return false;
        }
        infect(player);
        return true;
    }

    /** the infection itself: villager puppet → zombie villager puppet, in place. */
    public static void infect(ServerPlayer player) {
        closeShops(player);
        CompoundTag tag = player.getData(WitchModAttachments.PUPPET_DATA);
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ZOMBIE_VILLAGER).toString());
        tag.remove("Brain");
        tag.remove("Gossips");
        player.setData(WitchModAttachments.PUPPET_DATA, tag);
        BASE_MAX.put(player.getUUID(), 20.0F);
        player.setData(WitchModAttachments.PUPPET_MAX_HEALTH, 20.0F);
        player.setData(WitchModAttachments.PUPPET_HEALTH, 20.0F);
        retype(player, PuppetType.ZOMBIE_VILLAGER);
        scaleHealth(player);
        player.setData(WitchModAttachments.PUPPET_HEALTH, player.getData(WitchModAttachments.PUPPET_MAX_HEALTH));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_INFECT, SoundSource.PLAYERS, 2.0F, 1.0F);
        player.displayClientMessage(Component.translatable("witchmod.puppeteer.villager_infected").withStyle(ChatFormatting.DARK_GREEN), true);
    }

    /** a villager's takings, taken out of the saved mob on stepping out and paid once your inventory is back. */
    private static final Map<UUID, Integer> TILL_DUE = EffectState.sessionConcurrent("puppeteer/villager/till_due");

    /** a villager hmms; a wandering trader's right-click drinks its invisibility (or opts back out). */
    @Override
    public void rightClick(ServerPlayer player, PuppetType type, @Nullable Entity target) {
        if (type == PuppetType.WANDERING_TRADER) {
            toggleTraderStealth(player);
            return;
        }
        if (!Puppets.ready(player)) {
            return;
        }
        Puppets.cooldown(player, Config.PUPPETEER_VILLAGER_HMM_COOLDOWN_TICKS.get());
        hmm(player);
        Puppets.countAction(player);
    }

    /** a wandering trader's hmm is on the LEFT-click (right-click is its stealth). */
    @Override
    public void swing(ServerPlayer player, PuppetType type) {
        if (type == PuppetType.WANDERING_TRADER && Puppets.ready(player)) {
            Puppets.cooldown(player, Config.PUPPETEER_VILLAGER_HMM_COOLDOWN_TICKS.get());
            hmm(player);
        }
    }

    /** a wandering trader doesn't hit anything. */
    @Override
    public boolean dealsMelee(ServerPlayer player, PuppetType type) {
        return type != PuppetType.WANDERING_TRADER;
    }

    /** killed by a zombie, a villager carries on as a zombie villager. */
    @Override
    public boolean cheatsDeath(ServerPlayer player, PuppetType type, DamageSource source) {
        return type == PuppetType.VILLAGER && infectVillager(player, source);
    }

    /** a hurt villager / trader panics, and the village's iron golems come for whoever did it. */
    @Override
    public void damaged(ServerPlayer player, PuppetType type, DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == player) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, false, false));
        if (!(attacker instanceof net.minecraft.world.entity.animal.IronGolem)
                && !(attacker instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            for (net.minecraft.world.entity.animal.IronGolem golem : player.serverLevel().getEntitiesOfClass(
                    net.minecraft.world.entity.animal.IronGolem.class, player.getBoundingBox().inflate(16.0), LivingEntity::isAlive)) {
                golem.setTarget(attacker);
            }
        }
    }

    /** right-clicking a villager / wandering-trader puppet opens its shop (whatever's in your hand). */
    @Override
    public boolean interact(Player user, InteractionHand hand, ItemStack stack, ServerPlayer puppet, PuppetType type) {
        if (user instanceof ServerPlayer customer && hand == InteractionHand.MAIN_HAND) {
            openShop(puppet, customer);
        }
        return true;
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        closeShops(player);
        if (player.getData(WitchModAttachments.PUPPET_STEALTH)) { // leaving a stealthed trader: drop the invisibility
            player.removeEffect(MobEffects.INVISIBILITY);
            player.setData(WitchModAttachments.PUPPET_STEALTH, false);
        }
        int till = data.getInt(TILL_KEY); // the takings stay with you, not the mob
        data.remove(TILL_KEY);
        if (till > 0) {
            TILL_DUE.merge(player.getUUID(), till, Integer::sum);
        }
    }

    /** your takings, paid out in emeralds now your inventory is back. */
    @Override
    public void released(ServerPlayer player) {
        Integer due = TILL_DUE.remove(player.getUUID());
        int till = due == null ? 0 : due;
        while (till > 0) {
            ItemStack pay = new ItemStack(Items.EMERALD, Math.min(64, till));
            till -= pay.getCount();
            if (!player.getInventory().add(pay)) {
                player.drop(pay, false);
            }
        }
    }

    /** a wandering trader's hmm is on the left-click. */
    @Override
    public boolean usesLeftClick(PuppetType type) {
        return type == PuppetType.WANDERING_TRADER;
    }
}
