package com.oliver.witchmod.effects;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.PacingManager;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.curses.CurseAllergic;
import com.oliver.witchmod.effects.curses.CurseButterfingers;
import com.oliver.witchmod.effects.curses.CurseClumsy;
import com.oliver.witchmod.effects.curses.CurseComicRelief;
import com.oliver.witchmod.effects.curses.CurseDwarfism;
import com.oliver.witchmod.effects.curses.CurseExplosive;
import com.oliver.witchmod.effects.curses.CurseGassy;
import com.oliver.witchmod.effects.curses.CurseSocialOutcast;
import com.oliver.witchmod.effects.curses.CurseSticky;
import com.oliver.witchmod.effects.curses.CurseHeavy;
import com.oliver.witchmod.effects.curses.CursePests;
import com.oliver.witchmod.effects.curses.CurseYap;
import com.oliver.witchmod.effects.curses.CurseGluttony;
import com.oliver.witchmod.effects.curses.CurseLoadingScreen;
import com.oliver.witchmod.effects.curses.CurseSuperExplosive;
import com.oliver.witchmod.effects.curses.CurseThirstMeter;

/** Curse hooks that need a game event rather than onApply/onTick (mirrors {@link BlessingEventHandler}). */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class CurseEventHandler {
    /** Allergic: food level + saturation captured when an allergic player STARTS eating, so the Finish hook
     *  can take back exactly the right share of the real gain. Transient; UUID-keyed. */
    private static final Map<UUID, float[]> PRE_EAT_FOOD = new ConcurrentHashMap<>();

    private CurseEventHandler() {}

    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Super Explosive: a small, constant chance to detonate when taking damage. Guard against explosion
        // damage so your own blast — which does hurt you — can't recursively set you off again.
        if (!event.getSource().is(DamageTypeTags.IS_EXPLOSION)
                && EffectManager.isActive(player, Curses.SUPER_EXPLOSIVE)
                && player.getRandom().nextInt(100) < Config.SUPER_EXPLOSIVE_CHANCE_PERCENT.get()) {
            CurseSuperExplosive.detonate(player);
        }
        // Butterfingers: a hit is much likelier to knock something out of your hands than idle clumsiness.
        if (EffectManager.isActive(player, Curses.BUTTERFINGERS)) {
            CurseButterfingers.onDamaged(player);
        }
        // Gassy: a hit frightens one out of you. Explosion damage uses the much higher explosion chance —
        // and note the nearby-blast hook below fires regardless of whether the blast actually hurt you.
        if (EffectManager.isActive(player, Curses.GASSY)) {
            CurseGassy.onExternalTrigger(player, event.getSource().is(DamageTypeTags.IS_EXPLOSION)
                    ? Config.GASSY_ON_EXPLOSION_CHANCE.get()
                    : Config.GASSY_ON_DAMAGE_CHANCE.get());
        }
        // Social Outcast: whoever just hit you becomes visible for a while. Uses the DIRECT entity so an
        // arrow reveals the archer rather than the arrow.
        if (EffectManager.isActive(player, Curses.SOCIAL_OUTCAST) && event.getSource().getEntity() != null) {
            CurseSocialOutcast.onDamagedBy(player, event.getSource().getEntity());
        }
        // Yap: a reaction line for taking a hit — and, if the source is a yap-cursed player, for dealing one.
        if (EffectManager.isActive(player, Curses.YAP)) {
            CurseYap.triggerEvent(player, "hurt");
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker
                && EffectManager.isActive(attacker, Curses.YAP)) {
            CurseYap.triggerEvent(attacker, "attack");
        }
    }

    /**
     * Sticky: items refuse to leave your hands. Cancelling puts the stack back (NeoForge re-adds it), and
     * the client also suppresses the drop key so it normally never gets this far — this is the authoritative
     * backstop rather than the primary mechanism.
     */
    @SubscribeEvent
    static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !Config.STICKY_BLOCKS_DROPPING.get()
                || !EffectManager.isActive(player, Curses.STICKY)) {
            return;
        }
        event.setCanceled(true);
        // ⚠⚠ CANCELLING ALONE DESTROYS THE ITEM. NeoForge's CommonHooks.onPlayerTossEvent has already taken
        // the stack out of the inventory by the time this fires, and on cancel it does nothing but skip
        // spawning the ItemEntity — it does NOT put anything back. The stack must be handed over explicitly.
        //
        // placeItemBackInInventory is used rather than add() because it cannot void: if there is genuinely
        // no room it drops the stack instead. Losing the curse for one item is infinitely better than
        // deleting somebody's netherite.
        player.getInventory().placeItemBackInInventory(event.getEntity().getItem());
        CurseSticky.squelch(player);
    }

    /** Sticky: armour goes straight back on. See {@link CurseSticky} for why it restores rather than locks. */
    @SubscribeEvent
    static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !event.getFrom().isEmpty()                            // something was actually taken off
                && EffectManager.isActive(player, Curses.STICKY)) {
            CurseSticky.onArmourRemoved(player, event.getSlot(), event.getFrom(), event.getTo());
        }
    }

    /** Clumsy: a block you just placed has a ramping chance of going down wrong. */
    @SubscribeEvent
    static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getLevel() instanceof ServerLevel level
                && EffectManager.isActive(player, Curses.CLUMSY)) {
            CurseClumsy.onBlockPlaced(player, level, event.getPos(), event.getPlacedBlock());
        }
    }

    /** Pests: something lives in every block you break. Thirst: mining is thirsty work. */
    @SubscribeEvent
    static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (EffectManager.isActive(player, Curses.PESTS)) {
            CursePests.onBlockMined(player, event.getPos());
        }
        if (EffectManager.isActive(player, Curses.THIRST_METER)) {
            CurseThirstMeter.onStrenuousAction(player);
        }
    }

    /**
     * Thirst Meter: drinking straight from a water source, and eating while already full.
     *
     * <p><b>Both need this hook for the same underlying reason — vanilla won't do them.</b>
     * <ul>
     *   <li>Right-clicking water: vanilla's crosshair ray-trace deliberately ignores fluids, so a click
     *       aimed at water actually lands on whatever solid block is behind it. The click is therefore
     *       re-traced here WITH fluid clipping to see whether water was really what you meant.</li>
     *   <li>Eating at full hunger: {@code Player.canEat} refuses unless {@code needsFood()}, so a full-up
     *       player could never drink a juicy apple for the thirst. When that's the only thing stopping
     *       them, the use is started manually and the interaction consumed.</li>
     * </ul>
     */
    @SubscribeEvent
    static void onRightClickThirst(PlayerInteractEvent.RightClickBlock event) {
        if (consumedByThirst(event.getEntity(), event.getHand())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onRightClickItemThirst(PlayerInteractEvent.RightClickItem event) {
        if (consumedByThirst(event.getEntity(), event.getHand())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    /** @return true if thirst took the interaction, so the caller should cancel it */
    private static boolean consumedByThirst(Player rawPlayer, InteractionHand hand) {
        if (!(rawPlayer instanceof ServerPlayer player)
                || !EffectManager.isActive(player, Curses.THIRST_METER)) {
            return false;
        }
        ItemStack stack = player.getItemInHand(hand);

        // Eating at full hunger, purely for the hydration.
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null && !player.canEat(false) && CurseThirstMeter.wantsToDrink(player)) {
            player.startUsingItem(hand);
            return true;
        }

        // Drinking from the world — only with a free hand, so it never eats a real item use.
        if (!stack.isEmpty()) {
            return false;
        }
        HitResult hit = player.pick(player.blockInteractionRange(), 0.0F, true); // true = clip fluids
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        Level level = player.level();
        BlockState state = level.getBlockState(pos);

        // A cauldron: drink it down a level. Still untreated water, so the same risk applies.
        if (state.getBlock() instanceof LayeredCauldronBlock
                && state.getValue(LayeredCauldronBlock.LEVEL) > 0
                && CurseThirstMeter.drinkFromWorld(player, hand)) {
            LayeredCauldronBlock.lowerFillLevel(state, level, pos);
            return true;
        }
        // A wet sponge. Deeply unpleasant, entirely allowed, and it wrings the thing dry.
        if (state.is(Blocks.WET_SPONGE) && CurseThirstMeter.drinkFromWorld(player, hand)) {
            level.setBlockAndUpdate(pos, Blocks.SPONGE.defaultBlockState());
            level.playSound(null, pos, SoundEvents.WET_SPONGE_DRIES, SoundSource.BLOCKS, 1.0F, 1.0F);
            return true;
        }
        // Open water.
        if (level.getFluidState(pos).is(FluidTags.WATER)) {
            return CurseThirstMeter.drinkFromWorld(player, hand);
        }
        return false;
    }

    /**
     * Dwarfism: right-click a player or villager to climb on. Cancelled when it works, so the same click
     * doesn't also open a villager's trade screen on the way past.
     */
    @SubscribeEvent
    static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (EffectManager.isActive(player, Curses.DWARFISM)
                && CurseDwarfism.tryRide(player, event.getTarget())) {
            event.setCanceled(true);
        }
    }

    /**
     * Heavy: you come down hard, so fall damage is amplified. ONLY the damage — the crater is triggered from
     * the curse's own tick instead, because this event never fires in creative ({@code Player.causeFallDamage}
     * bails out immediately when {@code mayFly()} is true).
     */
    @SubscribeEvent
    static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (EffectManager.isActive(player, Curses.HEAVY)) {
            event.setDamageMultiplier(event.getDamageMultiplier() * CurseHeavy.fallDamageMultiplier());
        }
    }

    /**
     * Gassy: any explosion going off nearby can set one off sympathetically — TNT, creepers, beds, end
     * crystals, the lot, since they all route through this one event. Deliberately independent of whether the
     * blast actually damaged you: being startled doesn't require being hurt.
     */
    @SubscribeEvent
    static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Vec3 centre = event.getExplosion().center();
        double radius = Config.GASSY_EXPLOSION_HEAR_RADIUS.get();
        for (ServerPlayer player : event.getLevel().getServer().getPlayerList().getPlayers()) {
            if (player.level() == event.getLevel()
                    && player.position().distanceToSqr(centre) <= radius * radius
                    && EffectManager.isActive(player, Curses.GASSY)) {
                CurseGassy.onExternalTrigger(player, Config.GASSY_ON_EXPLOSION_CHANCE.get());
            }
        }
    }

    /** Explosive: going down sets you off. Queued rather than immediate — see {@link CurseExplosive}. */
    @SubscribeEvent
    static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (EffectManager.isActive(player, Curses.EXPLOSIVE)) {
                CurseExplosive.onDeath(player);
            }
            if (EffectManager.isActive(player, Curses.YAP)) {
                CurseYap.triggerEvent(player, "death"); // last words
            }
            // Comic Relief: a small chance of one more bolt on the spot, moments later, to burn the drops.
            if (EffectManager.isActive(player, Curses.COMIC_RELIEF)) {
                CurseComicRelief.onDeath(player);
            }
        }
    }

    /** Butterfingers: swinging a tool at a block (mining) can send it flying instead. */
    @SubscribeEvent
    static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && isToolSwing(player)
                && EffectManager.isActive(player, Curses.BUTTERFINGERS)) {
            CurseButterfingers.onToolSwing(player);
        }
    }

    /** Butterfingers: same again for swinging at an entity. */
    @SubscribeEvent
    static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isToolSwing(player)
                && EffectManager.isActive(player, Curses.BUTTERFINGERS)) {
            CurseButterfingers.onToolSwing(player);
        }
    }

    /** "Swinging a tool" = holding something with durability — tools and weapons, mods included. */
    private static boolean isToolSwing(ServerPlayer player) {
        return player.getMainHandItem().isDamageableItem();
    }

    @SubscribeEvent
    static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean allergic = EffectManager.isActive(player, Curses.ALLERGIC);
        boolean gluttony = EffectManager.isActive(player, Curses.GLUTTONY);
        // Snapshot hunger/saturation before the food lands, so Finish can measure the ACTUAL gain (vanilla
        // clamps it when you're nearly full) — used by Allergic's clawback and Gluttony's overflow/saturation.
        if (allergic || gluttony) {
            PRE_EAT_FOOD.put(player.getUUID(),
                    new float[]{player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()});
        }
        // Gluttony: wolf food down faster — the curse's one mercy, given how much of it you have to eat.
        if (gluttony) {
            int faster = Math.max(1, Math.round(event.getDuration()
                    * (1.0F - Config.GLUTTONY_EAT_SPEED_PERCENT.get() / 100.0F)));
            event.setDuration(faster);
        }
    }

    @SubscribeEvent
    static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItem();
        // Thirst Meter: anything drunk or eaten hydrates by some amount — water bottles most, then other
        // potions, then natural food, then raw, then dry processed food. No-op if the curse isn't active.
        CurseThirstMeter.onConsumed(player, stack);
        float[] beforeEating = PRE_EAT_FOOD.remove(player.getUUID());
        FoodProperties food = stack.get(DataComponents.FOOD);

        // Gluttony: the two rows are ONE bar, so nutrition vanilla couldn't fit (because the lower half was
        // already full) spills UP into the extra row instead of being wasted. Saturation is then docked, so
        // meals never stick and you have to keep grazing.
        if (food != null && beforeEating != null && EffectManager.isActive(player, Curses.GLUTTONY)) {
            int gained = player.getFoodData().getFoodLevel() - (int) beforeEating[0];
            CurseGluttony.feed(player, food.nutrition() - gained);

            float satGained = player.getFoodData().getSaturationLevel() - beforeEating[1];
            if (satGained > 0.0F) {
                float keep = satGained * (1.0F - Config.GLUTTONY_SATURATION_PENALTY_PERCENT.get() / 100.0F);
                player.getFoodData().setSaturation(Math.max(0.0F, beforeEating[1] + keep));
            }
        }

        // Allergic: react badly to anything the rolled diet forbids. This event fires AFTER vanilla applied
        // the food/potion, so we punish, strip any beneficial potion effects, and claw back the nutrition.
        if (EffectManager.isActive(player, Curses.ALLERGIC)) {
            CurseAllergic.Diet diet = CurseAllergic.dietOf(player);
            if (CurseAllergic.isForbidden(diet, stack)) {
                CurseAllergic.reactBadly(player);
                CurseAllergic.stripBeneficialEffects(player, stack);
                if (beforeEating != null) {
                    CurseAllergic.reduceGain(player, (int) beforeEating[0], beforeEating[1]);
                }
                // Rule 2: the victim discovers Allergic on their first bad reaction, not when it landed.
                Curses.ALLERGIC.get().markDiscoveredByVictim(player);
            }
        }
    }

    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // Loading Screen: EVERY door/trapdoor/fence gate, no chance roll and no cooldown. The curse is
        // completely avoidable — you choose when to touch a door — so certainty is what gives it teeth.
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());

        // Yap: opening a chest (incl. trapped/ender) is one of its rare reaction triggers.
        if (state.getBlock() instanceof AbstractChestBlock && EffectManager.isActive(player, Curses.YAP)) {
            CurseYap.triggerEvent(player, "chest");
        }

        if (!state.is(BlockTags.DOORS) && !state.is(BlockTags.TRAPDOORS) && !state.is(BlockTags.FENCE_GATES)) {
            return;
        }
        if (!EffectManager.isActive(player, Curses.LOADING_SCREEN)) {
            return;
        }
        CurseLoadingScreen.trigger(player);
        Curses.LOADING_SCREEN.value().markDiscoveredByVictim(player);
    }

    @SubscribeEvent
    static void onPacingHit(LivingIncomingDamageEvent event) {
        // Pacing: the cursed player landing a hit rolls the ramped chance (see PacingManager). Listens on
        // the same event as Super Explosive but keys off the attacker, not the victim.
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && EffectManager.isActive(attacker, Curses.PACING)) {
            PacingManager.onHit(attacker);
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        // Set off any Explosive death that has waited its tick — by now the victim's drops are real
        // entities, so the blast destroys them instead of going off before they exist.
        CurseExplosive.tickPending();

        // Comic Relief: land any parting bolt owed to someone who has already died.
        CurseComicRelief.tickPending();

        // Restore entities whose Pacing time-stop has expired, and let the ramp auto-fire (non-combat) for
        // any cursed player whose charge has maxed out without a hit.
        PacingManager.tickFreeze(event.getServer());
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (EffectManager.isActive(player, Curses.PACING)) {
                PacingManager.tickCharge(player);
            }
        }
    }
}
