package com.oliver.witchmod.data;

import java.util.Optional;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * A single curse or blessing. There are no strength tiers (CLAUDE.md section 1) — every effect is one
 * fixed behavior, hooked in here for Phase 1 to implement per curse/blessing. Registered through
 * {@link WitchModRegistries#EFFECTS}.
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

    /** Essence cost consumed by the success/backfire formulas in CLAUDE.md section 5.7. */
    public int baseCost() {
        return baseCost;
    }

    /** The item that must be placed in the Bewitching Table's Sacrificial Item slot to select this effect. */
    public Item sacrificialItem() {
        return sacrificialItem.get();
    }

    /**
     * A tag whose members ALL select this effect at the Table, as the explicit exception to exact-item
     * matching (master-spec Rule 9): only Hype Man (any music disc) and Party Time (any candle) use it. Empty
     * by default. {@link SacrificialItems#findEffect} checks it only when no exact-item match is found, and
     * {@link #sacrificialItem()} still returns a representative member for the icon/preview.
     */
    public Optional<TagKey<Item>> sacrificialTag() {
        return Optional.empty();
    }

    /**
     * Called when this effect begins acting on {@code target}, whether by successful Table cast,
     * command, Voodoo Doll forward, Coin gamble, or backfire (Mirror).
     *
     * @param caster       null when the effect wasn't attributed to a specific player (e.g. command with
     *                     no selector, or a system-triggered backfire).
     * @param durationTicks the tracked duration this instance was applied for; effects that layer a
     *                     vanilla {@link net.minecraft.world.effect.MobEffectInstance} on top should size
     *                     it to match, since removal is explicit (see {@link #onRemove}) rather than relying
     *                     on the vanilla effect's own countdown.
     */
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {}

    /** Called when this effect stops acting on {@code target}, whether by expiry, cure, or removal. */
    public void onRemove(ServerPlayer target) {}

    /**
     * Called once per tick while this effect is active on {@code target}. Most effects that only need a
     * one-time state change (a status effect, an attribute modifier) can ignore this; effects with
     * periodic behavior (a chance each tick, a fixed interval) should check {@code ticksRemaining} here.
     */
    public void onTick(ServerPlayer target, int ticksRemaining) {}

    /**
     * Extra, instance-specific detail the Scrying Mirror should reveal about this effect on {@code target} —
     * e.g. Allergic's rolled diet, which is otherwise hidden from the victim until they eat the wrong thing.
     * Empty by default; the mirror lists those effects by name and timer only.
     */
    public Optional<String> scryingDetail(ServerPlayer target) {
        return Optional.empty();
    }

    /**
     * Whether the VICTIM only discovers this attachment when it actually fires, rather than the moment it
     * lands (master-spec Rule 2: "the victim discovers on trigger"). Effects that return true must call
     * {@link #markDiscoveredByVictim} at their real trigger moment — otherwise the victim never discovers
     * it. Defaults to false, which keeps the centralised discover-on-apply behaviour for everything that
     * hasn't been refined yet.
     */
    public boolean discoversOnTrigger() {
        return false;
    }

    /**
     * A multiplier applied to this effect's rolled duration when it's cast (master-spec "duration override").
     * Defaults to 1.0. Moonwalker halves it, because being unable to walk forward is punishing enough that
     * it shouldn't last a full 30–60 minutes. Applied centrally in {@code EffectManager.apply}, so it's
     * respected by every cast path (table, command, coin, effigy...).
     */
    public float durationMultiplier() {
        return 1.0F;
    }

    /**
     * DEBUG: force this effect's signature event (or, with {@code arg}, a named sub-event / parameter) on
     * {@code target}, for testing via {@code /bewitch debug force}. Returns a human-readable feedback line —
     * including when a precondition wasn't met but it fired anyway, or when the arg named no valid sub-event.
     * Returns {@code null} (the default) for effects that have no discrete, forcible event.
     *
     * <p><b>PROJECT RULE (see CLAUDE.md §16.4):</b> any curse/blessing/event with a discrete, observable
     * moment MUST override this so the moment is forcible for hands-on testing. Effects whose event fires from
     * an internal method should expose a small package-visible trigger the override can call; if a precondition
     * genuinely can't be satisfied, still do as much as possible and say so in the returned message.
     *
     * @param arg optional free-text argument (a sub-event name, a stat value, etc.); may be null/blank.
     */
    @Nullable
    public String debugForce(ServerPlayer target, @Nullable String arg) {
        return null;
    }

    /** Marks this effect discovered for {@code target}, alerting them — call at the real trigger moment. */
    public void markDiscoveredByVictim(ServerPlayer target) {
        ResourceLocation id = WitchModRegistries.EFFECT_REGISTRY.getKey(this);
        if (id != null) {
            DiscoveryManager.markEffectDiscovered(target, id);
        }
    }
}
