package com.oliver.witchmod.effects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.ActiveEffects;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * the one listener per game event that hands it to the {@link Effect} hooks of whoever it concerns — the
 * victim, the attacker, the breaker. each player's active effects are walked in hook order (cached on
 * {@link ActiveEffects}), so an event costs one attachment lookup however many effects there are. dispatch stops
 * once an effect cancels the event, the same rule neoforge applies between listeners.
 *
 * <p>events that aren't about the effect holder (a mob's death, a spawn, a nearby explosion) stay as small
 * listeners in the effect's own class.
 */
@EventBusSubscriber(modid = WitchMod.MODID)
public final class EffectHooks {
    private EffectHooks() {}

    @FunctionalInterface
    private interface Hook {
        void call(Effect effect, ServerPlayer self);
    }

    /** set by {@link #consumeHit()} during a damage dispatch. */
    private static boolean consumed;

    /**
     * from {@link Effect#onHurt}: this hit is held back for later (bedrock moment's bluetooth / delay), so no other
     * effect reacts to it now — they will when it's really delivered.
     */
    public static void consumeHit() {
        consumed = true;
    }

    /**
     * calls {@code hook} on each of {@code entity}'s active effects, if it's a server player.
     *
     * @return true if dispatch stopped early (the event was cancelled or the hit consumed)
     */
    private static boolean dispatch(@Nullable Entity entity, @Nullable Event event, Hook hook) {
        if (!(entity instanceof ServerPlayer self)) {
            return false;
        }
        ActiveEffects active = self.getExistingDataOrNull(WitchModAttachments.ACTIVE_EFFECTS);
        if (active == null || active.isEmpty()) {
            return false;
        }
        ICancellableEvent cancellable = event instanceof ICancellableEvent c ? c : null;
        for (ActiveEffects.Hooked entry : active.hooked()) {
            if (!active.isActive(entry.id())) {
                continue; // removed by an earlier hook this event
            }
            hook.call(entry.effect(), self);
            if (consumed || cancellable != null && cancellable.isCanceled()) {
                return true;
            }
        }
        return false;
    }

