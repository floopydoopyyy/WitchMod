package com.oliver.witchmod.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import com.oliver.witchmod.Config;

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
        if (!Config.discoveryEnabled()) {
            return false; // discovery system off — nothing is "discovered", no alerts
        }
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
        if (!Config.discoveryEnabled()) {
            return true; // discovery off — everything counts as known
        }
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
        if (!Config.discoveryEnabled()) {
            return false;
        }
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

    /**
     * one-time on a player's first-ever join: silently reveal a few low-power curses/blessings so the
     * Compendium isn't blank to start. gated by a persisted flag so it never repeats. no chat alert.
     */
    public static void grantStarterDiscoveries(ServerPlayer player) {
        if (!Config.discoveryEnabled()
                || !Config.STARTER_DISCOVERY_ENABLED.get()
                || player.getData(WitchModAttachments.STARTER_DISCOVERY_DONE) == 1) {
            return;
        }
        player.setData(WitchModAttachments.STARTER_DISCOVERY_DONE, 1);
        int min = Config.STARTER_DISCOVERY_POWER_MIN.get();
        int max = Math.max(min, Config.STARTER_DISCOVERY_POWER_MAX.get());
        grantRandom(player, EffectCategory.CURSE, Config.STARTER_DISCOVERY_CURSES.get(), min, max);
        grantRandom(player, EffectCategory.BLESSING, Config.STARTER_DISCOVERY_BLESSINGS.get(), min, max);
    }

    private static void grantRandom(ServerPlayer player, EffectCategory category, int count, int min, int max) {
        if (count <= 0) {
            return;
        }
        List<ResourceLocation> pool = new ArrayList<>();
        for (Effect e : WitchModRegistries.EFFECT_REGISTRY) {
            ResourceLocation id = WitchModRegistries.EFFECT_REGISTRY.getKey(e);
            if (id == null || !e.selectable() || e.category() != category
                    || e.powerLevel() < min || e.powerLevel() > max) {
                continue;
            }
            pool.add(id);
        }
        Collections.shuffle(pool, new java.util.Random(player.getRandom().nextLong()));
        for (int i = 0; i < count && i < pool.size(); i++) {
            setEffectDiscovered(player, pool.get(i), true); // silent (no chat alert)
        }
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
