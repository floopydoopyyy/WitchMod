package com.oliver.witchmod.data;

import java.util.Optional;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
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

/**
 * base class for a single curse or blessing — one fixed behaviour, hooked in via the lifecycle methods below.
 * registered through {@link WitchModRegistries#EFFECTS}. subclasses override only the hooks they need.
 *
 * <p>the {@code on…} event hooks further down are called by {@code effects.EffectHooks} only while this effect is
 * active on {@code self}, server side. per-player state kept between calls belongs in {@link EffectState}.
 */
public abstract class Effect {
    private final EffectCategory category;
    private final EffectCostTier tier;
    private final int baseCost;
    private final Supplier<? extends Item> sacrificialItem;

    protected Effect(EffectCategory category, EffectCostTier tier, int baseCost, Supplier<? extends Item> sacrificialItem) {
        this.category = category;
        this.tier = tier;
        this.baseCost = baseCost;
        this.sacrificialItem = sacrificialItem;
    }

    public EffectCategory category() {
        return category;
    }

    public EffectCostTier tier() {
        return tier;
    }

    /** essence cost the success/backfire formulas key off. */
    public int baseCost() {
        return baseCost;
    }

    /** the item that selects this effect in the table's sacrificial slot. */
    public Item sacrificialItem() {
        return sacrificialItem.get();
    }

    /**
     * whether this effect can be selected directly (sacrificial item, coin, compendium page). internal
     * side-effect-only states (e.g. infectious) return false so they never show as castable or in the compendium.
     */
    public boolean selectable() {
        return true;
    }

    /**
     * a secret attachment: hidden from the compendium, random rolls and casting until the caster has discovered
     * every other effect of its category (see {@link SpecialAttachments}). ignored when specials are un-gated.
     */
    public boolean special() {
        return false;
    }

    /** whether pandora's box / cornucopia may roll this into their rotation. */
    public boolean inRotations() {
        return true;
    }

    /** pips the compendium draws for the 0..100 power scale. */
    public static final int POWER_PIPS = 5;

    /** power rating (0..100) read live from {@link PowerLevels} by registry id, so it's editable without code. */
    public int powerLevel() {
        return PowerLevels.get(this);
    }

    /**
     * tag whose members all select this effect — the exact-item exception (hype man = discs, party time =
     * candles). checked only as a fallback; {@link #sacrificialItem()} still returns a member for the icon.
     */
    public Optional<TagKey<Item>> sacrificialTag() {
        return Optional.empty();
    }

    /**
     * called when the effect begins acting on {@code target} (any cast path). {@code caster} is null when
     * unattributed; size any layered vanilla MobEffect to {@code durationTicks} since removal is explicit.
     */
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {}

    /** called when the effect stops (expiry, cure, or removal). */
    public void onRemove(ServerPlayer target) {}

    /** called once per tick while active; periodic effects check {@code ticksRemaining} here. */
    public void onTick(ServerPlayer target, int ticksRemaining) {}

    // ---- event hooks: called only while this effect is active on self (dispatched by effects.EffectHooks) ----
    // once a hook cancels its event, effects later in the order don't hear about it (as with neoforge listeners).

    /** hook order: higher runs first. only for effects that must intercept an event before anything else reacts. */
    public int hookPriority() {
        return 0;
    }

    /** you're about to take damage: cancel it, scale it, or react to it. */
    public void onHurt(ServerPlayer self, LivingIncomingDamageEvent event) {}

    /** something you caused (you, or your projectile) is about to damage {@code victim}. */
    public void onHurtOther(ServerPlayer self, LivingEntity victim, LivingIncomingDamageEvent event) {}

    /** the damage you're about to take, after armour and absorption. */
    public void onDamagePre(ServerPlayer self, LivingDamageEvent.Pre event) {}

    /** the damage you're about to deal {@code victim}, after its armour and absorption. */
    public void onDamagePreOther(ServerPlayer self, LivingEntity victim, LivingDamageEvent.Pre event) {}

    /** you've just taken damage. */
    public void onDamaged(ServerPlayer self, LivingDamageEvent.Post event) {}

    /** something you caused has just damaged {@code victim}. */
    public void onDamagedOther(ServerPlayer self, LivingEntity victim, LivingDamageEvent.Post event) {}

    /** you're dying: return true to cancel the death. asked before any {@link #onDeath}. */
    public boolean cheatsDeath(ServerPlayer self, LivingDeathEvent event) {
        return false;
    }

    /** you've died, and nothing cheated it. */
    public void onDeath(ServerPlayer self, LivingDeathEvent event) {}

    /** something you caused has killed {@code victim}. */
    public void onKill(ServerPlayer self, LivingEntity victim, LivingDeathEvent event) {}

    public void onFall(ServerPlayer self, LivingFallEvent event) {}

    /** you're being knocked back. */
    public void onKnockback(ServerPlayer self, LivingKnockBackEvent event) {}

    public void onHeal(ServerPlayer self, LivingHealEvent event) {}

    /** {@code by} is about to target you: cancel to refuse it. */
    public void onTargeted(ServerPlayer self, LivingEntity by, LivingChangeTargetEvent event) {}

