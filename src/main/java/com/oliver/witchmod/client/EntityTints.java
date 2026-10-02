package com.oliver.witchmod.client;

import net.minecraft.util.FastColor;
import net.minecraft.world.entity.LivingEntity;

import com.oliver.witchmod.entities.ShadowEntity;

/** the body-model colour overrides applied by {@link com.oliver.witchmod.mixin.EntityTintMixin}. */
public final class EntityTints {
    /** the shadow: see-through (alpha ~0.6) and purple. its renderer draws with a translucent render type. */
    private static final int SHADOW = 0x99A060FF;
    /** extra opacity scale for the shadow pass being drawn (summon fade-in, afterimages); set by {@link ShadowRenderer}. */
    static float shadowAlpha = 1.0F;

    private EntityTints() {}

    public static int tint(LivingEntity entity, int color) {
        if (entity instanceof ShadowEntity) {
            int alpha = Math.round(FastColor.ARGB32.alpha(SHADOW) * shadowAlpha);
            return FastColor.ARGB32.multiply(color, (alpha << 24) | (SHADOW & 0xFFFFFF));
        }
        return AllergyClient.tint(entity, color);
    }
}
