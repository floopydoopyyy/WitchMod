package com.oliver.witchmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.oliver.witchmod.entities.SpaghettiManEntity;

/**
 * the Spaghetti Man — a lanky humanoid built entirely in code (no Blockbench). Human PROPORTIONS but pulled
 * out like taffy into an enderman-like silhouette: a small head on a long thin neck, a long narrow torso,
 * very long legs, and arms that DRAPE down past the hips toward the floor. The glowing eyes live on the face
 * ({@link SpaghettiManEyesLayer}); the face texture reads as a fixed grin, which the twitchy idle plays off.
 *
 * <p>Idle is deliberately freakish: the head LOLLS slowly side to side (a roll) with sudden sharper cocks
 * snapping in on top, plus nervous little yaw/pitch flickers, while the long arms give the odd subtle jerk —
 * a smiling thing that can't quite hold still. It only travels during a chase, where the stride is kept
 * human-ish and controlled (a full beastly flail would look silly at the speeds it moves).
 */
@OnlyIn(Dist.CLIENT)
public final class SpaghettiManModel<T extends SpaghettiManEntity> extends EntityModel<T> {
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public SpaghettiManModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // feet rest at y=24 (the usual ground plane). Everything is stretched UPWARD from there, so the head
        // ends up around y=-32 — a figure ~3.5 blocks tall. Thin throughout, legs close together.

        // very long thin legs (26 tall), hips at y=-2.
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                        .texOffs(40, 32).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 26.0F, 2.0F),
                PartPose.offset(-1.0F, -2.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                        .texOffs(48, 32).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 26.0F, 2.0F),
                PartPose.offset(1.0F, -2.0F, 0.0F));

        // long narrow torso (20 tall): hips (y=-2) up to shoulders (y=-22).
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(16, 16).addBox(-3.0F, -20.0F, -2.0F, 6.0F, 20.0F, 4.0F),
                PartPose.offset(0.0F, -2.0F, 0.0F));

        // A long thin neck (4 tall) atop the shoulders.
        root.addOrReplaceChild("neck", CubeListBuilder.create()
                        .texOffs(0, 40).addBox(-1.5F, -4.0F, -1.5F, 3.0F, 4.0F, 3.0F),
                PartPose.offset(0.0F, -22.0F, 0.0F));

        // small head (6^3) on the neck; pivots about the neck-top so the loll/cock/track reads naturally.
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.0F, -6.0F, -3.0F, 6.0F, 6.0F, 6.0F),
                PartPose.offset(0.0F, -26.0F, 0.0F));

        // very long arms (28) that hang from the shoulders and DRAPE down past the hips toward the floor.
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(40, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 28.0F, 3.0F),
                PartPose.offset(-4.0F, -22.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                        .texOffs(52, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 28.0F, 3.0F),
                PartPose.offset(4.0F, -22.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    /** deterministic pseudo-random in [0,1) from an integer seed — no per-frame allocation. */
    private static float hash(int n) {
        int h = n * 374761393 + 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        return ((h ^ (h >> 16)) & 0x7fffffff) / (float) 0x7fffffff;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // how "still" it is — ~1 standing/watching, ~0 while striding in a chase; scales the idle twitch away.
        float idle = 1.0F - Mth.clamp(limbSwingAmount * 3.0F, 0.0F, 1.0F);

        // head base tracking (synced head rotation of the entity).
        this.head.yRot = netHeadYaw * (Mth.PI / 180.0F);
        this.head.xRot = headPitch * (Mth.PI / 180.0F);

        // IDLE HEAD: a slow side-to-side LOLL (roll) with sudden sharper COCKS snapping in on top — freakish,
        // never smoothly still, playing off the fixed grin.
        float loll = Mth.sin(ageInTicks * 0.09F) * 0.30F; // lazy ~17° lull each way
        int seg = entity.tickCount / 26;
        int local = entity.tickCount % 26;
        float pick = hash(entity.getId() * 31 + seg);
        float cock = 0.0F;
        if (pick < 0.78F) {
            float amp = (hash(entity.getId() * 91 + seg) - 0.5F) * 1.7F; // sudden extra tilt, up to ~48°
            float env = local < 2 ? local / 2.0F : (local > 20 ? (26 - local) / 6.0F : 1.0F);
            cock = amp * Mth.clamp(env, 0.0F, 1.0F);
        }
        this.head.zRot = (loll + cock) * idle;
        // nervous flickers — quick small yaw/pitch twitches so the head is never dead still.
        this.head.yRot += (Mth.sin(entity.tickCount * 2.3F) * 0.05F
                + (hash(entity.getId() * 7 + entity.tickCount / 3) - 0.5F) * 0.07F) * idle;
        this.head.xRot += Mth.sin(entity.tickCount * 1.9F + 1.3F) * 0.045F * idle;

        // IDLE ARMS: the long draped arms hang with a slight outward splay and give the odd subtle JERK.
        float twR = hash(entity.getId() * 13 + entity.tickCount / 5) - 0.5F;
        float twL = hash(entity.getId() * 17 + entity.tickCount / 5 + 3) - 0.5F;
        this.rightArm.zRot = 0.04F + Mth.abs(twR) * 0.06F * idle;   // slight hang + twitch
        this.leftArm.zRot = -0.04F - Mth.abs(twL) * 0.06F * idle;
        this.rightArm.xRot = twR * 0.16F * idle;
        this.leftArm.xRot = twL * 0.16F * idle;

        // CHASE WALK: a controlled human-ish stride — clearly moving, but not a beastly flail (which would look
        // goofy at its speed). Arms counter-swing to the legs, damped.
        float swing = Mth.cos(limbSwing * 0.5F) * limbSwingAmount;
        float swingOpp = Mth.cos(limbSwing * 0.5F + Mth.PI) * limbSwingAmount;
        this.rightLeg.xRot = swing * 0.85F;
        this.leftLeg.xRot = swingOpp * 0.85F;
        this.rightArm.xRot += swingOpp * 0.5F;
        this.leftArm.xRot += swing * 0.5F;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
