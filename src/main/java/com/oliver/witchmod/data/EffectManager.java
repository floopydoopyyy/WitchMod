package com.oliver.witchmod.data;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import com.oliver.witchmod.Config;

/** Applies, removes, and ticks down curses/blessings on players. The single entry point into {@link ActiveEffects}. */
public final class EffectManager {
    /** Called when an active Ward BLOCKS an attachment cast by someone else — for the item's FX + durability. */
    public interface WardBlock {
        void block(ServerPlayer target, ServerPlayer caster, boolean curse);
    }

    private static Predicate<ServerPlayer> wardCheck = player -> false;
    private static WardBlock onWardBlock = (target, caster, curse) -> {};

    private EffectManager() {}

    /**
     * Lets the Ward item (items package) hook into effect application without EffectManager depending on it
     * directly — {@code check} reports whether a player has an active Ward, {@code onBlock} is called (target,
     * caster, isCurse) once a block actually happens so the item can consume durability and show its
     * directional lash + block FX.
     */
    public static void setWardHook(Predicate<ServerPlayer> check, WardBlock onBlock) {
        wardCheck = check;
        onWardBlock = onBlock;
    }

    /**
     * The single, global magic shield: anyone carrying the {@link WitchModMobEffects#PROTECTED} MobEffect is
     * protected. Warding Totems and Holy Water simply APPLY that effect; giving it via {@code /effect} works
     * too. It blocks all magic from OTHERS (self-casts pass) — curses/blessings, jar lashes and voodoo — but
     * NOT a direct thrown-jar splash ({@link ApplyOptions#directHit}).
     */
    public static boolean isMagicProtected(net.minecraft.world.entity.LivingEntity entity) {
        return entity.hasEffect(WitchModMobEffects.PROTECTED);
    }

    /** Whether a cast from {@code caster} would be blocked by {@code target}'s protection — for Ledger logging. */
    public static boolean wouldBlock(ServerPlayer target, @Nullable ServerPlayer caster) {
        return caster != target && isMagicProtected(target);
    }

