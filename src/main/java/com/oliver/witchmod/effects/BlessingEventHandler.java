package com.oliver.witchmod.effects;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent;
import net.neoforged.neoforge.event.enchanting.EnchantmentLevelSetEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.trading.MerchantOffer;

import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModDamageTypes;
import com.oliver.witchmod.effects.blessings.BlessingChat;
import com.oliver.witchmod.effects.blessings.BlessingIronStomach;
import com.oliver.witchmod.effects.blessings.BlessingArmy;
import com.oliver.witchmod.effects.blessings.BlessingBodyguard;
import com.oliver.witchmod.effects.blessings.BlessingHypeMan;
import com.oliver.witchmod.effects.blessings.BlessingSoulBond;
import com.oliver.witchmod.entities.BodyguardEntity;
import com.oliver.witchmod.effects.blessings.BlessingBlacksmith;
import com.oliver.witchmod.effects.blessings.BlessingAngler;
import com.oliver.witchmod.effects.blessings.BlessingImmortality;
import com.oliver.witchmod.effects.blessings.BlessingLastStand;
import com.oliver.witchmod.effects.blessings.BlessingMainCharacter;
import com.oliver.witchmod.effects.blessings.BlessingThickSkinned;
import com.oliver.witchmod.effects.blessings.BlessingTwistOfFate;

