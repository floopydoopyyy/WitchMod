package com.oliver.witchmod.data;

import java.util.Optional;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * base class for a single curse or blessing — one fixed behaviour, hooked in via the lifecycle methods below.
 * registered through {@link WitchModRegistries#EFFECTS}. subclasses override only the hooks they need.
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

    /** instance detail the scrying mirror reveals (e.g. allergic's diet); empty by default (name + timer only). */
    public Optional<String> scryingDetail(ServerPlayer target) {
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