    /** The "your magic fizzles on their shield" feedback shown at a protected target when a cast is blocked. */
    private static void protectedFizzle(ServerPlayer target, boolean curse) {
        if (!(target.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        double x = target.getX(), y = target.getY() + 1.0, z = target.getZ();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, x, y, z, 10, 0.35, 0.5, 0.35, 0.02);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH, x, y, z, 6, 0.3, 0.4, 0.3, 0.0);
        level.playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.5F);
        level.playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.4F, 1.4F);
    }

    /**
     * Applies {@code effect} to {@code target} for {@code durationTicks}. NO-STACKING with KEEP_LONGER
     * (master-spec Rule 1): if the effect is already active, the instance keeps whichever remaining
     * duration is longer — never additive, never refreshed to a shorter time. Because this is the single
     * entry point every application path goes through (table, commands, coins, jars, Effigy, Bell, Gamble,
     * Mirror backfire), that rule is inherited everywhere for free.
     */
    /** @return true if the effect actually landed on someone (a Ward deflect counts — it landed on the caster). */
    /**
     * Per-application modifier options: Netherite Ingot bypasses the Ward; Ink Sac hides the wrapper/tell until
     * discovery ({@link ActiveEffectInstance#DISPLAY_HIDDEN}); Wither Rose shows the OPPOSITE category until
     * discovery ({@link ActiveEffectInstance#DISPLAY_DISGUISED}).
     */
    public record ApplyOptions(boolean bypassWard, int display, boolean directHit) {
        public static final ApplyOptions DEFAULT = new ApplyOptions(false, ActiveEffectInstance.DISPLAY_NORMAL, false);

        /** Existing 2-arg callers (modifier casts) — not a direct hit, so protection still blocks them. */
        public ApplyOptions(boolean bypassWard, int display) {
            this(bypassWard, display, false);
        }

        /** A direct thrown-jar splash — bypasses the Protected shield (a well-aimed bottle still catches you). */
        public static final ApplyOptions DIRECT_HIT = new ApplyOptions(false, ActiveEffectInstance.DISPLAY_NORMAL, true);
    }

    public static boolean apply(ServerPlayer target, Holder<Effect> effect, int durationTicks, @Nullable ServerPlayer caster) {
        return apply(target, effect, durationTicks, caster, ApplyOptions.DEFAULT);
    }

    public static boolean apply(ServerPlayer target, Holder<Effect> effect, int durationTicks, @Nullable ServerPlayer caster, ApplyOptions opts) {
        boolean categoryEnabled = effect.value().category() == EffectCategory.CURSE
                ? Config.CURSES_ENABLED.get()
                : Config.BLESSINGS_ENABLED.get();
        if (!categoryEnabled) {
            if (caster != null) {
                caster.displayClientMessage(Component.literal(
                        (effect.value().category() == EffectCategory.CURSE ? "Curses" : "Blessings") + " are disabled on this server."), true);
            }
            return false;
        }
        if (caster != null && caster != target && GracePeriod.isInGracePeriod(target)) {
            caster.displayClientMessage(Component.literal(target.getName().getString() + " is still under a new-player grace period."), true);
            return false;
        }
        // The PROTECTED effect (from a Warding Totem, Holy Water, or /effect) shields against OTHERS' magic —
        // curses/blessings, jar lashes, coins, the dummy command and voodoo all fizzle. Self-casts pass, and a
        // DIRECT thrown-jar splash punches through (directHit).
        if (caster != target && !opts.directHit() && isMagicProtected(target)) {
            protectedFizzle(target, effect.value().category() == EffectCategory.CURSE);
            return false;
        }
        // A Ward BLOCKS any attachment cast by someone ELSE (self-casts pass through). It doesn't redirect —
        // it just stops it, with a coloured incoming-lash + a block, spending 1 durability.
        if (!opts.bypassWard() && caster != null && caster != target && wardCheck.test(target)) {
            onWardBlock.block(target, caster, effect.value().category() == EffectCategory.CURSE);
            return false;
        }

        // Per-effect duration override (e.g. Moonwalker halves its own). Applied here so every cast path
        // honours it. Clamped to at least 1 tick so a zero multiplier can't make an effect never land.
        durationTicks = Math.max(1, Math.round(durationTicks * effect.value().durationMultiplier()));

        ResourceLocation id = idOf(effect);
        ActiveEffects active = target.getData(WitchModAttachments.ACTIVE_EFFECTS);

        int existingRemaining = active.get(id).map(ActiveEffectInstance::remainingTicks).orElse(0);
        int effectiveDuration = Math.max(existingRemaining, durationTicks);
        // Caster attribution follows the winning duration: a longer (or equal) reapply takes ownership of
        // the instance; if the existing one was longer, its original caster stands.
        Optional<UUID> casterId = durationTicks >= existingRemaining
                ? Optional.ofNullable(caster).map(ServerPlayer::getUUID)
                : active.get(id).flatMap(ActiveEffectInstance::caster);
        active.put(id, new ActiveEffectInstance(effectiveDuration, casterId, opts.display()));
        target.setData(WitchModAttachments.ACTIVE_EFFECTS, active);
        // Size the effect's own vanilla sub-effects to the kept (never-shortened) duration.
        effect.value().onApply(target, caster, effectiveDuration);
        StatusEffectSync.sync(target);

        // Discovery (Rule 2): the caster discovers the instant they successfully send it. The victim
        // normally discovers here too, EXCEPT for effects that define a real trigger moment
        // (discoversOnTrigger) — those call markDiscoveredByVictim themselves when they actually fire,
        // e.g. Allergic's first bad reaction or Backseat Driver's first AI takeover.
        boolean discoversOnTrigger = effect.value().discoversOnTrigger();
        // Ink Sac (hidden) / Wither Rose (disguised) suppress the victim's IMMEDIATE discovery so the wrapper
        // stays hidden/faked until the effect's real discovery moment reveals it (see revealDisplay).
        if (!discoversOnTrigger && opts.display() == ActiveEffectInstance.DISPLAY_NORMAL) {
            DiscoveryManager.markEffectDiscovered(target, id);
        }
        // A self-cast makes you both roles at once. For a trigger-discovered effect the VICTIM half has to
        // win, or casting one on yourself would spoil the very moment you're meant to find out from.
        if (caster != null && !(caster == target && discoversOnTrigger)) {
            DiscoveryManager.markEffectDiscovered(caster, id);
        }
        return true;
    }

    /** @return true if {@code effect} was active on {@code target} and has now been removed. */
    public static boolean remove(ServerPlayer target, Holder<Effect> effect) {
        ResourceLocation id = idOf(effect);
        ActiveEffects active = target.getData(WitchModAttachments.ACTIVE_EFFECTS);
        if (active.remove(id) == null) {
            return false;
        }
        target.setData(WitchModAttachments.ACTIVE_EFFECTS, active);
        effect.value().onRemove(target);
        StatusEffectSync.sync(target);
        return true;
    }

    /**
     * Removes every active curse/blessing on {@code target}, or only those of {@code category} when given
     * (null = both). Each removed effect's {@link Effect#onRemove} fires so its vanilla sub-effects/state
     * are cleaned up. @return how many were removed.
     */
    public static int removeAll(ServerPlayer target, @Nullable EffectCategory category) {
        ActiveEffects active = target.getExistingDataOrNull(WitchModAttachments.ACTIVE_EFFECTS);
        if (active == null || active.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (ResourceLocation id : active.activeIds()) {
            Optional<Effect> effect = WitchModRegistries.EFFECT_REGISTRY.getOptional(id);
            if (category != null && !effect.map(e -> e.category() == category).orElse(false)) {
                continue; // category filter: only remove known effects of that category
            }
            if (active.remove(id) != null) {
                effect.ifPresent(e -> e.onRemove(target));
                removed++;
            }
        }
        if (removed > 0) {
            target.setData(WitchModAttachments.ACTIVE_EFFECTS, active);
            StatusEffectSync.sync(target);
        }
        return removed;
    }

    /** Purifying Water's "rapidly burns down active curse/blessing timers" (CLAUDE.md section 2.5). */
    public static void reduceAllDurations(ServerPlayer player, int ticksToRemove) {
        ActiveEffects active = player.getExistingDataOrNull(WitchModAttachments.ACTIVE_EFFECTS);
        if (active == null || active.isEmpty()) {
            return;
        }
        List<ResourceLocation> expired = active.reduceAll(ticksToRemove);
        player.setData(WitchModAttachments.ACTIVE_EFFECTS, active);
        for (ResourceLocation id : expired) {
            WitchModRegistries.EFFECT_REGISTRY.getOptional(id).ifPresent(effect -> effect.onRemove(player));
        }
        // Re-sync EVERY call (not only on expiry) so the visible Cursed/Blessed wrapper's timer shrinks in step
        // with the internal drain — otherwise the on-screen effect counts down at 1x while the real timer races.
        // updateWrapper only bursts particles on the onset edge, so a per-tick re-sync doesn't spam anything.
        StatusEffectSync.sync(player);
    }

    public static boolean isActive(ServerPlayer target, Holder<Effect> effect) {
        return target.getExistingData(WitchModAttachments.ACTIVE_EFFECTS)
                .map(active -> active.isActive(idOf(effect)))
                .orElse(false);
    }

    /** Total curses+blessings currently active on {@code target} — CLAUDE.md section 2.7's per-player limit. */
    public static int activeCount(ServerPlayer target) {
        return target.getExistingData(WitchModAttachments.ACTIVE_EFFECTS)
                .map(ActiveEffects::size)
                .orElse(0);
    }

    /** Whether {@code target} already carries any active effect of {@code category} (Amethyst Shard modifier). */
    public static boolean hasActiveOfCategory(ServerPlayer target, EffectCategory category) {
        ActiveEffects active = target.getExistingDataOrNull(WitchModAttachments.ACTIVE_EFFECTS);
        if (active == null) {
            return false;
        }
        for (ResourceLocation id : active.activeIds()) {
            if (WitchModRegistries.EFFECT_REGISTRY.getOptional(id).map(e -> e.category() == category).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    /** A snapshot of {@code target}'s active effects → their remaining ticks (for the Infectious spread). */
    public static java.util.Map<ResourceLocation, Integer> activeSnapshot(ServerPlayer target) {
        ActiveEffects active = target.getData(WitchModAttachments.ACTIVE_EFFECTS);
        java.util.Map<ResourceLocation, Integer> out = new java.util.LinkedHashMap<>();
        for (ResourceLocation id : active.activeIds()) {
            active.get(id).ifPresent(inst -> out.put(id, inst.remainingTicks()));
        }
        return out;
    }

    /**
     * Reveals a hidden (Ink Sac) or disguised (Wither Rose) effect on {@code player} — flips its display back to
     * normal and re-syncs, so the true Cursed/Blessed wrapper appears with its onset sting. Called from
     * {@link DiscoveryManager#markEffectDiscovered} the first time the effect is discovered. No-op otherwise.
     */
    public static void revealDisplay(ServerPlayer player, ResourceLocation effectId) {
        ActiveEffects active = player.getData(WitchModAttachments.ACTIVE_EFFECTS);
        active.get(effectId).ifPresent(inst -> {
            if (inst.display() != ActiveEffectInstance.DISPLAY_NORMAL) {
                active.put(effectId, inst.withDisplay(ActiveEffectInstance.DISPLAY_NORMAL));
                player.setData(WitchModAttachments.ACTIVE_EFFECTS, active);
                StatusEffectSync.sync(player);
            }
        });
    }

    /** Resolves the registry holder for an effect id (used when moving effects between players). */
    public static Optional<Holder.Reference<Effect>> holderOf(ResourceLocation id) {
        return WitchModRegistries.EFFECT_REGISTRY.holders().filter(h -> h.key().location().equals(id)).findFirst();
    }

    /**
     * Places {@code effect} at an EXACT remaining duration — no {@link Effect#durationMultiplier}, bypassing the
     * cast-time guards (category-enabled / grace / totem / ward). Used by the Infectious / Very Infectious spread,
     * which is a physical hit rather than a Table cast, so the timer is preserved verbatim on transfer.
     */
    public static void applyExact(ServerPlayer target, Holder<Effect> effect, int remainingTicks, @Nullable ServerPlayer caster) {
        ResourceLocation id = idOf(effect);
        ActiveEffects active = target.getData(WitchModAttachments.ACTIVE_EFFECTS);
        int existing = active.get(id).map(ActiveEffectInstance::remainingTicks).orElse(0);
        int dur = Math.max(existing, Math.max(1, remainingTicks));
        active.put(id, new ActiveEffectInstance(dur, Optional.ofNullable(caster).map(ServerPlayer::getUUID)));
        target.setData(WitchModAttachments.ACTIVE_EFFECTS, active);
        effect.value().onApply(target, caster, dur);
        StatusEffectSync.sync(target);
        if (!effect.value().discoversOnTrigger()) {
            DiscoveryManager.markEffectDiscovered(target, id);
        }
    }

    /** Called once per player per tick by {@link WitchModEventHandler}. */
    public static void tick(ServerPlayer player) {
        ActiveEffects active = player.getExistingDataOrNull(WitchModAttachments.ACTIVE_EFFECTS);
        if (active == null || active.isEmpty()) {
            return;
        }

        for (ResourceLocation id : active.activeIds()) {
            active.get(id).ifPresent(instance -> WitchModRegistries.EFFECT_REGISTRY.getOptional(id)
                    .ifPresent(effect -> effect.onTick(player, instance.remainingTicks())));
        }

        List<ResourceLocation> expired = active.tickDown();
        if (!expired.isEmpty()) {
            player.setData(WitchModAttachments.ACTIVE_EFFECTS, active);
            for (ResourceLocation id : expired) {
                WitchModRegistries.EFFECT_REGISTRY.getOptional(id).ifPresent(effect -> effect.onRemove(player));
            }
            StatusEffectSync.sync(player);
        }
    }

    private static ResourceLocation idOf(Holder<Effect> effect) {
        return effect.unwrapKey().orElseThrow().location();
    }
}
