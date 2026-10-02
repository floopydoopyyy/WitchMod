package com.oliver.witchmod.effects.blessings;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.DiscoveryManager;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.RouletteState;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.EffectRoulette;

/**
 * secret: cornucopia. holds several random blessings at once (past the usual limit) and swaps the oldest for a new
 * one on a timer — more of them, but no say in which. the rotation itself lives in {@link EffectRoulette}.
 */
public final class BlessingCornucopia extends Effect {
    private static final EffectCategory CATEGORY = EffectCategory.BLESSING;

    public BlessingCornucopia() {
        super(CATEGORY, EffectCostTier.MAJOR, 80, () -> Items.ENDER_CHEST);
    }

    @Override
    public boolean special() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        if (EffectRoulette.holding(target, CATEGORY).isEmpty()) {
            EffectRoulette.start(target, CATEGORY, durationTicks);
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectRoulette.stop(target, CATEGORY);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        if (ticksRemaining % 10 == 0) {
            EffectRoulette.tick(target, CATEGORY, ticksRemaining);
        }
    }

    @Override
    public Optional<Component> scryingDetail(ServerPlayer target) {
        return Optional.of(Component.translatable("witchmod.scry.roulette", names(target)));
    }

    @Override
    public String debugForce(ServerPlayer target, @Nullable String arg) {
        if ("swap".equalsIgnoreCase(arg)) {
            RouletteState state = target.getData(CATEGORY == EffectCategory.CURSE
                    ? WitchModAttachments.ROULETTE_CURSES : WitchModAttachments.ROULETTE_BLESSINGS);
            target.setData(CATEGORY == EffectCategory.CURSE ? WitchModAttachments.ROULETTE_CURSES
                    : WitchModAttachments.ROULETTE_BLESSINGS, new RouletteState(state.owned(), 0L));
            int remaining = com.oliver.witchmod.data.EffectManager.activeSnapshot(target)
                    .getOrDefault(com.oliver.witchmod.data.WitchModRegistries.EFFECT_REGISTRY.getKey(this), 20 * 60);
            EffectRoulette.tick(target, CATEGORY, remaining);
            return "swapped the oldest — now holding: " + names(target);
        }
        return "holding: " + names(target) + " (arg 'swap' forces a swap now)";
    }

    @Override
    public List<String> debugArgs() {
        return List.of("swap");
    }

    private static String names(ServerPlayer target) {
        List<String> ids = EffectRoulette.holding(target, CATEGORY).stream()
                .map(id -> DiscoveryManager.titleCase(id.getPath())).toList();
        return ids.isEmpty() ? "nothing" : ids.stream().collect(Collectors.joining(", "));
    }
}
