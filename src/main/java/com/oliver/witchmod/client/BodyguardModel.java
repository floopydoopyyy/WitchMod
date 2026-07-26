package com.oliver.witchmod.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.oliver.witchmod.entities.BodyguardEntity;

/**
 * The Bodyguard's model — vanilla humanoid (skeleton geometry), but it poses the right arm to actually HOLD
 * its sword when one is drawn. Without that the arm just hangs and the attack swing reads as a flail; with
 * {@code ArmPose.ITEM} the sword is held out and vanilla's own attack-swing animation (driven by
 * {@code swing()} → {@code attackTime}) plays over it properly. Walking and idle bob come free from
 * {@link HumanoidModel}.
 */
@OnlyIn(Dist.CLIENT)
public class BodyguardModel<T extends BodyguardEntity> extends HumanoidModel<T> {
    public BodyguardModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        this.rightArmPose = entity.getMainHandItem().isEmpty()
                ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        this.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }
}