    // ---- damage ----

    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        boolean outer = consumed;
        consumed = false;
        try {
            LivingEntity victim = event.getEntity();
            if (!dispatch(victim, event, (e, self) -> e.onHurt(self, event))) {
                dispatch(event.getSource().getEntity(), event, (e, self) -> e.onHurtOther(self, victim, event));
            }
        } finally {
            consumed = outer;
        }
    }

    @SubscribeEvent
    static void onDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        dispatch(victim, event, (e, self) -> e.onDamagePre(self, event));
        dispatch(event.getSource().getEntity(), event, (e, self) -> e.onDamagePreOther(self, victim, event));
    }

    @SubscribeEvent
    static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        dispatch(victim, event, (e, self) -> e.onDamaged(self, event));
        dispatch(event.getSource().getEntity(), event, (e, self) -> e.onDamagedOther(self, victim, event));
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead instanceof ServerPlayer player) {
            // anything that cheats death goes first; a death that didn't happen isn't heard by anything else
            if (dispatch(player, event, (e, self) -> {
                if (e.cheatsDeath(self, event)) {
                    event.setCanceled(true);
                }
            })) {
                return;
            }
            EffectManager.stripNonPersistent(player);
            dispatch(player, event, (e, self) -> e.onDeath(self, event));
        }
        dispatch(event.getSource().getEntity(), event, (e, self) -> e.onKill(self, dead, event));
    }

    @SubscribeEvent
    static void onFall(LivingFallEvent event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onFall(self, event));
    }

    @SubscribeEvent
    static void onKnockback(LivingKnockBackEvent event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onKnockback(self, event));
    }

    @SubscribeEvent
    static void onHeal(LivingHealEvent event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onHeal(self, event));
    }

    @SubscribeEvent
    static void onChangeTarget(LivingChangeTargetEvent event) {
        dispatch(event.getNewAboutToBeSetTarget(), event, (e, self) -> e.onTargeted(self, event.getEntity(), event));
    }

    @SubscribeEvent
    static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit) {
            dispatch(hit.getEntity(), event, (e, self) -> e.onStruckByProjectile(self, event.getProjectile(), event));
        }
    }

    @SubscribeEvent
    static void onMobEffect(MobEffectEvent.Applicable event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onMobEffect(self, event));
    }

    // ---- doing things ----

    @SubscribeEvent
    static void onAttack(AttackEntityEvent event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onAttack(self, event.getTarget(), event));
    }

    @SubscribeEvent
    static void onCrit(CriticalHitEvent event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onCrit(self, event));
    }

    @SubscribeEvent
    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof Projectile projectile
                && !(projectile instanceof FishingHook)) {
            dispatch(projectile.getOwner(), null, (e, self) -> e.onProjectileLaunched(self, projectile));
        }
    }

    @SubscribeEvent
    static void onBlockBreak(BlockEvent.BreakEvent event) {
        dispatch(event.getPlayer(), event, (e, self) -> e.onBlockBreak(self, event));
    }

    @SubscribeEvent
    static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onBlockPlace(self, event));
    }

    @SubscribeEvent
    static void onBlockDrops(BlockDropsEvent event) {
        dispatch(event.getBreaker(), event, (e, self) -> e.onBlockDrops(self, event));
    }

    @SubscribeEvent
    static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onLeftClickBlock(self, event));
    }

    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onRightClickBlock(self, event));
    }

    @SubscribeEvent
    static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onRightClickItem(self, event));
    }

    @SubscribeEvent
    static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!dispatch(event.getEntity(), event, (e, self) -> e.onInteract(self, event.getTarget(), event))) {
            dispatch(event.getTarget(), event, (e, self) -> e.onInteractedWith(self, event.getEntity(), event));
        }
    }

    @SubscribeEvent
    static void onUseStart(LivingEntityUseItemEvent.Start event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onUseStart(self, event));
    }

    @SubscribeEvent
    static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onUseFinish(self, event));
    }

    @SubscribeEvent
    static void onToss(ItemTossEvent event) {
        dispatch(event.getPlayer(), event, (e, self) -> e.onToss(self, event));
    }

    @SubscribeEvent
    static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onEquipmentChange(self, event));
    }

    @SubscribeEvent
    static void onPickup(ItemEntityPickupEvent.Post event) {
        dispatch(event.getPlayer(), event, (e, self) -> e.onPickup(self, event));
    }

    @SubscribeEvent
    static void onXpPickup(PlayerXpEvent.PickupXp event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onXpPickup(self, event));
    }

    @SubscribeEvent
    static void onLevelChange(PlayerXpEvent.LevelChange event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onLevelChange(self, event));
    }

    @SubscribeEvent
    static void onEnchant(PlayerEnchantItemEvent event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onEnchant(self, event));
    }

    @SubscribeEvent
    static void onAnvil(AnvilUpdateEvent event) {
        dispatch(event.getPlayer(), event, (e, self) -> e.onAnvil(self, event));
    }

    @SubscribeEvent
    static void onContainerOpen(PlayerContainerEvent.Open event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onContainerOpen(self, event));
    }

    @SubscribeEvent
    static void onTrade(TradeWithVillagerEvent event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onTrade(self, event));
    }

    @SubscribeEvent
    static void onFished(ItemFishedEvent event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onFished(self, event));
    }

    @SubscribeEvent
    static void onTame(AnimalTameEvent event) {
        dispatch(event.getTamer(), event, (e, self) -> e.onTame(self, event));
    }

    @SubscribeEvent
    static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onCraft(self, event));
    }

    @SubscribeEvent
    static void onMount(EntityMountEvent event) {
        dispatch(event.getEntityMounting(), event, (e, self) -> e.onMount(self, event));
    }

    @SubscribeEvent
    static void onSleep(CanPlayerSleepEvent event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onSleep(self, event));
    }

    @SubscribeEvent
    static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        dispatch(event.getEntity(), event, (e, self) -> e.onChangeDimension(self, event));
    }

    @SubscribeEvent
    static void onChat(ServerChatEvent event) {
        dispatch(event.getPlayer(), event, (e, self) -> e.onChat(self, event));
    }
}
