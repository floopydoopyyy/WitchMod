package com.oliver.witchmod.effects;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.ClientOptOut;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectManager;
import com.oliver.witchmod.data.RouletteState;
import com.oliver.witchmod.data.TaxBank;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.data.WitchModRegistries;
import com.oliver.witchmod.effects.curses.CurseAudit;

/**
 * the engine behind pandora's box (curses) and cornucopia (blessings): keeps N random effects of one category
 * on the player, swapping the oldest for a fresh one on a timer. the effects it holds are real active effects
 * (synergies, discovery, etc. all work) but are exempt from the per-category cap. a real cast of an effect it's
 * holding takes that effect over ({@link #release}), so the rotation never strips a genuine cast.
 */
public final class EffectRoulette {
    private EffectRoulette() {}

    private static Supplier<AttachmentType<RouletteState>> slot(EffectCategory category) {
        return category == EffectCategory.CURSE ? WitchModAttachments.ROULETTE_CURSES : WitchModAttachments.ROULETTE_BLESSINGS;
    }

    /** whether a rotation (either kind) is currently holding {@code id} on this player. */
    public static boolean owns(ServerPlayer player, ResourceLocation id) {
        RouletteState curses = player.getExistingDataOrNull(WitchModAttachments.ROULETTE_CURSES);
        RouletteState blessings = player.getExistingDataOrNull(WitchModAttachments.ROULETTE_BLESSINGS);
        return (curses != null && curses.owns(id)) || (blessings != null && blessings.owns(id));
    }

    /** hand {@code id} over to a real cast: the rotation forgets it (and refills that slot on its next tick). */
    public static void release(ServerPlayer player, ResourceLocation id) {
        for (EffectCategory category : EffectCategory.values()) {
            RouletteState state = player.getExistingDataOrNull(slot(category));
            if (state != null && state.owns(id)) {
                List<ResourceLocation> owned = new ArrayList<>(state.owned());
                owned.remove(id);
                player.setData(slot(category), new RouletteState(List.copyOf(owned), state.nextSwap()));
            }
        }
    }

    public static void start(ServerPlayer player, EffectCategory category, int durationTicks) {
        player.setData(slot(category), new RouletteState(List.of(), nextSwapFrom(player, category)));
        refill(player, category, new ArrayList<>(), durationTicks, null);
    }

    /** prune anything that left by other means, swap the oldest when due, and top back up to the slot count. */
    public static void tick(ServerPlayer player, EffectCategory category, int ticksRemaining) {
        RouletteState state = player.getData(slot(category));
        List<ResourceLocation> owned = new ArrayList<>(state.owned());
        owned.removeIf(id -> EffectManager.holderOf(id).map(h -> !EffectManager.isActive(player, h)).orElse(true));
        long nextSwap = state.nextSwap();
        long now = player.level().getGameTime();
        ResourceLocation swappedOut = null;
        if (now >= nextSwap) {
            if (!owned.isEmpty()) {
                ResourceLocation oldest = owned.remove(0);
                swappedOut = oldest;
                // save the shortened list first so the removal isn't treated as ours to refill mid-way
                player.setData(slot(category), new RouletteState(List.copyOf(owned), now));
                EffectManager.holderOf(oldest).ifPresent(h -> EffectManager.remove(player, h));
            }
            nextSwap = nextSwapFrom(player, category);
        }
        player.setData(slot(category), new RouletteState(List.copyOf(owned), nextSwap));
        refill(player, category, owned, ticksRemaining, swappedOut);
    }

    /** the rotation ends: strip everything it's still holding. */
    public static void stop(ServerPlayer player, EffectCategory category) {
        RouletteState state = player.getExistingDataOrNull(slot(category));
        player.setData(slot(category), RouletteState.EMPTY);
        if (state == null) {
            return;
        }
        for (ResourceLocation id : state.owned()) {
            EffectManager.holderOf(id).ifPresent(h -> EffectManager.remove(player, h));
        }
    }

    public static List<ResourceLocation> holding(ServerPlayer player, EffectCategory category) {
        return player.getData(slot(category)).owned();
    }

    private static void refill(ServerPlayer player, EffectCategory category, List<ResourceLocation> owned, int durationTicks,
                               ResourceLocation exclude) {
        int slots = category == EffectCategory.CURSE ? Config.PANDORA_SLOTS.get() : Config.CORNUCOPIA_SLOTS.get();
        long nextSwap = player.getData(slot(category)).nextSwap();
        while (owned.size() < slots) {
            Holder.Reference<Effect> pick = pick(player, category, exclude);
            if (pick == null) {
                break; // nothing left that isn't already on them
            }
            ResourceLocation id = pick.key().location();
            owned.add(id);
            // record ownership BEFORE it lands, so the cap check / counts already treat it as the rotation's.
            player.setData(slot(category), new RouletteState(List.copyOf(owned), nextSwap));
            EffectManager.applyExact(player, pick, Math.max(1, durationTicks), null);
        }
    }

    /** a random ordinary effect of the category that isn't on the player, disabled, or opted out of. */
    private static Holder.Reference<Effect> pick(ServerPlayer player, EffectCategory category, ResourceLocation exclude) {
        boolean taxFull = TaxBank.get(player.server).isFull();
        List<Holder.Reference<Effect>> pool = new ArrayList<>();
        for (Holder.Reference<Effect> h : WitchModRegistries.EFFECT_REGISTRY.holders().toList()) {
            Effect e = h.value();
            ResourceLocation id = h.key().location();
            if (!e.selectable() || e.special() || !e.inRotations() || e.category() != category || EffectManager.isActive(player, h)
                    || Config.isAttachmentDisabled(id) || ClientOptOut.blocks(player, id)
                    || id.equals(exclude) || (taxFull && e instanceof CurseAudit)) {
                continue;
            }
            pool.add(h);
        }
        return pool.isEmpty() ? null : pool.get(player.getRandom().nextInt(pool.size()));
    }

    private static long nextSwapFrom(ServerPlayer player, EffectCategory category) {
        int seconds = category == EffectCategory.CURSE ? Config.PANDORA_SWAP_SECONDS.get() : Config.CORNUCOPIA_SWAP_SECONDS.get();
        return player.level().getGameTime() + seconds * 20L;
    }
}
