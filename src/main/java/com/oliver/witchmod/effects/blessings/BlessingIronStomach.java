package com.oliver.witchmod.effects.blessings;

import java.util.Set;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.EffectCostTier;

/**
 * cast-iron guts: you can eat any of the game's
 * "bad" foods with NO penalty, and you actually get MORE hunger and saturation out of them than anyone else
 * would. The work — stripping the food's negative effects and granting the bonus nutrition — happens on the
 * eat, in {@code BlessingEventHandler.onIronStomachEat}.
 */
public final class BlessingIronStomach extends Effect {
    /**
     * the vanilla foods that come with a downside — the ones this blessing is about. Kept a curated set rather
     * than reflecting each food's effects, which changed shape across 1.21.x; modded bad foods aren't covered
     * automatically, which is an acceptable simplification for now.
     */
    public static final Set<Item> BAD_FOODS = Set.of(
            Items.ROTTEN_FLESH, Items.CHICKEN, Items.PUFFERFISH, Items.POISONOUS_POTATO, Items.SPIDER_EYE);

    public BlessingIronStomach() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.CHICKEN);
    }

    /** you find out the first time you wolf down something rank and feel great for it (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }
}
