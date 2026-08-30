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
import net.minecraft.world.entity.monster.Drowned;
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
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jetbrains.annotations.Nullable;

import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
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
import com.oliver.witchmod.effects.curses.CurseCutawayGag;
import com.oliver.witchmod.effects.curses.CurseDwarfism;
import com.oliver.witchmod.effects.curses.CurseExplosive;
import com.oliver.witchmod.effects.curses.CurseGassy;
import com.oliver.witchmod.effects.curses.CurseGiant;
import com.oliver.witchmod.effects.curses.CurseGlassCannon;
import com.oliver.witchmod.effects.curses.CurseInsomniac;
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

    /** Flat Footed: you can't help but be LOUD — even your chat comes out in capitals. */
    @SubscribeEvent
    static void onFlatFootedShout(net.neoforged.neoforge.event.ServerChatEvent event) {
        if (EffectManager.isActive(event.getPlayer(), com.oliver.witchmod.effects.Curses.FLAT_FOOTED)) {
            event.setMessage(net.minecraft.network.chat.Component.literal(
                    event.getRawText().toUpperCase(java.util.Locale.ROOT)));
        }
    }

    /**
     * Social Outcast: the isolation reaches chat too — a cursed player can only READ another player's message if
     * they're CLOSE to them. Far away, they still see that SOMEONE spoke (so they know chat happened) but not who
     * or what. Implemented by cancelling the vanilla broadcast and re-sending PER RECIPIENT (the only way to vary
     * a message per-viewer), and ONLY when at least one outcast is online — otherwise chat passes through vanilla
     * untouched.
     */
    @SubscribeEvent
    static void onSocialOutcastChat(net.neoforged.neoforge.event.ServerChatEvent event) {
        ServerPlayer sender = event.getPlayer();
        net.minecraft.server.MinecraftServer server = sender.getServer();
        if (server == null) {
            return;
        }
        java.util.List<ServerPlayer> players = server.getPlayerList().getPlayers();
        boolean anyOutcast = players.stream().anyMatch(p -> p != sender && EffectManager.isActive(p, Curses.SOCIAL_OUTCAST));
        if (!anyOutcast) {
            return; // nobody's outcast — let vanilla deliver chat normally
        }
        event.setCanceled(true);
        net.minecraft.network.chat.Component normal =
                net.minecraft.network.chat.Component.translatable("chat.type.text", sender.getDisplayName(), event.getMessage());
        net.minecraft.network.chat.Component muffled = net.minecraft.network.chat.Component
                .literal("Someone says something...")
                .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC);
        double reveal = Config.OUTCAST_REVEAL_DISTANCE.get();
        for (ServerPlayer recipient : players) {
            boolean muffle = recipient != sender
                    && EffectManager.isActive(recipient, Curses.SOCIAL_OUTCAST)
                    && (recipient.level() != sender.level() || recipient.distanceTo(sender) > reveal);
            recipient.sendSystemMessage(muffle ? muffled : normal);
        }
    }

    // --- Solicitor: kill → instant respawn; completing a trade → it hides for a while ------------------
    @SubscribeEvent
    static void onSolicitorKilled(LivingDeathEvent event) {
        ServerPlayer victim = solicitorVictim(event.getEntity());
        if (victim != null) {
            com.oliver.witchmod.effects.curses.CurseSolicitor.onKilled(victim);
        }
    }

    @SubscribeEvent
    static void onSolicitorTraded(net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent event) {
        ServerPlayer victim = solicitorVictim(event.getAbstractVillager());
        if (victim != null) {
            com.oliver.witchmod.effects.curses.CurseSolicitor.onTraded(victim,
                    (net.minecraft.world.entity.npc.WanderingTrader) event.getAbstractVillager());
        }
    }

    /** The still-cursed, online owner of a solicitor trader, or null if {@code e} isn't one / owner is gone. */
    @Nullable
    private static ServerPlayer solicitorVictim(net.minecraft.world.entity.Entity e) {
        if (!com.oliver.witchmod.effects.curses.CurseSolicitor.isSolicitor(e)
                || !(e.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return null;
        }
        UUID owner = com.oliver.witchmod.effects.curses.CurseSolicitor.ownerOf(e);
        if (owner == null) {
            return null;
        }
        ServerPlayer victim = level.getServer().getPlayerList().getPlayer(owner);
        return victim != null && EffectManager.isActive(victim, com.oliver.witchmod.effects.Curses.SOLICITOR) ? victim : null;
    }

    /**
     * Siren's Call: a Drowned near a soothed victim protects its own — it can't even acquire the victim as a
     * target. Vetoed at the source (its goals never lock on), rather than clearing the target each tick, which
     * flickered against those goals and let the Drowned get hits in between sweeps.
     */
    @SubscribeEvent
    static void onSirenChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer player)
                || !(event.getEntity() instanceof Drowned drowned)
                || !EffectManager.isActive(player, Curses.SIRENS_CALL)) {
            return;
        }
        double radius = Config.SIREN_DROWNED_SOOTHE_RADIUS.get();
        if (drowned.distanceToSqr(player) <= radius * radius) {
            event.setCanceled(true);
        }
    }

    /**
     * Glass Cannon runs FIRST and on its own, because it applies to any damaged entity (not just the cursed
     * player) — it has to catch both the victim taking a hit AND a cursed attacker landing a melee blow on
     * something else. It scales the amount; the rest of the mod's per-player logic runs afterwards.
     */
    /** Splitscreen: taking damage while a shared sign is open force-closes it for both players. */
    @SubscribeEvent
    static void onSplitscreenDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.oliver.witchmod.effects.curses.CurseSplitscreen.onDamaged(player);
        }
    }

    @SubscribeEvent
    static void onBedrockPausedHit(LivingIncomingDamageEvent event) {
        // Bedrock Moment (Pause): hitting a paused entity force-ends its pause (→ the catch-up burst).
        com.oliver.witchmod.effects.curses.bedrock.CurseBedrockMoment.onEntityHurt(event.getEntity());
    }

    @SubscribeEvent
    static void onCutawayHit(LivingIncomingDamageEvent event) {
        // Being hit mid-cutaway has a high chance to snap you back (the gag's effects still stick).
        if (event.getEntity() instanceof ServerPlayer player) {
            com.oliver.witchmod.effects.curses.CurseCutawayGag.onWatcherHit(player);
        }
    }

    @SubscribeEvent
    static void onGlassCannon(LivingIncomingDamageEvent event) {
        // Taking a hit: every source counts, at 200%.
        if (event.getEntity() instanceof ServerPlayer victim
                && EffectManager.isActive(victim, Curses.GLASS_CANNON)) {
            event.setAmount(CurseGlassCannon.onDamageTaken(victim, event.getAmount()));
        }
        // Dealing a hit: melee only (the direct entity of the blow is the attacker, not a projectile), 150%.
        if (event.getSource().getEntity() instanceof ServerPlayer attacker
                && CurseGlassCannon.isMelee(event.getSource().getDirectEntity(), attacker)
                && EffectManager.isActive(attacker, Curses.GLASS_CANNON)) {
            event.setAmount(CurseGlassCannon.onMeleeDealt(attacker, event.getEntity(), event.getAmount()));
        }
    }

    @SubscribeEvent
    static void onGiantAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer giant
                && EffectManager.isActive(giant, Curses.GIANT)) {
            CurseGiant.onMeleeHit(giant, event.getTarget());
        }
    }

    /** Very Infectious: a hit copies your OTHER attachments onto the victim for this many ticks (~10s). */
    private static final int VERY_INFECTIOUS_SPREAD_TICKS = 200;

    /**
     * Slime Ball / Slime Block modifiers: hitting another player spreads attachments. INFECTIOUS is a hot potato
     * — ALL of the attacker's attachments (this state included) move onto the victim with timers preserved, and
     * the attacker is left clean. VERY INFECTIOUS instead COPIES the attacker's other attachments onto the victim
     * for a short window while the attacker keeps everything.
     */
    @SubscribeEvent
    static void onInfectiousAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        if (!(event.getTarget() instanceof ServerPlayer victim) || attacker == victim) {
            return;
        }
        boolean infectious = EffectManager.isActive(attacker, Curses.INFECTIOUS);
        boolean very = EffectManager.isActive(attacker, Curses.VERY_INFECTIOUS);
        if (!infectious && !very) {
            return;
        }

        java.util.Map<net.minecraft.resources.ResourceLocation, Integer> snap = EffectManager.activeSnapshot(attacker);
        if (infectious) {
            for (var entry : snap.entrySet()) {
                EffectManager.holderOf(entry.getKey())
                        .ifPresent(h -> EffectManager.applyExact(victim, h, entry.getValue(), attacker));
            }
            EffectManager.removeAll(attacker, null); // you've passed the potato — nothing left on you
        } else {
            net.minecraft.resources.ResourceLocation veryId = Curses.VERY_INFECTIOUS.getId();
            for (var entry : snap.entrySet()) {
                if (entry.getKey().equals(veryId)) {
                    continue; // don't self-propagate the very-infectious state
                }
                EffectManager.holderOf(entry.getKey())
                        .ifPresent(h -> EffectManager.applyExact(victim, h, VERY_INFECTIOUS_SPREAD_TICKS, attacker));
            }
        }
        victim.level().playSound(null, victim.blockPosition(), net.minecraft.sounds.SoundEvents.SLIME_ATTACK,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 1.0F);
        if (victim.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ITEM_SLIME,
                    victim.getX(), victim.getY() + 1.0, victim.getZ(), 16, 0.3, 0.6, 0.3, 0.0);
        }
    }

    @SubscribeEvent
    static void onGiantDamage(LivingIncomingDamageEvent event) {
        // Take 75% less from ANY source.
        if (event.getEntity() instanceof ServerPlayer victim
                && EffectManager.isActive(victim, Curses.GIANT)) {
            event.setAmount(CurseGiant.onDamageTaken(event.getAmount()));
        }
        // Deal 80% more with MELEE (direct hits only, so a stomp/melee counts but a thrown item doesn't).
        if (event.getSource().getEntity() instanceof ServerPlayer attacker
                && CurseGiant.isMelee(event.getSource().getDirectEntity(), attacker)
                && EffectManager.isActive(attacker, Curses.GIANT)) {
            event.setAmount(CurseGiant.onMeleeDealt(event.getAmount()));
        }
    }

    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Bedrock Moment: Bluetooth (withhold + store all damage) / Delay (late fall damage). If it swallowed
        // the hit, nothing else reacts to it this tick.
        if (EffectManager.isActive(player, Curses.BEDROCK_MOMENT)
                && com.oliver.witchmod.effects.curses.bedrock.CurseBedrockMoment.onIncomingDamage(player, event)) {
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

    /**
     * Insomniac: the bed just won't take you. Only overrides when vanilla would ACTUALLY have let you sleep
     * (no problem of its own) — so a daytime bed, monsters nearby, etc. still show their normal reason rather
     * than a curse line — and blocks it with {@code OTHER_PROBLEM}, which carries no vanilla message so the
     * curse's own line stands alone. Never sleeping is exactly what keeps the phantom counter rising.
     */
    @SubscribeEvent
    static void onCanSleep(CanPlayerSleepEvent event) {
        ServerPlayer player = event.getEntity();
        if (event.getProblem() == null && EffectManager.isActive(player, Curses.INSOMNIAC)) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
            Curses.INSOMNIAC.value().onSleepDenied(player);
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
        // Bedrock Moment (Ghost Blocks): a block you place may briefly appear then reject itself.
        if (event.getEntity() instanceof ServerPlayer player && EffectManager.isActive(player, Curses.BEDROCK_MOMENT)) {
            com.oliver.witchmod.effects.curses.bedrock.CurseBedrockMoment.onBlockPlaced(player, event.getPos());
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
        // Bedrock Moment (Ghost Block Phase): during the spell, a broken block just... comes back.
        if (EffectManager.isActive(player, Curses.BEDROCK_MOMENT)
                && com.oliver.witchmod.effects.curses.bedrock.CurseBedrockMoment.onBlockBroken(player)) {
            event.setCanceled(true);
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
        if (EffectManager.isActive(player, Curses.DENSE)) {
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
            // Recovery Compass modifier: effects it tagged do NOT survive death — strip them before the respawn
            // copy runs (ACTIVE_EFFECTS is copyOnDeath, so removing here keeps them off the clone).
            java.util.Set<net.minecraft.resources.ResourceLocation> nonPersist =
                    player.getData(com.oliver.witchmod.data.WitchModAttachments.NON_PERSISTENT_EFFECTS);
            if (!nonPersist.isEmpty()) {
                for (net.minecraft.resources.ResourceLocation id : new java.util.ArrayList<>(nonPersist)) {
                    EffectManager.holderOf(id).ifPresent(h -> EffectManager.remove(player, h));
                }
                nonPersist.clear();
                player.setData(com.oliver.witchmod.data.WitchModAttachments.NON_PERSISTENT_EFFECTS, nonPersist);
            }
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
            // The Dweller: death doesn't end the curse — it just knocks the dread down a tier (two mid-hunt).
            if (EffectManager.isActive(player, Curses.THE_DWELLER)) {
                com.oliver.witchmod.effects.curses.dweller.CurseTheDweller.onVictimDeath(player);
            }
            // Bedrock Moment: a death clears the fake drowning so it doesn't carry to respawn.
            if (EffectManager.isActive(player, Curses.BEDROCK_MOMENT)) {
                com.oliver.witchmod.effects.curses.bedrock.CurseBedrockMoment.onDeath(player);
            }
            // Cutaway Gag: dying mid-cutaway strobes against the respawn screen — snap out of it cleanly.
            com.oliver.witchmod.effects.curses.CurseCutawayGag.onWatcherDeath(player);
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
        if (event.getEntity() instanceof ServerPlayer player) {
            // The Dweller: it's unhittable — swinging at the stalker swallows the swing (no bad interact packet).
            if (EffectManager.isActive(player, Curses.THE_DWELLER)
                    && com.oliver.witchmod.effects.curses.dweller.CurseTheDweller.onDwellerAttacked(player, event.getTarget())) {
                event.setCanceled(true);
                return;
            }
            // Bedrock Moment (Hit Reg): a small chance the hit just doesn't register — whiffs to empty air.
            if (EffectManager.isActive(player, Curses.BEDROCK_MOMENT)
                    && com.oliver.witchmod.effects.curses.bedrock.CurseBedrockMoment.onAttack(player)) {
                event.setCanceled(true);
                return;
            }
            if (isToolSwing(player) && EffectManager.isActive(player, Curses.BUTTERFINGERS)) {
                CurseButterfingers.onToolSwing(player);
            }
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
        // Bedrock Moment (Food Reg): a chance the food you just ate provides no hunger at all.
        if (EffectManager.isActive(player, Curses.BEDROCK_MOMENT)) {
            com.oliver.witchmod.effects.curses.bedrock.CurseBedrockMoment.onFoodEaten(player, stack);
        }
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
        long now = event.getServer().overworld().getGameTime();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (EffectManager.isActive(player, Curses.PACING)) {
                PacingManager.tickCharge(player);
            }
            // Drive any in-flight cutaway regardless of whether the curse is applied — so a debug-forced
            // cutaway fires its gag and ends, and a cutaway whose curse was removed still cleans up.
            if (player.getData(WitchModAttachments.CUTAWAY_TARGET) >= 0) {
                CurseCutawayGag.driveActiveCutaway(player, now);
            } else {
                // Anti-teleport guard: if they're NOT spectating but still hold a persisted return position
                // (relog / server restart / abnormal end), send them home so they can't be left at the vantage.
                CurseCutawayGag.recoverIfStranded(player);
            }
        }
    }

    /**
     * Anti-teleport guard on login: a player who logged out mid-cutaway comes back at the spectate vantage
     * (CUTAWAY_TARGET is sync-only, so it resets on relog and the tick driver won't pick them up). Send them
     * straight home from the persisted return position the instant they join.
     */
    @SubscribeEvent
    static void onPlayerLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            CurseCutawayGag.recoverIfStranded(sp);
        }
    }
}
