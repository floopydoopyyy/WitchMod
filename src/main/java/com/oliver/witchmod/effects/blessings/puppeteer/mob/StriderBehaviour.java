package com.oliver.witchmod.effects.blessings.puppeteer.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;
import com.oliver.witchmod.effects.blessings.puppeteer.*;

/**
 * puppeteer: the strider. no move of its own — it's a lava walker: it stands on lava (LivingEntityFluidMixin, the real
 * strider's canStandOnFluid) and runs quick across it, fire can't touch it, and out of the warm it shivers (the cold,
 * purple look) and slows. water and rain hurt it. ridden like a pig — a saddle, then a rider with a warped fungus on a
 * stick steers and boosts (RideSteering) — the one mount that crosses lava. lured by warped fungus.
 */
public final class StriderBehaviour implements PuppetBehaviour {
    public static final StriderBehaviour INSTANCE = new StriderBehaviour();

    private StriderBehaviour() {}

    private static final ResourceLocation STRIDER_WARMTH_ID = EffectUtil.modifierId("puppeteer_strider_warmth");

    /** shivering out of the warm (PUPPET_DASH_POWER 1, synced for the cold look). */
    public static boolean cold(Player player) {
        return PuppetType.byId(player.getData(WitchModAttachments.PUPPET_TYPE)) == PuppetType.STRIDER
                && player.getData(WitchModAttachments.PUPPET_DASH_POWER) == 1;
    }

    /** vanilla's warmth test: on or in a warm block (lava, magma, a lava cauldron...), or wading in lava. */
    private static boolean warm(ServerPlayer player) {
        BlockPos at = player.blockPosition();
        return player.level().getBlockState(at).is(BlockTags.STRIDER_WARM_BLOCKS)
                || player.level().getBlockState(at.below()).is(BlockTags.STRIDER_WARM_BLOCKS)
                || player.getFluidHeight(FluidTags.LAVA) > 0.0;
    }

    @Override
    public void tick(ServerPlayer player, PuppetType type, int ticksRemaining) {
        boolean cold = !warm(player);
        if (cold != cold(player)) {
            player.setData(WitchModAttachments.PUPPET_DASH_POWER, cold ? 1 : 0);
        }
        // quick on lava, sluggish in the cold (vanilla: 0.55 vs 0.35 of its speed)
        double warmth = cold ? -Config.PUPPETEER_STRIDER_COLD_SLOW.get() : Config.PUPPETEER_STRIDER_LAVA_SPEED.get();
        net.minecraft.world.entity.ai.attributes.AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeModifier had = speed == null ? null : speed.getModifier(STRIDER_WARMTH_ID);
        if (had == null || had.amount() != warmth) { // (only on a change, or it'd re-sync every tick)
            EffectUtil.addModifier(player, Attributes.MOVEMENT_SPEED, STRIDER_WARMTH_ID, warmth, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
        // water and rain sting it, as they do a real strider
        if (player.isInWaterRainOrBubble()) {
            player.hurt(player.damageSources().drown(), 1.0F);
        }
        FarmAnimalBehaviour.lure(player, type); // someone holding warped fungus draws it in
        RideSteering.tick(player);
    }

    /** a saddle goes on; saddled, another player climbs aboard. */
    @Override
    public boolean interact(Player user, InteractionHand hand, ItemStack stack, ServerPlayer puppet, PuppetType type) {
        return RideSteering.interact(user, hand, stack, puppet, type);
    }

    @Override
    public void clear(ServerPlayer player, CompoundTag data) {
        EffectUtil.removeModifier(player, Attributes.MOVEMENT_SPEED, STRIDER_WARMTH_ID);
        RideSteering.clear(player);
    }

    /** a strider never attacks. */
    @Override
    public boolean dealsMelee(ServerPlayer player, PuppetType type) {
        return false;
    }

    /** no move: the hud just shows how it's ridden. */
    @Override
    public void controls(Player player, PuppetType type, PuppetControls out) {
    }
}
