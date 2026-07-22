package com.oliver.witchmod.effects.curses;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.Config;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;
import com.oliver.witchmod.data.EffectUtil;
import com.oliver.witchmod.data.WitchModAttachments;

/**
 * You're always hungry, and it shows — literally (master-spec Gluttony). The player is rendered wider and
 * carries a second hunger row, but the two rows are <b>ONE BIG 40-POINT BAR</b>, not two separate meters:
 *
 * <ul>
 *   <li>The extra row ({@link WitchModAttachments#GLUTTONY_HUNGER}, drawn above vanilla's) is the TOP half.
 *       Vanilla hunger drains as normal, and every tick any shortfall is topped straight back up out of the
 *       extra — so the upper half empties first, then the vanilla half, exactly like one long bar draining.</li>
 *   <li>Because of that, <b>starvation takes twice as long to reach</b>: you only start starving once all 40
 *       points are gone.</li>
 *   <li>Eating fills vanilla first and the <b>overflow spills into the extra row</b> instead of being wasted
 *       (see the eating hook in {@code CurseEventHandler}).</li>
 *   <li>Sprinting cuts out at {@code gluttonySprintCutoff} of the COMBINED bar — double vanilla's 6.</li>
 * </ul>
 *
 * <p>The trade: food is eaten {@code gluttonyEatSpeedPercent} faster (the one mercy), but every meal gives
 * {@code gluttonySaturationPenaltyPercent} less saturation, so it never sticks and you have to keep eating.
 */
public final class CurseGluttony extends Effect {
    public static final int EXTRA_MAX = 20;
    /** Vanilla's own bar length — the lower half of the combined bar. */
    private static final int VANILLA_MAX = 20;
    /**
     * Where the vanilla half is held while there's still reserve above it — deliberately ONE POINT SHORT of
     * full. Vanilla gates eating on {@code FoodData.needsFood()}, which is simply {@code foodLevel < 20}, so
     * holding the lower half at a true 20 makes the game think you're full and <b>refuses every food item</b>
     * until the reserve runs dry. Sitting at 19 keeps you permanently able to eat, and costs half a drumstick
     * of display.
     */
    private static final int VANILLA_HOLD = VANILLA_MAX - 1;

    private static final ResourceLocation SCALE_MODIFIER_ID = EffectUtil.modifierId("curse_gluttony_scale");

    public CurseGluttony() {
        super(EffectCategory.CURSE, EffectCostTier.MINOR, 25, () -> Items.CAKE);
    }

    @Override
    public void onApply(ServerPlayer target, @Nullable ServerPlayer caster, int durationTicks) {
        target.setData(WitchModAttachments.GLUTTONY_HUNGER, EXTRA_MAX);
        EffectUtil.addModifier(target, Attributes.SCALE, SCALE_MODIFIER_ID,
                Config.GLUTTONY_MODEL_SCALE_BONUS.get(), AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public void onRemove(ServerPlayer target) {
        target.setData(WitchModAttachments.GLUTTONY_HUNGER, -1); // hide the extra row
        EffectUtil.removeModifier(target, Attributes.SCALE, SCALE_MODIFIER_ID);
    }

    @Override
    public void onTick(ServerPlayer target, int ticksRemaining) {
        int extra = target.getData(WitchModAttachments.GLUTTONY_HUNGER);
        if (extra < 0) {
            extra = EXTRA_MAX; // self-heal (relog — onApply doesn't re-run)
        }
        // The SCALE modifier is TRANSIENT, so a world reload drops it while the curse itself persists —
        // you'd come back the wrong size. onApply never re-runs, so re-apply it here if it's gone.
        AttributeInstance scale = target.getAttribute(Attributes.SCALE);
        if (scale != null && !scale.hasModifier(SCALE_MODIFIER_ID)) {
            scale.addOrUpdateTransientModifier(new AttributeModifier(SCALE_MODIFIER_ID,
                    Config.GLUTTONY_MODEL_SCALE_BONUS.get(), AttributeModifier.Operation.ADD_VALUE));
        }

        // THE COMBINED BAR: keep the vanilla half sitting at VANILLA_HOLD while there's reserve above it, so
        // the pair behaves as one 40-point bar draining from the top down. Anything above the hold line gets
        // pushed UP into the reserve (that's where eating overflow settles), anything below is drawn back
        // DOWN out of it — so starvation only begins once the reserve is spent and vanilla runs to zero.
        FoodData food = target.getFoodData();
        int level = food.getFoodLevel();
        if (level > VANILLA_HOLD && extra < EXTRA_MAX) {
            int push = Math.min(level - VANILLA_HOLD, EXTRA_MAX - extra);
            food.setFoodLevel(level - push);
            extra += push;
        } else if (level < VANILLA_HOLD && extra > 0) {
            int pull = Math.min(VANILLA_HOLD - level, extra);
            food.setFoodLevel(level + pull);
            extra -= pull;
        }
        target.setData(WitchModAttachments.GLUTTONY_HUNGER, extra);

        // NOTE: the sprint cutoff is enforced CLIENT-side (see ClientCurseHandler). Sprinting is decided by
        // the client, so calling setSprinting(false) here is simply overwritten on the next client tick —
        // which is why the cutoff appeared to do nothing and vanilla's own 6-point rule was all that applied.
    }

    /** Total of both halves, 0..40. */
    public static int combined(ServerPlayer player) {
        int extra = Math.max(0, player.getData(WitchModAttachments.GLUTTONY_HUNGER));
        return player.getFoodData().getFoodLevel() + extra;
    }

    /** Spills eating overflow into the extra row (clamped). No-op while the curse isn't active. */
    public static void feed(ServerPlayer player, int overflow) {
        int extra = player.getData(WitchModAttachments.GLUTTONY_HUNGER);
        if (extra < 0 || overflow <= 0) {
            return;
        }
        player.setData(WitchModAttachments.GLUTTONY_HUNGER, Math.min(EXTRA_MAX, extra + overflow));
    }
}
