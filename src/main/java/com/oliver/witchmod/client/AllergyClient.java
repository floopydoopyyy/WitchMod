package com.oliver.witchmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.curses.AllergicReaction;

/** client side of the allergic reaction: the green skin tint (read by the render mixins) and the fov squeeze. */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class AllergyClient {
    /** skin multiply colours per tier (rgb). tier 3 pulses between the last two. */
    private static final int[] TIER_TINT = {0xD2FFC4, 0x9EEB86, 0x6FD455, 0x9BF26E};

    private AllergyClient() {}

    /** synced tier for any player (0 for everything else). */
    public static int tierOf(Entity entity) {
        return entity instanceof Player player ? player.getData(WitchModAttachments.ALLERGY_TIER) : 0;
    }

    /** multiplies the tier's green into a model colour (keeps its alpha); unchanged when not reacting. */
    public static int tint(Entity entity, int color) {
        int tier = tierOf(entity);
        if (tier <= 0) {
            return color;
        }
        int rgb = TIER_TINT[Math.min(tier, 3) - 1];
        if (tier >= 3) {
            float t = (Mth.sin((entity.tickCount + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false))
                    * 0.15F) + 1.0F) * 0.5F;
            rgb = lerpRgb(TIER_TINT[2], TIER_TINT[3], t);
        }
        return FastColor.ARGB32.multiply(color, 0xFF000000 | rgb);
    }

    private static int lerpRgb(int a, int b, float t) {
        int r = Mth.lerpInt(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = Mth.lerpInt(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = Mth.lerpInt(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    @SubscribeEvent
    static void onFov(ComputeFovModifierEvent event) {
        float reduction = AllergicReaction.fovReduction(AllergicReaction.tierOf(event.getPlayer()));
        if (reduction > 0.0F) {
            event.setNewFovModifier(event.getNewFovModifier() * (1.0F - reduction));
        }
    }
}
