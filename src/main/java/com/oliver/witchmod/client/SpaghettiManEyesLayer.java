package com.oliver.witchmod.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.entities.SpaghettiManEntity;

/**
 * emissive eyes for the Spaghetti Man — the same trick vanilla uses for the Enderman/Spider. The eye texture
 * ({@code spaghetti_man_eyes.png}) is transparent everywhere but the glowing pixels, rendered with
 * {@link RenderType#eyes} so it burns at full brightness through the dark.
 */
@OnlyIn(Dist.CLIENT)
public final class SpaghettiManEyesLayer extends RenderLayer<SpaghettiManEntity, SpaghettiManModel<SpaghettiManEntity>> {
    private static final RenderType EYES = RenderType.eyes(
            ResourceLocation.fromNamespaceAndPath(WitchMod.MODID, "textures/entity/spaghetti_man_eyes.png"));

    public SpaghettiManEyesLayer(net.minecraft.client.renderer.entity.RenderLayerParent<SpaghettiManEntity, SpaghettiManModel<SpaghettiManEntity>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, SpaghettiManEntity entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        // the glow FLICKERS — but stays lit the great majority of the time, with the odd brief blink-off, like a
        // failing bulb. Deterministic per-entity so it doesn't strobe randomly every frame.
        if (eyesOff(entity, ageInTicks)) {
            return;
        }
        this.getParentModel().renderToBuffer(poseStack, buffer.getBuffer(EYES), 15728640, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, -1);
    }

    /** true for the rare, brief moments the eyes are dark (a flicker). Otherwise they glow. */
    private static boolean eyesOff(SpaghettiManEntity entity, float ageInTicks) {
        int seg = (int) (ageInTicks / 3.0F);                 // a fresh roll every ~3 ticks
        float pick = hash(entity.getId() * 2654435761L + seg);
        if (pick < 0.06F) {                                  // ~6% of segments blink off entirely
            return true;
        }
        // A rarer, faster sputter within a segment (a single dark tick) so it isn't a clean square-wave.
        return pick < 0.10F && ((int) ageInTicks & 1) == 0;
    }

    private static float hash(long n) {
        long h = n * 0x9E3779B97F4A7C15L;
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return ((h >>> 40) & 0xFFFFFF) / (float) 0xFFFFFF;
    }
}