/** blessing hooks that need a game event rather than onApply/onTick/onRemove. */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class BlessingEventHandler {
    private BlessingEventHandler() {}

    @SubscribeEvent
    static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // immortality: death instead drops you into a slow golden "rebuild" recovery (never the respawn
        // screen), repeatable with a growing recovery time, breaking after immortalityMaxUses. See
        // blessingImmortality for the rebuild + finish.
        if (EffectManager.isActive(player, Blessings.IMMORTALITY)) {
            event.setCanceled(true);
            BlessingImmortality.beginRecovery(player);
            return;
        }
        // last Stand: revive with a brief invuln comeback window (off cooldown); on cooldown you die normally.
        if (EffectManager.isActive(player, Blessings.LAST_STAND) && BlessingLastStand.tryTrigger(player)) {
            event.setCanceled(true);
            Blessings.LAST_STAND.get().markDiscoveredByVictim(player);
        }
    }

    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // immortality: while you're mid-rebuild you can't be touched — nothing gets to finish you off (and
        // this is also what keeps you safe without a persistent invulnerable flag that could leak on relog).
        if (EffectManager.isActive(player, Blessings.IMMORTALITY) && BlessingImmortality.isRecovering(player)) {
            event.setCanceled(true);
            return;
        }
        // last Stand: the brief post-revive invuln window (checked without isActive, so it still holds on the
        // final revive that consumed the blessing).
        if (BlessingLastStand.isInvulnerable(player)) {
            event.setCanceled(true);
            return;
        }
        // layered_hide synergy (with Tank): the negation floor is raised by a point.
        double thickFloor = Config.THICKSKIN_DAMAGE_FLOOR.get()
                + (com.oliver.witchmod.synergy.Synergies.LAYERED_HIDE.activeFor(player)
                        ? Config.LAYERED_HIDE_FLOOR_BONUS.get() : 0.0);
        if (EffectManager.isActive(player, Blessings.THICK_SKINNED)
                && event.getAmount() <= thickFloor
                && !event.getSource().is(DamageTypes.DROWN)) {
            // small tick damage ignored — single events at or below the floor are fully negated, with feedback.
            // drowning is deliberately NOT mitigated (you still need air).
            event.setCanceled(true);
            BlessingThickSkinned.neutralise(player);
            Blessings.THICK_SKINNED.get().markDiscoveredByVictim(player);
            return;
        }
        // twist of Fate: a small chance (off cooldown) that a hit just doesn't happen, with a chime + particles.
        if (EffectManager.isActive(player, Blessings.TWIST_OF_FATE) && BlessingTwistOfFate.tryNegate(player)) {
            event.setCanceled(true);
            Blessings.TWIST_OF_FATE.get().markDiscoveredByVictim(player);
        }
        // bouncy "get off me": whatever MELEES you pings straight back off your rubbery hide.
        if (EffectManager.isActive(player, Curses.BOUNCY)
                && event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.LivingEntity attacker
                && attacker != player) {
            com.oliver.witchmod.effects.curses.CurseBouncy.bounceAway(player, attacker, Config.BOUNCY_ENTITY_FORCE.get());
            Curses.BOUNCY.get().markDiscoveredByVictim(player);
        }
        // flight: taking a hit spends 20% of the energy bar and knocks you out of flight for a moment.
        if (!event.isCanceled() && EffectManager.isActive(player, Blessings.FLIGHT)) {
            com.oliver.witchmod.effects.blessings.BlessingFlight.onHurt(player);
        }
        // disguise: taking a hit breaks the costume (handled server-side — flip to the real model + start the timer).
        // with the concealment synergy it cascades prop -> animal -> player one step per hit instead.
        if (EffectManager.isActive(player, Blessings.DISGUISE)) {
            if (com.oliver.witchmod.synergy.Synergies.CONCEALMENT.activeFor(player)) {
                com.oliver.witchmod.effects.blessings.BlessingDisguise.concealCascade(player);
            } else {
                com.oliver.witchmod.effects.blessings.BlessingDisguise.breakDisguise(player);
            }
        }
    }

    @SubscribeEvent
    static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (EffectManager.isActive(player, Curses.BOUNCY)) {
            // fall damage negated; the actual upward rebound is applied client-side (ClientCurseHandler.tickBouncy)
            // so it can build height with repeated jumps.
            event.setDamageMultiplier(0.0F);
            Curses.BOUNCY.get().markDiscoveredByVictim(player);
        }
        // twinkletoes: total fall-damage immunity. Only fire discovery if the fall would ACTUALLY have hurt,
        // so stepping off a block doesn't spend the "first save" moment.
        if (EffectManager.isActive(player, Blessings.TWINKLETOES)) {
            boolean wouldHaveHurt = event.getDistance() > 3.0F && event.getDamageMultiplier() > 0.0F;
            event.setDamageMultiplier(0.0F);
            if (wouldHaveHurt) {
                Blessings.TWINKLETOES.get().markDiscoveredByVictim(player);
                // A soft landing puff at the feet — more of it the harder the fall you just shrugged off.
                ServerLevel level = player.serverLevel();
                int count = (int) Math.min(24, 6 + event.getDistance());
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.05, player.getZ(),
                        count, 0.28, 0.02, 0.28, 0.02);
            }
        }
        // low Gravity: soft landings — scale fall damage down (on top of the lighter gravity already
        // reducing your terminal velocity).
        if (EffectManager.isActive(player, Blessings.LOW_GRAVITY)) {
            event.setDamageMultiplier(event.getDamageMultiplier()
                    * com.oliver.witchmod.effects.blessings.BlessingLowGravity.fallDamageMultiplier());
        }
    }

    /**
     * Iron Stomach: eating a "bad" food strips its downside (the food effects it just applied are removed)
     * and gives you MORE hunger + saturation from it than normal. Fires on the eat's Finish, right after
     * vanilla has applied the food.
     */
    @SubscribeEvent
    static void onIronStomachEat(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !EffectManager.isActive(player, Blessings.IRON_STOMACH)
                || !BlessingIronStomach.BAD_FOODS.contains(event.getItem().getItem())) {
            return;
        }
        // no penalty: clear the negative effects a bad food inflicts (just applied, so this removes them).
        player.removeEffect(MobEffects.HUNGER);
        player.removeEffect(MobEffects.POISON);
        player.removeEffect(MobEffects.CONFUSION);

        // more out of it than anyone else: bonus hunger + saturation on top of what you just gained.
        FoodProperties food = event.getItem().get(DataComponents.FOOD);
        if (food != null) {
            int bonusNutrition = (int) Math.ceil(food.nutrition() * (Config.IRONSTOMACH_HUNGER_MULT.get() - 1.0));
            float bonusSaturationMod = (float) (food.saturation() * Config.IRONSTOMACH_SATURATION_MULT.get());
            if (bonusNutrition > 0) {
                player.getFoodData().eat(bonusNutrition, bonusSaturationMod);
            }
        }
        Blessings.IRON_STOMACH.get().markDiscoveredByVictim(player);
    }

    /** excavation: each block you break ramps your (invisible) mining-speed bonus up. */
    @SubscribeEvent
    static void onExcavationBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && EffectManager.isActive(player, Blessings.EXCAVATION)) {
            ((com.oliver.witchmod.effects.blessings.BlessingExcavation) Blessings.EXCAVATION.get()).onBlockBroken(player);
        }
    }

    /**
     * Excavation: apply the ramped bonus to your break speed — same effect as Haste, but no icon/particles.
     * Reads the SYNCED bonus (not isActive), so it fires on the CLIENT too — mining is client-authoritative,
     * so that's the side that actually needs to dig faster.
     */
    @SubscribeEvent
    static void onExcavationBreakSpeed(net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        float bonus = event.getEntity().getData(WitchModAttachments.EXCAVATION_BONUS);
        if (bonus > 0.0F) {
            event.setNewSpeed(event.getNewSpeed() * (1.0F + bonus));
        }
    }

    /** angler: a catch has a decent chance to pull out multiple things, and a smaller chance for a comical surprise. */
    @SubscribeEvent
    static void onAnglerFished(net.neoforged.neoforge.event.entity.player.ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !EffectManager.isActive(player, Blessings.ANGLER)) {
            return;
        }
        // multi-catch: sometimes reel in an extra copy of what you caught (luck is untouched — that's Luck's job).
        if (player.getRandom().nextDouble() < Config.ANGLER_MULTI_CHANCE.get()) {
            java.util.List<net.minecraft.world.item.ItemStack> extra = new java.util.ArrayList<>();
            for (net.minecraft.world.item.ItemStack drop : event.getDrops()) {
                extra.add(drop.copy());
            }
            event.getDrops().addAll(extra);
        }
        // comical surprise dragged out of the water.
        if (player.getRandom().nextDouble() < Config.ANGLER_SURPRISE_CHANCE.get()) {
            BlessingAngler.spawnSurprise(player, event.getHookEntity());
        }
        Blessings.ANGLER.get().markDiscoveredByVictim(player);
    }

    /** nightowl: Blindness and Darkness can't be applied to you (immune to darkening effects). */
    @SubscribeEvent
    static void onNightowlEffect(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !EffectManager.isActive(player, Blessings.NIGHTOWL)) {
            return;
        }
        var effect = event.getEffectInstance().getEffect();
        if (effect == MobEffects.BLINDNESS || effect == MobEffects.DARKNESS) {
            event.setResult(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /** personal Trainer: villagers gain extra XP from your trades and level up fast — the whole crowd learns. */
    @SubscribeEvent
    static void onPersonalTrainerTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !EffectManager.isActive(player, Blessings.TRAINER)
                || !(event.getAbstractVillager() instanceof Villager villager)) {
            return;
        }
        MerchantOffer offer = event.getMerchantOffer();
        int extra = (offer.shouldRewardExp() && offer.getXp() > 0)
                ? (int) Math.round(offer.getXp() * (Config.TRAINER_VILLAGER_XP_MULT.get() - 1.0)) : 0;
        if (extra > 0) {
            coachVillager(player, villager, extra, true);
            double r = Config.TRAINER_NEARBY_RADIUS.get();
            if (r > 0) {
                for (Villager other : player.serverLevel().getEntitiesOfClass(Villager.class,
                        villager.getBoundingBox().inflate(r), v -> v != villager && v.isAlive())) {
                    coachVillager(player, other, extra, false);
                }
            }
        }
        Blessings.TRAINER.get().markDiscoveredByVictim(player);
    }

    /** silver villager synergy (Silver Tongue + Disguise): a villager-shaped trader sometimes waives a trade's cost. */
    @SubscribeEvent
    static void onSilverVillagerRefund(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !com.oliver.witchmod.synergy.Synergies.SILVER_VILLAGER.activeFor(player)
                || player.getRandom().nextInt(100) >= Config.SILVER_VILLAGER_REFUND_CHANCE.get()) {
            return;
        }
        MerchantOffer offer = event.getMerchantOffer();
        refundCost(player, offer.getCostA());
        refundCost(player, offer.getCostB());
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("witchmod.silver_tongue.refund"), true);
        player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(),
                player.getY() + 1.0, player.getZ(), 8, 0.3, 0.4, 0.3, 0.0);
    }

    private static void refundCost(ServerPlayer player, net.minecraft.world.item.ItemStack cost) {
        if (cost.isEmpty()) {
            return;
        }
        net.minecraft.world.item.ItemStack give = cost.copy();
        if (!player.getInventory().add(give)) {
            player.drop(give, false);
        }
    }

    /** add coached XP to one villager, schedule its fast level-up, and (for the traded one) live-update the screen. */
    private static void coachVillager(ServerPlayer player, Villager villager, int extra, boolean traded) {
        villager.setVillagerXp(villager.getVillagerXp() + extra);
        scheduleVillagerLevelUp(villager);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(),
                villager.getY() + villager.getBbHeight() * 0.6, villager.getZ(), traded ? 14 : 8, 0.4, 0.5, 0.4, 0.0);
        level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.6F, 1.2F);
        // the traded villager's screen keeps the xp it synced at open — re-push so its bar fills live (showProgress
        // hard TRUE: a villager always shows the bar; the server menu's own flag reads false and hid it).
        if (traded && player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu menu) {
            player.sendMerchantOffers(menu.containerId, villager.getOffers(),
                    villager.getVillagerData().getLevel(), villager.getVillagerXp(), true, menu.canRestock());
        }
    }

    /**
     * Silver Tongue: villager trades are much cheaper — and the UI shows it. When you open a merchant we knock
     * each offer's cost down via its special-price diff (which drives BOTH the displayed price and what you
     * actually pay) and re-sync the offers to your screen. Vanilla resets that diff from reputation whenever
     * ANY player starts trading, so this doesn't leak the discount to others — it re-applies each time you open.
     */
    @SubscribeEvent
    static void onSilverTongueOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getContainer() instanceof net.minecraft.world.inventory.MerchantMenu menu)
                || !EffectManager.isActive(player, Blessings.SILVER_TONGUE)) {
            return;
        }
        double discount = Config.SILVERTONGUE_DISCOUNT.get();
        net.minecraft.world.item.trading.MerchantOffers offers = menu.getOffers();
        for (MerchantOffer offer : offers) {
            int baseCount = offer.getBaseCostA().getCount();
            offer.setSpecialPriceDiff(-(int) Math.floor(baseCount * discount)); // costA -> ~(1-discount) of base, min 1
        }
        player.sendMerchantOffers(menu.containerId, offers, menu.getTraderLevel(), menu.getTraderXp(),
                readShowProgress(menu), menu.canRestock());
        Blessings.SILVER_TONGUE.get().markDiscoveredByVictim(player);
    }

    private static boolean readShowProgress(net.minecraft.world.inventory.MerchantMenu menu) {
        try {
            java.lang.reflect.Field f = net.minecraft.world.inventory.MerchantMenu.class.getDeclaredField("showProgressBar");
            f.setAccessible(true);
            return f.getBoolean(menu);
        } catch (ReflectiveOperationException e) {
            return true; // villagers show the level bar; worst case is a cosmetic bar on a wandering trader
        }
    }

    /**
     * Studious: all XP you take in is multiplied. Hooked on {@code PickupXp} (fires the instant an orb is
     * collected, BEFORE the mending/XP split) and boosts the orb's own value — so every real XP source (mobs,
     * mining, furnaces, fishing, breeding, bottles o' enchanting) is multiplied reliably, even through Mending
     * gear. Using {@code XpChange} instead silently missed all XP that Mending ate before it ever became XP.
     */
    @SubscribeEvent
    static void onStudiousXp(PlayerXpEvent.PickupXp event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !EffectManager.isActive(player, Blessings.STUDIOUS)) {
            return;
        }
        net.minecraft.world.entity.ExperienceOrb orb = event.getOrb();
        int before = orb.value;
        int boosted = (int) Math.round(before * Config.STUDIOUS_XP_MULT.get());
        if (boosted > before) {
            orb.value = boosted;
            Blessings.STUDIOUS.get().markDiscoveredByVictim(player);
            // feedback that the XP was multiplied — an amethyst chime + subtle green motes. Rate-limited, since a
            // pile of orbs fires this many times a tick and we don't want a machine-gun of chimes.
            long now = player.serverLevel().getGameTime();
            if (now - STUDIOUS_FX_LAST.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) >= 6L) {
                STUDIOUS_FX_LAST.put(player.getUUID(), now);
                ServerLevel level = player.serverLevel();
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(),
                        player.getY() + player.getBbHeight() * 0.6, player.getZ(), 6, 0.3, 0.4, 0.3, 0.0);
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS, 0.5F, 1.6F);
            }
        }
    }

    /** studious: last game-tick the multiply-FX played, per player, to rate-limit the chime across orb piles. */
    private static final Map<UUID, Long> STUDIOUS_FX_LAST = new ConcurrentHashMap<>();

    // villager's private level-up scheduling fields (Mojang-mapped at runtime), resolved once. This is the same
    // pair vanilla's rewardTradeXp writes: the flag says "level up on the next merchant update" and the timer
    // (which only counts down while you're NOT trading with it) delays it a beat, so trades don't regenerate
    // out from under your open screen.
    private static java.lang.reflect.Field villagerLevelUpFlag;
    private static java.lang.reflect.Field villagerUpdateTimer;
    private static boolean villagerFieldsResolved;

    private static void resolveVillagerFields() {
        if (villagerFieldsResolved) {
            return;
        }
        villagerFieldsResolved = true;
        try {
            villagerLevelUpFlag = Villager.class.getDeclaredField("increaseProfessionLevelOnUpdate");
            villagerLevelUpFlag.setAccessible(true);
            villagerUpdateTimer = Villager.class.getDeclaredField("updateMerchantTimer");
            villagerUpdateTimer.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            WitchMod.LOGGER.error("[Trainer] could not resolve villager level-up fields — villagers won't level "
                    + "up faster: {}", e.toString());
        }
    }

    /**
     * If the villager's XP is now at/over its level-up threshold, schedule the profession level-up exactly the
     * way vanilla does — needed because vanilla's own check ran on the base XP before this event fired.
     */
    private static void scheduleVillagerLevelUp(Villager villager) {
        int level = villager.getVillagerData().getLevel();
        if (!VillagerData.canLevelUp(level) || villager.getVillagerXp() < VillagerData.getMaxXpPerLevel(level)) {
            return; // not actually over the threshold yet
        }
        resolveVillagerFields();
        if (villagerLevelUpFlag == null || villagerUpdateTimer == null) {
            return;
        }
        try {
            if (!villagerLevelUpFlag.getBoolean(villager)) { // don't reset an already-scheduled level-up
                villagerLevelUpFlag.setBoolean(villager, true);
                villagerUpdateTimer.setInt(villager, Config.TRAINER_LEVELUP_DELAY_TICKS.get());
            }
        } catch (ReflectiveOperationException e) {
            WitchMod.LOGGER.error("[Trainer] couldn't schedule villager level-up: {}", e.toString());
        }
    }

    /** main Character: while you're powered up, mark anyone you hit so their knockback is multiplied. */
    @SubscribeEvent
    static void onMainCharAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getTarget() instanceof net.minecraft.world.entity.LivingEntity victim) {
            int tier = player.getData(WitchModAttachments.MAINCHAR_TIER);
            if (tier > 0 && EffectManager.isActive(player, Blessings.MAIN_CHARACTER)) {
                BlessingMainCharacter.queueKnockback(victim, BlessingMainCharacter.knockbackMultiplier(tier));
                ServerLevel level = player.serverLevel();
                double vy = victim.getY() + victim.getBbHeight() * 0.6;
                level.sendParticles(ParticleTypes.FIREWORK, victim.getX(), vy, victim.getZ(),
                        tier >= 2 ? 16 : 10, 0.3, 0.4, 0.3, 0.12);
                level.sendParticles(ParticleTypes.CRIT, victim.getX(), vy, victim.getZ(), 8, 0.3, 0.3, 0.3, 0.15);
            }
        }
    }

    /** applies the queued Main Character knockback multiplier when the hit's knockback resolves. */
    @SubscribeEvent
    static void onMainCharKnockback(LivingKnockBackEvent event) {
        float mult = BlessingMainCharacter.consumeKnockback(event.getEntity());
        if (mult != 1.0F) {
            event.setStrength(event.getStrength() * mult);
        }
    }

    // --- Chat blessing: feed the entertainment score from everything the streamer does ------------------
    private static boolean chatOn(net.minecraft.world.entity.Entity e) {
        return e instanceof ServerPlayer p && EffectManager.isActive(p, Blessings.CHAT);
    }

    @SubscribeEvent
    static void onChatAttack(AttackEntityEvent event) {
        if (chatOn(event.getEntity())) {
            boolean pvp = event.getTarget() instanceof Player;
            BlessingChat.quiet((ServerPlayer) event.getEntity(),
                    pvp ? "combat_pvp" : "combat_pve",
                    pvp ? Config.CHAT_PVP_SCORE.get() : Config.CHAT_PVE_SCORE.get());
        }
    }

    // --- Gladiator blessing -----------------------------------------------------------------------------
    @SubscribeEvent
    static void onGladiatorRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof ServerPlayer player
                && com.oliver.witchmod.effects.blessings.BlessingGladiator.isParryWeapon(event.getItemStack())
                && EffectManager.isActive(player, Blessings.GLADIATOR)) {
            com.oliver.witchmod.effects.blessings.BlessingGladiator.tryStartParry(player);
        }
    }

    @SubscribeEvent
    static void onGladiatorParry(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !EffectManager.isActive(player, Blessings.GLADIATOR)) {
            return;
        }
        // melee (the DIRECT entity is the living attacker) → full parry + riposte. Projectiles go through
        // onGladiatorProjectileParry (reflect) instead.
        if (event.getSource().getDirectEntity() != null
                && com.oliver.witchmod.effects.blessings.BlessingGladiator.tryParry(player, event.getSource().getDirectEntity())) {
            event.setCanceled(true);
            return;
        }
        // anything else a shield could block (explosions, unreflected hurting projectiles...) → EMPTY parry.
        if (com.oliver.witchmod.effects.blessings.BlessingGladiator.tryEmptyParry(player, event.getSource())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onDisguiseAttack(AttackEntityEvent event) {
        // throwing a punch blows your cover too, not just taking one.
        if (event.getEntity() instanceof ServerPlayer player && EffectManager.isActive(player, Blessings.DISGUISE)) {
            com.oliver.witchmod.effects.blessings.BlessingDisguise.breakDisguise(player);
        }
    }

    @SubscribeEvent
    static void onThunderHit(LivingDamageEvent.Post event) {
        // A Thunder-blessed player's MELEE hit (direct entity == the player) discharges the stored static.
        if (event.getSource().getDirectEntity() instanceof ServerPlayer player
                && EffectManager.isActive(player, Blessings.THUNDER)
                && event.getEntity() instanceof net.minecraft.world.entity.LivingEntity victim
                && victim != player) {
            com.oliver.witchmod.effects.blessings.BlessingThunder.discharge(player, victim, event.getNewDamage());
        }
    }

    @SubscribeEvent
    static void onGladiatorLeewayRecord(LivingDamageEvent.Post event) {
        // A melee hit that WASN'T parried — remember it briefly so a slightly-late parry (leeway) can still
        // catch it. Only fires for hits that landed (a parried hit is cancelled before this).
        if (event.getEntity() instanceof ServerPlayer player
                && EffectManager.isActive(player, Blessings.GLADIATOR)
                && event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.LivingEntity attacker
                && attacker != player) {
            com.oliver.witchmod.effects.blessings.BlessingGladiator.recordLeewayHit(player, attacker.getId(), event.getNewDamage());
        }
    }

    @SubscribeEvent
    static void onGladiatorProjectileParry(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit
                && hit.getEntity() instanceof ServerPlayer player
                && EffectManager.isActive(player, Blessings.GLADIATOR)
                && com.oliver.witchmod.effects.blessings.BlessingGladiator.tryParryProjectile(player, event.getProjectile())) {
            event.setCanceled(true); // caught and reflected, no damage
        }
    }

    // --- Cow blessing -----------------------------------------------------------------------------------
    private static void cowMilk(Player milker, InteractionHand hand, ItemStack bucket, ServerPlayer cow) {
        milker.setItemInHand(hand, ItemUtils.createFilledResult(bucket, milker, new ItemStack(Items.MILK_BUCKET)));
        cow.serverLevel().playSound(null, cow.blockPosition(), SoundEvents.COW_MILK, SoundSource.PLAYERS, 1.0F, 1.0F);
        Blessings.COW.get().markDiscoveredByVictim(cow);
    }

    @SubscribeEvent
    static void onCowSelfMilk(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof ServerPlayer player
                && event.getItemStack().is(Items.BUCKET)
                && EffectManager.isActive(player, Blessings.COW)) {
            cowMilk(player, event.getHand(), event.getItemStack(), player);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    static void onCowMilkedByOther(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide()
                && event.getTarget() instanceof ServerPlayer cow
                && event.getItemStack().is(Items.BUCKET)
                && EffectManager.isActive(cow, Blessings.COW)) {
            cowMilk(event.getEntity(), event.getHand(), event.getItemStack(), cow);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    // --- Tank blessing ----------------------------------------------------------------------------------
    @SubscribeEvent
    static void onTankRegen(LivingHealEvent event) {
        // small heals are natural regen ticks — slow those slightly. Potions/food (bigger heals) are untouched.
        if (event.getEntity() instanceof ServerPlayer player
                && event.getAmount() <= 1.0F
                && EffectManager.isActive(player, Blessings.TANK)) {
            event.setAmount(event.getAmount() * Config.TANK_REGEN_MULT.get().floatValue());
        }
    }

    /** sanguine: natural regen throttled to a fraction — you don't heal by resting, you heal by bleeding others. */
    @SubscribeEvent
    static void onSanguineRegen(LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && EffectManager.isActive(player, Blessings.SANGUINE)) {
            event.setAmount(com.oliver.witchmod.effects.blessings.BlessingSanguine.throttleRegen(event.getAmount()));
        }
    }

    /** sanguine: lifesteal a share of the damage you deal — melee AND projectile (the attacker is the source entity). */
    @SubscribeEvent
    static void onSanguineLifesteal(LivingDamageEvent.Post event) {
        if (event.getSource().getEntity() instanceof ServerPlayer attacker
                && attacker != event.getEntity() && attacker.isAlive()
                && EffectManager.isActive(attacker, Blessings.SANGUINE)) {
            com.oliver.witchmod.effects.blessings.BlessingSanguine.lifesteal(attacker, event.getEntity(), event.getNewDamage());
        }
    }

    /** sonar: every hit you take sharpens your senses — shave time off the next ping. */
    @SubscribeEvent
    static void onSonarDamaged(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && EffectManager.isActive(player, Blessings.SONAR)) {
            com.oliver.witchmod.effects.blessings.BlessingSonar.onDamaged(player);
        }
    }

    /** vein Miner: breaking an ore/log fells the whole connected vein/tree. */
    @SubscribeEvent
    static void onVeinMine(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && event.getLevel() instanceof ServerLevel level
                && EffectManager.isActive(player, Blessings.VEIN_MINER)) {
            com.oliver.witchmod.effects.blessings.BlessingVeinMiner.onBreak(player, level, event.getPos(), event.getState());
        }
    }

    // --- Prop Hunt blessing: any real action (not moving/jumping) drops the disguise --------------------
    private static void propHuntAction(net.minecraft.world.entity.Entity e) {
        if (e instanceof ServerPlayer p) {
            com.oliver.witchmod.effects.blessings.BlessingPropHunt.actionTaken(p);
        }
    }

    @SubscribeEvent
    static void onPropHuntAttack(AttackEntityEvent event) {
        propHuntAction(event.getEntity());
    }

    @SubscribeEvent
    static void onPropHuntLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        propHuntAction(event.getEntity());
    }

    @SubscribeEvent
    static void onPropHuntUseItem(PlayerInteractEvent.RightClickItem event) {
        propHuntAction(event.getEntity());
    }

    @SubscribeEvent
    static void onPropHuntUseBlock(PlayerInteractEvent.RightClickBlock event) {
        propHuntAction(event.getEntity());
    }

    @SubscribeEvent
    static void onPropHuntInteract(PlayerInteractEvent.EntityInteract event) {
        propHuntAction(event.getEntity());
    }

    // --- Backstabbing blessing --------------------------------------------------------------------------
    @SubscribeEvent
    static void onBackstab(LivingIncomingDamageEvent event) {
        // A MELEE hit (direct entity IS the attacking player) landed from behind: multiply the FINAL damage,
        // so it stacks on top of crits/enchants already baked into the amount.
        if (event.getSource().getDirectEntity() instanceof ServerPlayer attacker
                && event.getSource().getEntity() == attacker
                && event.getEntity() instanceof net.minecraft.world.entity.LivingEntity victim && victim != attacker
                && EffectManager.isActive(attacker, Blessings.BACKSTABBING)) {
            float mult = com.oliver.witchmod.effects.blessings.BlessingBackstabbing.onMeleeHit(attacker, victim);
            if (mult != 1.0F) {
                event.setAmount(event.getAmount() * mult);
            }
        }
    }

    @SubscribeEvent
    static void onBackstabKnockback(LivingKnockBackEvent event) {
        float mult = com.oliver.witchmod.effects.blessings.BlessingBackstabbing.knockbackMultiplier(event.getEntity());
        if (mult != 1.0F) {
            event.setStrength(event.getStrength() * mult);
        }
    }

    // --- Heavy Hitter blessing --------------------------------------------------------------------------
    @SubscribeEvent
    static void onHeavyHitterDamage(LivingIncomingDamageEvent event) {
        // A MELEE hit by a Heavy Hitter (direct entity IS the attacking player) marks the victim for this
        // tick, so the knockback hook below can double the knockback.
        if (event.getSource().getDirectEntity() instanceof ServerPlayer attacker
                && event.getSource().getEntity() == attacker
                && event.getEntity() instanceof net.minecraft.world.entity.LivingEntity victim && victim != attacker
                && EffectManager.isActive(attacker, Blessings.HEAVY_HITTER)) {
            com.oliver.witchmod.effects.blessings.BlessingHeavyHitter.onMeleeHit(attacker, victim);
            event.setAmount(event.getAmount() + Config.HEAVY_HITTER_FLAT_DAMAGE.get().floatValue()); // flat extra hurt
        }
    }

    @SubscribeEvent
    static void onHeavyHitterKnockback(LivingKnockBackEvent event) {
        float mult = com.oliver.witchmod.effects.blessings.BlessingHeavyHitter.knockbackMultiplier(event.getEntity());
        if (mult != 1.0F) {
            event.setStrength(event.getStrength() * mult);
        }
    }

    // --- Enchanter blessing -----------------------------------------------------------------------------
    @SubscribeEvent
    static void onEnchanterCost(PlayerXpEvent.LevelChange event) {
        // soften the level loss while an enchanting table is open (so we don't touch anvils or XP overflow).
        if (event.getEntity() instanceof ServerPlayer player
                && event.getLevels() < 0
                && player.containerMenu instanceof net.minecraft.world.inventory.EnchantmentMenu
                && EffectManager.isActive(player, Blessings.ENCHANTER)) {
            double keep = 1.0 - Config.ENCHANTER_XP_REDUCTION.get();
            event.setLevels((int) Math.ceil(event.getLevels() * keep)); // getLevels() < 0; ceil -> less lost
            Blessings.ENCHANTER.get().markDiscoveredByVictim(player);
        }
    }

    @SubscribeEvent
    static void onEnchanterLevels(EnchantmentLevelSetEvent event) {
        // not player-aware, so find the Enchanter standing at this table and bump the offered level.
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().closerThan(event.getPos(), 8.0) && EffectManager.isActive(p, Blessings.ENCHANTER)) {
                event.setEnchantLevel(event.getEnchantLevel() + Config.ENCHANTER_LEVEL_BONUS.get());
                return;
            }
        }
    }

    @SubscribeEvent
    static void onEnchanterResult(PlayerEnchantItemEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && EffectManager.isActive(player, Blessings.ENCHANTER)) {
            com.oliver.witchmod.effects.blessings.BlessingEnchanter.stripBadEnchants(event.getEnchantedItem());
            Blessings.ENCHANTER.get().markDiscoveredByVictim(player);
        }
    }

    @SubscribeEvent
    static void onOceansTargetVeto(LivingChangeTargetEvent event) {
        // aggressive mobs won't lock onto an Ocean's-blessed player while they're in the water.
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player
                && player.isInWater()
                && EffectManager.isActive(player, Blessings.OCEANS_BLESSING)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onBerserkerHit(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getTarget() instanceof net.minecraft.world.entity.LivingEntity
                && EffectManager.isActive(player, Blessings.BERSERKER)) {
            com.oliver.witchmod.effects.blessings.BlessingBerserker.onHit(player);
        }
    }

    @SubscribeEvent
    static void onChatCrit(CriticalHitEvent event) {
        if (event.isCriticalHit() && chatOn(event.getEntity())) {
            BlessingChat.quiet((ServerPlayer) event.getEntity(), "crit", Config.CHAT_CRIT_SCORE.get());
        }
    }

    @SubscribeEvent
    static void onChatKillOrDeath(LivingDeathEvent event) {
        // A kill by the streamer — the biggest highlights there are.
        if (event.getSource().getEntity() instanceof ServerPlayer killer
                && killer != event.getEntity() && EffectManager.isActive(killer, Blessings.CHAT)) {
            boolean pvp = event.getEntity() instanceof Player;
            BlessingChat.highlight(killer, pvp ? "pvp_kill" : "kill",
                    pvp ? Config.CHAT_PVP_KILL_SCORE.get() : Config.CHAT_KILL_SCORE.get());
        }
        // the streamer dying — interest tanks and chat spams the mocking gifs.
        if (chatOn(event.getEntity())) {
            BlessingChat.onStreamerDeath((ServerPlayer) event.getEntity());
        }
    }

    @SubscribeEvent
    static void onChatDamaged(LivingDamageEvent.Post event) {
        if (chatOn(event.getEntity())) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            float hp = player.getHealth();
            if (hp <= 0.0F) {
                return; // that's a death, handled above
            }
            if (event.getNewDamage() >= 2.0F && hp <= (float) (double) Config.CHAT_CLUTCH_HP.get()) {
                BlessingChat.highlight(player, "clutch", Config.CHAT_CLUTCH_SCORE.get());
            } else if (event.getNewDamage() >= (float) (double) Config.CHAT_DAMAGE_THRESHOLD.get()) {
                BlessingChat.highlight(player, "damage", Config.CHAT_DAMAGE_SCORE.get());
            }
        }
    }

    @SubscribeEvent
    static void onChatFall(LivingFallEvent event) {
        if (chatOn(event.getEntity()) && event.getDistance() >= (float) (double) Config.CHAT_FALL_THRESHOLD.get()) {
            BlessingChat.highlight((ServerPlayer) event.getEntity(), "fall", Config.CHAT_FALL_SCORE.get());
        }
    }

    @SubscribeEvent
    static void onChatBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && EffectManager.isActive(player, Blessings.CHAT)) {
            if (isValuableOre(event.getState())) {
                BlessingChat.highlight(player, "diamond", Config.CHAT_ORE_SCORE.get());
            } else {
                BlessingChat.quiet(player, "mining", Config.CHAT_MINE_SCORE.get());
            }
        }
    }

    private static boolean isValuableOre(net.minecraft.world.level.block.state.BlockState state) {
        var b = state.getBlock();
        return b == net.minecraft.world.level.block.Blocks.DIAMOND_ORE
                || b == net.minecraft.world.level.block.Blocks.DEEPSLATE_DIAMOND_ORE
                || b == net.minecraft.world.level.block.Blocks.EMERALD_ORE
                || b == net.minecraft.world.level.block.Blocks.DEEPSLATE_EMERALD_ORE
                || b == net.minecraft.world.level.block.Blocks.ANCIENT_DEBRIS;
    }

    @SubscribeEvent
    static void onChatPlace(BlockEvent.EntityPlaceEvent event) {
        if (chatOn(event.getEntity())) {
            BlessingChat.quiet((ServerPlayer) event.getEntity(), "building", Config.CHAT_BUILD_SCORE.get());
        }
    }

    @SubscribeEvent
    static void onChatFished(ItemFishedEvent event) {
        if (chatOn(event.getEntity())) {
            BlessingChat.highlight((ServerPlayer) event.getEntity(), "fish", Config.CHAT_FISH_SCORE.get());
        }
    }

    @SubscribeEvent
    static void onChatTamed(AnimalTameEvent event) {
        if (chatOn(event.getTamer())) {
            BlessingChat.highlight((ServerPlayer) event.getTamer(), "tame", Config.CHAT_TAME_SCORE.get());
        }
    }

    @SubscribeEvent
    static void onChatTraded(TradeWithVillagerEvent event) {
        if (chatOn(event.getEntity())) {
            BlessingChat.quiet((ServerPlayer) event.getEntity(), "trade", Config.CHAT_TRADE_SCORE.get());
        }
    }

    @SubscribeEvent
    static void onChatEat(LivingEntityUseItemEvent.Finish event) {
        if (chatOn(event.getEntity()) && event.getItem().has(DataComponents.FOOD)) {
            BlessingChat.quiet((ServerPlayer) event.getEntity(), "eat", Config.CHAT_EAT_SCORE.get());
        }
    }

    @SubscribeEvent
    static void onBruteKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && EffectManager.isActive(player, Blessings.BRUTE)
                && com.oliver.witchmod.effects.blessings.BlessingBrute.isCharged(player)) {
            event.setCanceled(true); // nothing shoves a fully-charged brute
        }
    }

    @SubscribeEvent
    static void onAnchorKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && EffectManager.isActive(player, Blessings.ANCHOR)) {
            event.setCanceled(true); // immovable — no attack/projectile knockback at all
            Blessings.ANCHOR.get().markDiscoveredByVictim(player); // discovered on the first shove resisted
            // very, very subtle 'braced' feedback: a soft chain tink + a few sparks settling at the feet, so a
            // shove that went nowhere still reads as being planted rather than as nothing happening.
            ServerLevel level = player.serverLevel();
            level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.18F, 0.7F);
            level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.1, player.getZ(),
                    4, 0.22, 0.03, 0.22, 0.0);
        }
    }

    @SubscribeEvent
    static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !EffectManager.isActive(player, Blessings.BLACKSMITH)) {
            return;
        }
        // "Too Expensive!" is the anvil's cost cap (default 40). Lift it on the live menu so no repair is ever
        // blocked. Done on the menu directly because the event fires before vanilla recomputes the cost.
        if (player.containerMenu instanceof net.minecraft.world.inventory.AnvilMenu anvil) {
            anvil.setMaximumCost(Integer.MAX_VALUE);
        }
        // much cheaper: wipe the accumulated "prior work" penalty from the inputs — that's the exponential
        // driver of anvil costs (1,3,7,15,...), and a skilled blacksmith just doesn't rack it up. Vanilla then
        // recomputes the (now-low) cost from these. Also set the event cost for any custom-recipe output path.
        clearPriorWork(event.getLeft());
        clearPriorWork(event.getRight());
        event.setCost(Math.max(1L, Math.round(event.getCost() * BlessingBlacksmith.ANVIL_COST_MULT)));
    }

    private static void clearPriorWork(net.minecraft.world.item.ItemStack stack) {
        if (!stack.isEmpty() && stack.getOrDefault(net.minecraft.core.component.DataComponents.REPAIR_COST, 0) > 0) {
            stack.set(net.minecraft.core.component.DataComponents.REPAIR_COST, 0);
        }
    }

    /** player uuid -> game tick their next laugh-track reaction is allowed. */
    private static final java.util.Map<java.util.UUID, Long> LAUGH_TRACK_COOLDOWN = new java.util.HashMap<>();

    @SubscribeEvent
    static void onServerChat(ServerChatEvent event) {
        if (EffectManager.isActive(event.getPlayer(), Blessings.LAUGH_TRACK)) {
            triggerLaughTrack(event.getPlayer());
        }
    }

    /** a server-wide crowd reaction for the speaker, on the shared cooldown — also called by Yap's comedic_timing. */
    public static void triggerLaughTrack(ServerPlayer speaker) {
        long now = speaker.serverLevel().getGameTime();
        Long ready = LAUGH_TRACK_COOLDOWN.get(speaker.getUUID());
        if (ready != null && now < ready) {
            return;
        }
        LAUGH_TRACK_COOLDOWN.put(speaker.getUUID(), now + Config.LAUGHTRACK_COOLDOWN.get());

        // mostly laughs (90%), the odd cheer. Sent per-listener so it's a global "TV laugh track", not positional.
        var sound = speaker.getRandom().nextDouble() < Config.LAUGHTRACK_CHEER_CHANCE.get()
                ? com.oliver.witchmod.data.WitchModSounds.LAUGHTRACK_CHEER.get()
                : com.oliver.witchmod.data.WitchModSounds.LAUGHTRACK_LAUGH.get();
        float volume = Config.LAUGHTRACK_VOLUME.get().floatValue();
        for (ServerPlayer listener : speaker.getServer().getPlayerList().getPlayers()) {
            listener.playNotifySound(sound, SoundSource.PLAYERS, volume, 1.0F);
        }
        Blessings.LAUGH_TRACK.get().markDiscoveredByVictim(speaker);
    }

    /**
     * Fortune: an ore-tag block hands the breaker a few EXTRA drops, additive on top of enchantment Fortune
     * (this event fires with the already-rolled drop list in hand). A silk-touched drop of the ore block
     * itself is left alone so it can't be duplicated into free ore blocks.
     */
    @SubscribeEvent
    static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)
                || !EffectManager.isActive(player, Blessings.FORTUNE)
                || !event.getState().is(Tags.Blocks.ORES)) {
            return;
        }
        List<ItemEntity> drops = event.getDrops();
        if (drops.isEmpty()) {
            return;
        }
        int extra = rollExtraDrops(player.getRandom());
        if (extra <= 0) {
            return;
        }
        Item blockItem = event.getState().getBlock().asItem();
        for (ItemEntity entity : drops) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty() || stack.is(blockItem)) {
                continue; // skip a silk-touched ore-block drop — only the resource is boosted
            }
            stack.grow(extra);
            Blessings.FORTUNE.get().markDiscoveredByVictim(player);
            return;
        }
    }

    /** 0..max extra drops, peaked at {@code fortuneExtraMode} (a triangular roll). */
    private static int rollExtraDrops(RandomSource random) {
        int min = Config.FORTUNE_EXTRA_MIN.get();
        int max = Config.FORTUNE_EXTRA_MAX.get();
        if (max <= min) {
            return Math.max(0, min);
        }
        int mode = Mth.clamp(Config.FORTUNE_EXTRA_MODE.get(), min, max);
        double range = max - min;
        double c = (mode - min) / range;
        double u = random.nextDouble();
        double x = u < c
                ? min + Math.sqrt(u * range * (mode - min))
                : max - Math.sqrt((1.0 - u) * range * (max - mode));
        return (int) Math.round(x);
    }

    /**
     * Peace: quietly cancel most hostile NATURAL spawns near a blessed player (the other half — shrunken
     * detection — lives in {@link Blessings#PEACE}'s tick). Only monsters, only natural/chunk spawns, so
     * spawners and breeding are untouched.
     */
    @SubscribeEvent
    static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        if (event.getEntity().getType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        Player nearby = event.getLevel().getNearestPlayer(event.getX(), event.getY(), event.getZ(),
                Config.PEACE_RADIUS.get(),
                e -> e instanceof ServerPlayer sp && EffectManager.isActive(sp, Blessings.PEACE));
        if (nearby == null) {
            return;
        }
        // allow only PEACE_SPAWN_RATE_MULT of them; cancel the rest.
        if (event.getEntity().getRandom().nextDouble() >= Config.PEACE_SPAWN_RATE_MULT.get()) {
            event.setSpawnCancelled(true);
        }
    }

    /** luck: discovered the first time it could actually have mattered — reeling in a catch. */
    @SubscribeEvent
    static void onItemFished(ItemFishedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && EffectManager.isActive(player, Blessings.LUCK)) {
            Blessings.LUCK.get().markDiscoveredByVictim(player);
        }
    }

    /** luck: also discovered on opening a loot-tabled container (its loot table is still pending pre-open). */
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !EffectManager.isActive(player, Blessings.LUCK)) {
            return;
        }
        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (be instanceof RandomizableContainerBlockEntity chest && chest.getLootTable() != null) {
            Blessings.LUCK.get().markDiscoveredByVictim(player);
        }
    }

    /**
     * Army: a nearby hostile can't even acquire you as a target — vetoed at the source, so its own goals never
     * lock on (rather than clearing the target afterward, which flickered and let hits slip through).
     */
    /** unseen: a mob can't lock onto a cloaked player it isn't within reveal distance of. */
    @SubscribeEvent
    static void onDisguiseChangeTarget(LivingChangeTargetEvent event) {
        // A convincing livestock disguise: hostiles won't lock onto a currently-disguised player (DISGUISE_TYPE
        // is -1 while the costume is broken, so a broken cover CAN be targeted).
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player
                && event.getEntity() instanceof net.minecraft.world.entity.monster.Enemy
                && player.getData(WitchModAttachments.DISGUISE_TYPE) >= 0) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onUnseenChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player
                && EffectManager.isActive(player, Blessings.UNSEEN)
                && event.getEntity().distanceToSqr(player)
                        > Config.UNSEEN_REVEAL_DISTANCE.get() * Config.UNSEEN_REVEAL_DISTANCE.get()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onArmyChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer player)
                || !(event.getEntity() instanceof Mob mob)
                || !BlessingArmy.isConscriptable(mob)
                || !EffectManager.isActive(player, Blessings.ARMY)) {
            return;
        }
        double radius = Config.ARMY_RADIUS.get();
        if (mob.distanceToSqr(player) <= radius * radius) {
            event.setCanceled(true);
        }
    }

    /**
     * Army: pacified hostiles are neutral, so their damage to you is cancelled outright — and being hit by a
     * GENUINE aggressor (a player, a golem, anything not pacified) rallies the horde onto it.
     */
    @SubscribeEvent
    static void onArmyHit(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !EffectManager.isActive(player, Blessings.ARMY)) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker == null || attacker == player) {
            return;
        }
        if (attacker instanceof Mob mob && BlessingArmy.isConscriptable(mob)) {
            event.setCanceled(true); // neutral to you — no harm, and not something to rally against
            return;
        }
        BlessingArmy.markDefend(player, attacker);
    }

    /**
     * Reflect: a projectile about to strike you is caught and sent precisely back at its shooter, faster. We
     * cancel the impact (so it deals no damage and keeps flying), re-owner it to the player (so it can't
     * re-hit them but CAN hurt the original shooter) and re-aim it. It doesn't home — a straight, quick shot
     * the attacker can dodge.
     */
    @SubscribeEvent
    static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof ServerPlayer player)
                || !EffectManager.isActive(player, Blessings.REFLECT)) {
            return;
        }
        Projectile projectile = event.getProjectile();
        Entity shooter = projectile.getOwner();
        if (shooter == null || shooter == player) {
            return; // nothing to send it back to, and never reflect your own shot
        }
        Vec3 dir = shooter.getEyePosition().subtract(projectile.position());
        if (dir.lengthSqr() < 1.0e-4) {
            return;
        }
        event.setCanceled(true);
        double mult = Config.REFLECT_VELOCITY_MULT.get();
        if (com.oliver.witchmod.synergy.Synergies.DUELISTS.activeFor(player)) {
            mult *= Config.REFLECT_GLADIATOR_VELOCITY_MULT.get(); // duellist's flourish: send it further
        }
        float speed = (float) (projectile.getDeltaMovement().length() * mult);
        projectile.setOwner(player);
        projectile.shoot(dir.x, dir.y, dir.z, speed, (float) (double) Config.REFLECT_INACCURACY.get());
        // nudge it a block out of your hitbox toward the shooter so it doesn't immediately re-collide with you.
        Vec3 out = dir.normalize();
        projectile.setPos(player.getX() + out.x, player.getEyeY() + out.y * 0.5, player.getZ() + out.z);
        Blessings.REFLECT.get().markDiscoveredByVictim(player);
    }

    /**
     * Soul Bond: your bound eats {@code soulBondDamageShare} of every hit you take, in your place, and a
     * golden trail flicks out to show who paid. Shared damage is never itself re-shared (its own type is
     * skipped), so two bonded souls can't ping-pong a hit forever.
     */
    @SubscribeEvent
    static void onSoulBondDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer caster)
                || !EffectManager.isActive(caster, Blessings.SOUL_BOND)
                || event.getSource().is(WitchModDamageTypes.SOUL_BOND)) {
            return;
        }
        ServerLevel level = caster.serverLevel();
        LivingEntity bound = BlessingSoulBond.boundEntity(level, caster);
        if (bound == null || bound == caster) {
            return;
        }
        double radius = Config.SOULBOND_RADIUS.get();
        if (bound.distanceToSqr(caster) > radius * radius) {
            return; // the tether only carries while they're actually near you
        }
        float shared = (float) (event.getAmount() * Config.SOULBOND_DAMAGE_SHARE.get());
        if (shared <= 0.0F) {
            return;
        }
        event.setAmount(event.getAmount() - shared);
        bound.hurt(WitchModDamageTypes.soulBond(level, caster), shared);
        soulBondTrail(level, caster, bound);
        Blessings.SOUL_BOND.get().markDiscoveredByVictim(caster); // discovered the first time damage is shared
    }

    /** guardian Angel (Guiding light): you deal bonus damage to the highlighted highest-health foe. */
    @SubscribeEvent
    static void onGuardianGuidingBonus(LivingDamageEvent.Pre event) {
        if (event.getSource().getEntity() instanceof ServerPlayer owner
                && EffectManager.isActive(owner, Blessings.GUARDIAN_ANGEL)) {
            float bonus = com.oliver.witchmod.effects.blessings.BlessingGuardianAngel.guidingBonus(owner, event.getEntity());
            if (bonus > 0.0F) {
                event.setNewDamage(event.getNewDamage() + bonus);
            }
        }
    }

    /** guardian Angel: attacking anything puts you "in combat" (enables the zap) and sulks at villager/pet hits. */
    @SubscribeEvent
    static void onGuardianOwnerAttack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer owner && EffectManager.isActive(owner, Blessings.GUARDIAN_ANGEL)) {
            com.oliver.witchmod.effects.blessings.BlessingGuardianAngel.onOwnerAttack(owner, event.getTarget());
        }
    }

    /** guardian Angel: breaking a hard block, or a run of them, earns Haste + a watchful hover. */
    @SubscribeEvent
    static void onGuardianMined(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer owner && EffectManager.isActive(owner, Blessings.GUARDIAN_ANGEL)) {
            float hardness = event.getState().getDestroySpeed(owner.level(), event.getPos());
            com.oliver.witchmod.effects.blessings.BlessingGuardianAngel.onOwnerMined(owner, event.getPos(), hardness);
        }
    }

    /** guardian Angel: a killing blow is negated — the guardian dies in your place, leaving you at 1 HP. */
    @SubscribeEvent
    static void onGuardianLethal(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer owner
                && EffectManager.isActive(owner, Blessings.GUARDIAN_ANGEL)
                && event.getNewDamage() >= owner.getHealth()
                && com.oliver.witchmod.effects.blessings.BlessingGuardianAngel.tryGuardianSacrifice(owner)) {
            event.setNewDamage(Math.max(0.0F, owner.getHealth() - 1.0F)); // survive at 1 HP
        }
    }

    /** guardian Angel: the owner being hit makes the guardian panic around them. */
    @SubscribeEvent
    static void onGuardianOwnerHit(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer owner
                && EffectManager.isActive(owner, Blessings.GUARDIAN_ANGEL)
                && event.getSource().getEntity() instanceof LivingEntity attacker
                && attacker != owner
                && !(attacker instanceof net.minecraft.world.entity.animal.allay.Allay)) {
            com.oliver.witchmod.effects.blessings.BlessingGuardianAngel.onOwnerAttacked(owner, attacker);
        }
    }

    /** guardian Angel: the allay can be killed — its owner then waits out the respawn cooldown. */
    @SubscribeEvent
    static void onGuardianAllayDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.animal.allay.Allay allay)
                || !(allay.level() instanceof ServerLevel level)) {
            return;
        }
        String ownerTag = allay.getTags().stream().filter(t -> t.startsWith("guardian_")).findFirst().orElse(null);
        if (ownerTag == null) {
            return;
        }
        com.oliver.witchmod.effects.blessings.BlessingGuardianAngel.onAllayKilled(allay);
        // companionship banter: a surviving bodyguard/solicitor may remark on the angel falling.
        try {
            ServerPlayer owner = level.getServer().getPlayerList()
                    .getPlayer(java.util.UUID.fromString(ownerTag.substring("guardian_".length())));
            if (owner != null && EffectManager.isActive(owner, Blessings.GUARDIAN_ANGEL)) {
                CompanionshipBanter.reactToDeath(owner, "guardian");
            }
        } catch (IllegalArgumentException ignored) {
            // malformed tag — nothing to do
        }
    }

    /** guardian Angel: a player being kidnapped can't dismount the allay to escape. */
    @SubscribeEvent
    static void onGuardianKidnapDismount(net.neoforged.neoforge.event.entity.EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityMounting() instanceof ServerPlayer p
                && event.getEntityBeingMounted() instanceof net.minecraft.world.entity.animal.allay.Allay
                && com.oliver.witchmod.effects.blessings.BlessingGuardianAngel.isKidnapVictim(p)) {
            event.setCanceled(true);
        }
    }

    /** guardian Angel: killing the last nearby hostile can trigger a celebratory firework display. */
    @SubscribeEvent
    static void onGuardianKill(LivingDeathEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.monster.Enemy
                && event.getSource().getEntity() instanceof ServerPlayer killer
                && EffectManager.isActive(killer, Blessings.GUARDIAN_ANGEL)) {
            com.oliver.witchmod.effects.blessings.BlessingGuardianAngel.onOwnerKill(killer);
        }
    }

    /**
     * Bodyguard: anything striking the anchor — a player, a zombie, a golem — commits the bodyguard to
     * ATTACKING it, so it's an actual guard and not just set dressing.
     */
    @SubscribeEvent
    static void onBodyguardAnchorHit(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer anchor)
                || !EffectManager.isActive(anchor, Blessings.BODYGUARD)
                || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || attacker == anchor
                || attacker instanceof BodyguardEntity) {
            return;
        }
        BodyguardEntity bodyguard = BlessingBodyguard.get(anchor);
        if (bodyguard != null) {
            bodyguard.escalateToAttacking(attacker, true); // a real threat — pursue it on the longer leash
        }
    }

    /** bodyguard: the entity dying no longer breaks the blessing — a replacement is hired 6 minutes later. */
    @SubscribeEvent
    static void onBodyguardDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof BodyguardEntity bodyguard)
                || !(bodyguard.level() instanceof ServerLevel level)) {
            return;
        }
        UUID anchorId = bodyguard.getAnchorId();
        if (anchorId == null) {
            return;
        }
        BlessingBodyguard.forget(anchorId); // drop the tracking entry for the dead one
        ServerPlayer anchor = level.getServer().getPlayerList().getPlayer(anchorId);
        if (anchor != null && EffectManager.isActive(anchor, Blessings.BODYGUARD)) {
            BlessingBodyguard.onBodyguardDeath(anchor); // schedule the replacement + announce the fall
            CompanionshipBanter.reactToDeath(anchor, "bodyguard"); // the solicitor may pipe up
        }
    }


    /** A golden trail from the caster to the entity that just took a share of their damage. */
    private static void soulBondTrail(ServerLevel level, ServerPlayer caster, LivingEntity bound) {
        Vec3 from = caster.getEyePosition();
        Vec3 to = bound.position().add(0.0, bound.getBbHeight() * 0.5, 0.0);
        Vec3 delta = to.subtract(from);
        int points = Math.max(6, (int) (delta.length() * 2.0));
        for (int i = 0; i <= points; i++) {
            Vec3 p = from.add(delta.scale((double) i / points));
            level.sendParticles(BlessingSoulBond.GOLD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // --- Hype Man: the crowd praises the blessed player for what they do ---------------------------------

    /** combat — swinging on anything earns a cheer. */
    @SubscribeEvent
    static void onHypeManCombat(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && EffectManager.isActive(player, Blessings.HYPE_MAN)) {
            BlessingHypeMan.praise(player, "combat");
        }
    }

    /** picking an item up off the floor. */
    @SubscribeEvent
    static void onHypeManPickup(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer player && EffectManager.isActive(player, Blessings.HYPE_MAN)) {
            BlessingHypeMan.praise(player, "pickup");
        }
    }

    /**
     * Immortality drawback: the gear you spilled can be looted by anyone EXCEPT you — but only WHILE you're
     * still rebuilding. Each drop is tagged with the owner's UUID; if that owner tries to pick it back up
     * during their rebuild, the pickup is vetoed. Once the whole sequence finishes (they've stood back up)
     * they can collect whatever's left, so this is the "easy to be looted" window, not a permanent lockout.
     */
    @SubscribeEvent
    static void onImmortalityDropPickup(ItemEntityPickupEvent.Pre event) {
        String owner = event.getItemEntity().getData(WitchModAttachments.IMMORTALITY_DROP_OWNER);
        if (owner.isEmpty()
                || !(event.getPlayer() instanceof ServerPlayer picker)
                || !owner.equals(picker.getUUID().toString())) {
            return;
        }
        if (BlessingImmortality.isRecovering(picker)) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    /** opening a chest/barrel (a ChestMenu) — "looting". */
    @SubscribeEvent
    static void onHypeManLoot(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getContainer() instanceof ChestMenu
                && EffectManager.isActive(player, Blessings.HYPE_MAN)) {
            BlessingHypeMan.praise(player, "loot");
        }
    }

    /** placing a block — the crowd compliments your builds. */
    @SubscribeEvent
    static void onHypeManBuild(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && EffectManager.isActive(player, Blessings.HYPE_MAN)) {
            BlessingHypeMan.praise(player, "building");
        }
    }

    /** iron Lung: breathe in blocks — suffocation is negated (and drowning too, backing up Water Breathing). */
    @SubscribeEvent
    static void onIronLungDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && EffectManager.isActive(player, Blessings.IRON_LUNG)
                && (event.getSource().is(DamageTypes.IN_WALL) || event.getSource().is(DamageTypes.DROWN))) {
            event.setCanceled(true);
            Blessings.IRON_LUNG.get().markDiscoveredByVictim(player); // discovered on the first breath saved
        }
    }
}
