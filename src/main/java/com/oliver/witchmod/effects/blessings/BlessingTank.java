package com.oliver.witchmod.effects.blessings;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;

/**
 * A walking wall (sacrificial item COBBLED DEEPSLATE): a whole extra health bar, with the trade that natural
 * regeneration is significantly slower ({@code BlessingEventHandler}'s heal hook).
 *
 * <p>The extra HP is a {@code MAX_HEALTH} attribute modifier — NOT a Health Boost effect — so it never shows
 * as a status effect anywhere; it just reads as more hearts on the bar. The modifier is transient and
 * re-applied in {@link #onTick} so it survives a death/reload while the blessing persists.
 */
public final class BlessingTank extends Effect {
    public static final ResourceLocation HEALTH_ID = EffectUtil.modifierId("blessing_tank_health");

    public BlessingTank() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.COBBLED_DEEPSLATE);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        applyHealth(target);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        applyHealth(target); // self-heal after a death/reload dropped the transient modifier
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeModifier(target, Attributes.MAX_HEALTH, HEALTH_ID);
        // Max health drops; vanilla clamps current HP on the next tick.
    }

    private static void applyHealth(ServerPlayer target) {
        AttributeInstance health = target.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && !health.hasModifier(HEALTH_ID)) {
            health.addOrUpdateTransientModifier(new AttributeModifier(HEALTH_ID,
                    Config.TANK_BONUS_HEALTH.get(), AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
