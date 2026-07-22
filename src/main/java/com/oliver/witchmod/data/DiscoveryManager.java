package com.oliver.witchmod.data;

import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tracks which curses/blessings and neutrals/globals each player has discovered (master-spec Rule 2):
 * "the victim discovers on TRIGGER; the caster discovers the instant they successfully send it."
 *
 * <p>The caster half is centralised in {@link EffectManager#apply}. The victim half defaults to that same
 * place (so every unrefined attachment still gets discovered), but any effect with a meaningful "it
 * actually happened to you" moment overrides {@link Effect#discoversOnTrigger()} and calls
 * {@link Effect#markDiscoveredByVictim} at that moment instead — e.g. Allergic on the first bad reaction,
 * Backseat Driver the first time the AI takes the wheel. Events (neutrals/globals) are covered at their
 * trigger call sites ({@code BewitchCommand}, {@code BewitchingTableRitual}).
 *
 * <p>A NEW discovery alerts the player in chat (Rule 2); their Compendium shows it as discovered from then on.
 */
public final class DiscoveryManager {
    private DiscoveryManager() {}

    /** @return true if this was a NEW discovery (and therefore alerted the player). */
    public static boolean markEffectDiscovered(ServerPlayer player, ResourceLocation effectId) {
        Set<ResourceLocation> discovered = player.getData(WitchModAttachments.DISCOVERED_EFFECTS);
        if (!discovered.add(effectId)) {
            return false;
        }
        player.setData(WitchModAttachments.DISCOVERED_EFFECTS, discovered);
        alert(player, effectId);
        return true;
    }

    public static boolean hasDiscoveredEffect(ServerPlayer player, ResourceLocation effectId) {
        return player.getExistingData(WitchModAttachments.DISCOVERED_EFFECTS)
                .map(set -> set.contains(effectId))
                .orElse(false);
    }

    /** @return true if this was a NEW discovery (and therefore alerted the player). */
    public static boolean markEventDiscovered(ServerPlayer player, ResourceLocation eventId) {
        Set<ResourceLocation> discovered = player.getData(WitchModAttachments.DISCOVERED_EVENTS);
        if (!discovered.add(eventId)) {
            return false;
        }
        player.setData(WitchModAttachments.DISCOVERED_EVENTS, discovered);
        alert(player, eventId);
        return true;
    }

    public static boolean hasDiscoveredEvent(ServerPlayer player, ResourceLocation eventId) {
        return player.getExistingData(WitchModAttachments.DISCOVERED_EVENTS)
                .map(set -> set.contains(eventId))
                .orElse(false);
    }

    private static void alert(ServerPlayer player, ResourceLocation id) {
        player.displayClientMessage(Component.literal("Discovered: ").withStyle(ChatFormatting.DARK_PURPLE)
                .append(Component.literal(titleCase(id.getPath())).withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(" — your Compendium has been updated.").withStyle(ChatFormatting.GRAY)), false);
    }

    private static String titleCase(String snakeCase) {
        String[] parts = snakeCase.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }
}
