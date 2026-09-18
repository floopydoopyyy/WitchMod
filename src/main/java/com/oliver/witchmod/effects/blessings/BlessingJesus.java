package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * the water holds you up: you walk on the surface of water,
 * and CROUCHING drops you under it. Water only — not lava (JESUS_WORKS_ON_LAVA=false).
 *
 * <p>The actual surface-walking is applied CLIENT-side (player movement is client-authoritative) off the
 * synced {@link WitchModAttachments#JESUS_ACTIVE} flag — see {@code ClientCurseHandler.tickJesus}. The
 * local player's resulting position syncs back up, so other players see you stroll across the water.
 *
 * <p><b>Depth Strider re-purposed</b>: an enchantment that normally speeds you through water is useless when
 * you walk ON it — so while you're stood on the surface, Depth Strider instead gives you a MOVEMENT_SPEED
 * bonus (per level), applied server-side as a transient modifier and stripped the moment you leave the surface.
 */
public final class BlessingJesus extends Effect {
    private static final ResourceLocation SPEED_ID = EffectUtil.modifierId("blessing_jesus_depth_strider");

    public BlessingJesus() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.LILY_PAD);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.JESUS_ACTIVE, 1);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        int ds = depthStriderLevel(target);
        double want = ds > 0 && onWaterSurface(target)
                ? ds * Config.JESUS_DEPTH_STRIDER_SPEED_PER_LEVEL.get()
                : 0.0;
        AttributeModifier current = speed.getModifier(SPEED_ID);
        if (want > 0.0) {
            if (current == null || Math.abs(current.amount() - want) > 1.0E-6) {
                EffectUtil.removeModifier(target, Attributes.MOVEMENT_SPEED, SPEED_ID);
                EffectUtil.addModifier(target, Attributes.MOVEMENT_SPEED, SPEED_ID, want,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            }
        } else if (current != null) {
            EffectUtil.removeModifier(target, Attributes.MOVEMENT_SPEED, SPEED_ID);
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.JESUS_ACTIVE, -1);
        EffectUtil.removeModifier(target, Attributes.MOVEMENT_SPEED, SPEED_ID);
    }

    /** depth Strider level on the player's boots (0 if none). */
    private static int depthStriderLevel(ServerPlayer player) {
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (boots.isEmpty()) {
            return 0;
        }
        Holder<Enchantment> depthStrider = player.level().registryAccess()
.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.DEPTH_STRIDER);
        return EnchantmentHelper.getItemEnchantmentLevel(depthStrider, boots);
    }

    /**
     * true while the player is stood on the water surface. Deliberately does NOT check {@code onGround()} — the
     * server only reports a water-walker as on-ground intermittently, which made the boost flicker on and off.
     * Instead: not crouched/flying (crouching submerges you), not submerged (eyes above water), and water right
     * at/under your feet — i.e. you're on top of a water column.
     */
    private static boolean onWaterSurface(ServerPlayer player) {
        if (player.isShiftKeyDown() || player.getAbilities().flying || player.isUnderWater()) {
            return false;
        }
        return player.level().getFluidState(player.blockPosition()).is(FluidTags.WATER)
                || player.level().getFluidState(player.blockPosition().below()).is(FluidTags.WATER);
    }
}
