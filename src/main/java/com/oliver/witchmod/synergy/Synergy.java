package com.oliver.witchmod.synergy;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.registries.DeferredHolder;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectManager;

/**
 * a relationship between two attachments: an extra behaviour that exists ONLY while ONE player carries BOTH,
 * and vanishes the instant either leaves. participating effects gate their bonus behaviour on
 * {@link #activeFor} each tick, so the on/off is always live — nothing is stored. see {@link Synergies}.
 */
public record Synergy(String id, DeferredHolder<Effect, ?> first, DeferredHolder<Effect, ?> second,
                      String description) {
    /** true only while {@code player} has both halves active right now. */
    public boolean activeFor(ServerPlayer player) {
        return EffectManager.isActive(player, (Holder<Effect>) first)
                && EffectManager.isActive(player, (Holder<Effect>) second);
    }
}