    /** a projectile is about to hit you. */
    public void onStruckByProjectile(ServerPlayer self, Projectile projectile, ProjectileImpactEvent event) {}

    /** a mob effect is about to be applied to you. */
    public void onMobEffect(ServerPlayer self, MobEffectEvent.Applicable event) {}

    /** you swing at {@code target}. */
    public void onAttack(ServerPlayer self, Entity target, AttackEntityEvent event) {}

    public void onCrit(ServerPlayer self, CriticalHitEvent event) {}

    /** a projectile you own has just entered the world (fishing hooks excluded). */
    public void onProjectileLaunched(ServerPlayer self, Projectile projectile) {}

    public void onBlockBreak(ServerPlayer self, BlockEvent.BreakEvent event) {}

    public void onBlockPlace(ServerPlayer self, BlockEvent.EntityPlaceEvent event) {}

    /** a block you broke is about to drop its items. */
    public void onBlockDrops(ServerPlayer self, BlockDropsEvent event) {}

    public void onLeftClickBlock(ServerPlayer self, PlayerInteractEvent.LeftClickBlock event) {}

    public void onRightClickBlock(ServerPlayer self, PlayerInteractEvent.RightClickBlock event) {}

    public void onRightClickItem(ServerPlayer self, PlayerInteractEvent.RightClickItem event) {}

    /** you right-click {@code target}. */
    public void onInteract(ServerPlayer self, Entity target, PlayerInteractEvent.EntityInteract event) {}

    /** {@code by} right-clicks you. */
    public void onInteractedWith(ServerPlayer self, Player by, PlayerInteractEvent.EntityInteract event) {}

    public void onUseStart(ServerPlayer self, LivingEntityUseItemEvent.Start event) {}

    /** you've finished using (eating, drinking…) an item. */
    public void onUseFinish(ServerPlayer self, LivingEntityUseItemEvent.Finish event) {}

    public void onToss(ServerPlayer self, ItemTossEvent event) {}

    public void onEquipmentChange(ServerPlayer self, LivingEquipmentChangeEvent event) {}

    /** you've picked an item up. */
    public void onPickup(ServerPlayer self, ItemEntityPickupEvent.Post event) {}

    public void onXpPickup(ServerPlayer self, PlayerXpEvent.PickupXp event) {}

    public void onLevelChange(ServerPlayer self, PlayerXpEvent.LevelChange event) {}

    public void onEnchant(ServerPlayer self, PlayerEnchantItemEvent event) {}

    public void onAnvil(ServerPlayer self, AnvilUpdateEvent event) {}

    public void onContainerOpen(ServerPlayer self, PlayerContainerEvent.Open event) {}

    public void onTrade(ServerPlayer self, TradeWithVillagerEvent event) {}

    public void onFished(ServerPlayer self, ItemFishedEvent event) {}

    public void onTame(ServerPlayer self, AnimalTameEvent event) {}

    public void onCraft(ServerPlayer self, PlayerEvent.ItemCraftedEvent event) {}

    /** you mount or dismount something. */
    public void onMount(ServerPlayer self, EntityMountEvent event) {}

    public void onSleep(ServerPlayer self, CanPlayerSleepEvent event) {}

    public void onChangeDimension(ServerPlayer self, PlayerEvent.PlayerChangedDimensionEvent event) {}

    /** you've sent a chat message. */
    public void onChat(ServerPlayer self, ServerChatEvent event) {}

    /** instance detail the scrying mirror reveals (e.g. allergic's diet), as a translatable component; empty by default. */
    public Optional<net.minecraft.network.chat.Component> scryingDetail(ServerPlayer target) {
        return Optional.empty();
    }

    /**
     * whether the victim discovers this only when it fires (rather than at cast). effects that return true
     * must call {@link #markDiscoveredByVictim} at the real moment. default false = discover-on-apply.
     */
    public boolean discoversOnTrigger() {
        return false;
    }

    /** multiplier on the rolled duration at cast (moonwalker halves it); applied centrally so every path honours it. */
    public float durationMultiplier() {
        return 1.0F;
    }

    /**
     * debug: force this effect's signature event (or a named sub-event via {@code arg}) for {@code /bewitch
     * debug force}. returns a feedback line, or null if the effect has no discrete forcible event. anything
     * with an observable moment should override this.
     *
     * @param arg optional free-text argument (a sub-event name, a stat value, etc.); may be null/blank.
     */
    @Nullable
    public String debugForce(ServerPlayer target, @Nullable String arg) {
        return null;
    }

    /** valid {@code arg} values for {@code debug force}, surfaced as tab-completions; empty by default. */
    public java.util.List<String> debugArgs() {
        return java.util.List.of();
    }

    /** mark this effect discovered for {@code target} (alerts them) — call at the real trigger moment. */
    public void markDiscoveredByVictim(ServerPlayer target) {
        ResourceLocation id = WitchModRegistries.EFFECT_REGISTRY.getKey(this);
        if (id != null) {
            DiscoveryManager.markEffectDiscovered(target, id);
        }
    }
}
