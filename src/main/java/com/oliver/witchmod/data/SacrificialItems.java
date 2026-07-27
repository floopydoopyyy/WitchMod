package com.oliver.witchmod.data;

import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;

/** Reverse lookup from a Sacrificial Item (CLAUDE.md section 6.1/6.2) back to the curse/blessing it selects. */
public final class SacrificialItems {
    private SacrificialItems() {}

    public static Optional<Holder.Reference<Effect>> findEffect(Item item) {
        Optional<Holder.Reference<Effect>> exact = WitchModRegistries.EFFECT_REGISTRY.holders()
                .filter(holder -> holder.value().sacrificialItem() == item)
                .findFirst();
        if (exact.isPresent()) {
            return exact;
        }
        // Rule 9 tag exceptions (music discs -> Hype Man, candles -> Party Time), checked only as a fallback.
        return WitchModRegistries.EFFECT_REGISTRY.holders()
                .filter(holder -> holder.value().sacrificialTag()
                        .map(tag -> item.builtInRegistryHolder().is(tag)).orElse(false))
                .findFirst();
    }
}
