package com.oliver.witchmod.data;

import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * tracks which effects each player has discovered — caster on a successful cast (centralised in
 * {@link EffectManager#apply}), victim on trigger. by default the victim discovers at cast too, but effects
 * with a real "it happened to you" moment override {@link Effect#discoversOnTrigger()} and mark it then. a
 * new discovery alerts the player in chat and flips their compendium page.
 */
public final class DiscoveryManager {
    private DiscoveryManager() {}

    /** @return true if this was a new discovery (and so alerted the player). */
    public static boolean markEffectDiscovered(ServerPlayer player, ResourceLocation effectId) {
        // ink sac / wither rose: discovery reveals the true wrapper for a hidden/disguised effect
        EffectManager.revealDisplay(player, effectId);
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

    /** silently add/remove one effect discovery (for the {@code /bewitch discovery} command). */
    public static void setEffectDiscovered(ServerPlayer player, ResourceLocation effectId, boolean discovered) {
        Set<ResourceLocation> set = player.getData(WitchModAttachments.DISCOVERED_EFFECTS);
        boolean changed = discovered ? set.add(effectId) : set.remove(effectId);
        if (changed) {
            player.setData(WitchModAttachments.DISCOVERED_EFFECTS, set);
        }
    }

    /** silently add/remove one modifier discovery. */
    public static void setModifierDiscovered(ServerPlayer player, Modifier modifier, boolean discovered) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("witchmod", modifier.id());
        Set<ResourceLocation> set = player.getData(WitchModAttachments.DISCOVERED_MODIFIERS);
        boolean changed = discovered ? set.add(id) : set.remove(id);
        if (changed) {
            player.setData(WitchModAttachments.DISCOVERED_MODIFIERS, set);
        }
    }

    /** marks a table modifier discovered (call on a cast using it); alerts on first. keyed {@code witchmod:<id>} in its own set. */
    public static boolean markModifierDiscovered(ServerPlayer player, Modifier modifier) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("witchmod", modifier.id());
        Set<ResourceLocation> discovered = player.getData(WitchModAttachments.DISCOVERED_MODIFIERS);
        if (!discovered.add(id)) {
            return false;
        }
        player.setData(WitchModAttachments.DISCOVERED_MODIFIERS, discovered);
        Component name = new net.minecraft.world.item.ItemStack(ModifierItems.itemFor(modifier)).getHoverName();
        player.displayClientMessage(Component.literal("Modifier discovered: ").withStyle(ChatFormatting.DARK_AQUA)
                .append(name.copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" — your Compendium has been updated.").withStyle(ChatFormatting.GRAY)), false);
        return true;
    }

    private static void alert(ServerPlayer player, ResourceLocation id) {
        player.displayClientMessage(Component.literal("Discovered: ").withStyle(ChatFormatting.DARK_PURPLE)
                .append(Component.literal(titleCase(id.getPath())).withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(" — your Compendium has been updated.").withStyle(ChatFormatting.GRAY)), false);
    }

    public static String titleCase(String snakeCase) {
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
