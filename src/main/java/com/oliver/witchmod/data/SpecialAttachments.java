package com.oliver.witchmod.data;

import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.oliver.witchmod.Config;

/**
 * the secret attachments' gate. a special effect is unlocked for a player once they've discovered every other
 * (non-special) effect of its category. usable on both sides — the client compendium reads the synced
 * discovered set the same way. with specialAttachmentsGated off, everything is unlocked and rollable.
 */
public final class SpecialAttachments {
    private SpecialAttachments() {}

    public static boolean gated() {
        try {
            return Config.SPECIAL_ATTACHMENTS_GATED.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /** whether {@code player} may see / cast {@code effect}. always true for ordinary effects. */
    public static boolean unlockedFor(Player player, Effect effect) {
        if (!effect.special() || !gated() || !Config.discoveryEnabled()) {
            return true;
        }
        return allDiscovered(player, effect.category());
    }

    /** every selectable, non-special effect of {@code category} is in the player's discovered set. */
    public static boolean allDiscovered(Player player, EffectCategory category) {
        Set<ResourceLocation> discovered = player.getData(WitchModAttachments.DISCOVERED_EFFECTS);
        for (Effect e : WitchModRegistries.EFFECT_REGISTRY) {
            if (e.selectable() && !e.special() && e.category() == category) {
                ResourceLocation id = WitchModRegistries.EFFECT_REGISTRY.getKey(e);
                if (id != null && !discovered.contains(id)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** whether a random roll (redstone, coins, bell, backfire, starter discovery…) may pick {@code effect}. */
    public static boolean inRandomPools(Effect effect) {
        return effect.selectable() && (!effect.special() || !gated());
    }

    /**
     * records an unknowing attempt. @return true if this was the FIRST try (warn only), false if they'd already
     * been warned (so this one backfires).
     */
    public static boolean firstAttempt(ServerPlayer player, ResourceLocation id) {
        Set<ResourceLocation> attempts = player.getData(WitchModAttachments.SPECIAL_ATTEMPTS);
        if (!attempts.add(id)) {
            return false;
        }
        player.setData(WitchModAttachments.SPECIAL_ATTEMPTS, attempts);
        return true;
    }
}
