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
 * fortune of a subtler kind: your vanilla LUCK attribute
 * is greatly amplified, nudging loot-table rolls — fishing, chests, and anything else that reads luck — your
 * way. It probably doesn't do much moment to moment, but over a session it's worth having.
 *
 * <p>Applied as a plain {@code ADD_VALUE} modifier on {@link Attributes#LUCK}. The modifier is TRANSIENT, so
 * {@link #onTick} re-applies it if a world reload dropped it while the blessing persisted. Discovery is
 * handled in {@code BlessingEventHandler} — the first time you fish something up or crack open a loot chest,
 * the moment the amplified luck could actually have mattered.
 */
public final class BlessingLuck extends Effect {
    public static final ResourceLocation LUCK_MODIFIER_ID = EffectUtil.modifierId("blessing_luck");

    public BlessingLuck() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 28, () -> Items.RABBIT_FOOT);
    }

    /** discovered when the luck first has something to roll on (fishing / a loot chest). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        applyLuck(target);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        // self-heal: a reload drops the transient modifier while the blessing itself persists.
        AttributeInstance luck = target.getAttribute(Attributes.LUCK);
        if (luck != null && !luck.hasModifier(LUCK_MODIFIER_ID)) {
            applyLuck(target);
        }
    }

    @Override
    public void onRemove(ServerPlayer target) {
        EffectUtil.removeModifier(target, Attributes.LUCK, LUCK_MODIFIER_ID);
    }

    private static void applyLuck(ServerPlayer target) {
        AttributeInstance luck = target.getAttribute(Attributes.LUCK);
        if (luck != null && !luck.hasModifier(LUCK_MODIFIER_ID)) {
            luck.addOrUpdateTransientModifier(new AttributeModifier(LUCK_MODIFIER_ID,
                    Config.LUCK_ATTRIBUTE_BONUS.get(), AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
