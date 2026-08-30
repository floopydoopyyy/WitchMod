package com.oliver.witchmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import com.oliver.witchmod.WitchMod;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * Makes the Gladiator hold the weapon in a defensive guard while a parry window is open — <b>both</b>
 * perspectives, no mixins:
 * <ul>
 *   <li><b>Third person</b> (and other players' view): {@link #PARRY_POSE} is registered as a client item
 *       extension for every sword/axe, and returns {@link HumanoidModel.ArmPose#BLOCK} while the holder is
 *       parrying — the same hook vanilla uses to pose a raised shield, so it renders cleanly and is visible
 *       to everyone tracking that player (the parry-window tick is synced to trackers).</li>
 *   <li><b>First person</b>: {@link #onRenderHand} tilts the held weapon into an old-school block angle.</li>
 * </ul>
 */
@EventBusSubscriber(modid = WitchMod.MODID, value = Dist.CLIENT)
public final class GladiatorClientHandler {
    private GladiatorClientHandler() {}

    /** Registered for all swords/axes (see {@code WitchModClient}); poses the arm as a block while parrying. */
    public static final IClientItemExtensions PARRY_POSE = new IClientItemExtensions() {
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            if (hand == InteractionHand.MAIN_HAND && entity instanceof Player player && isParrying(player)) {
                return HumanoidModel.ArmPose.BLOCK;
            }
            return null; // default pose otherwise
        }
    };

    private static boolean isParrying(Player player) {
        return player.getData(WitchModAttachments.GLADIATOR_PARRY_END) > player.level().getGameTime();
    }

    @SubscribeEvent
    static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !isParrying(player)) {
            return;
        }
        Item item = event.getItemStack().getItem();
        if (!(item instanceof SwordItem) && !(item instanceof AxeItem)) {
            return;
        }
        // Bring the blade up into a guard and angle it across — an approximation of the classic 1.8 block.
        PoseStack pose = event.getPoseStack();
        pose.translate(0.05, 0.02, -0.08);
        pose.mulPose(Axis.YP.rotationDegrees(-32));
        pose.mulPose(Axis.ZP.rotationDegrees(22));
        pose.mulPose(Axis.XP.rotationDegrees(-12));
    }
}
