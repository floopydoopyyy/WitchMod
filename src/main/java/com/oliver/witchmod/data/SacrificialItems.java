package com.oliver.witchmod.data;

import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;

/** reverse lookup from a sacrificial item back to the curse/blessing it selects. */
public final class SacrificialItems {
    private SacrificialItems() {}

    public static Optional<Holder.Reference<Effect>> findEffect(Item item) {
        Optional<Holder.Reference<Effect>> exact = WitchModRegistries.EFFECT_REGISTRY.holders()
                .filter(holder -> holder.value().selectable())
                .filter(holder -> holder.value().sacrificialItem() == item)
                .findFirst();
        if (exact.isPresent()) {
            return exact;
        }
        // tag exceptions (music discs -> hype man, candles -> party time), only as a fallback
        return WitchModRegistries.EFFECT_REGISTRY.holders()
                .filter(holder -> holder.value().selectable())
                .filter(holder -> holder.value().sacrificialTag()
                        .map(tag -> item.builtInRegistryHolder().is(tag)).orElse(false))
                .findFirst();
    }
}
