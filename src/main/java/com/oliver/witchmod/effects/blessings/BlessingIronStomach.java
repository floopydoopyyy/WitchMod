package com.oliver.witchmod.effects.blessings;

import java.util.Map;
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

    /**
     * extra normally-INEDIBLE things a cast-iron gut can stomach → the hunger each restores. Eaten via a
     * right-click (they have no vanilla use), handled in {@code BlessingEventHandler.onIronStomachExtraEat};
     * a glistering melon slice also grants a short burst of Regeneration. Curated data, like {@link #BAD_FOODS}.
     */
    public static final Map<Item, Integer> EXTRA_FOODS = Map.of(
            Items.GLISTERING_MELON_SLICE, 6,
            Items.FERMENTED_SPIDER_EYE, 6,
            Items.EGG, 1,
            Items.SUGAR, 1,
            Items.SUGAR_CANE, 2,
            Items.RED_MUSHROOM, 2,
            Items.BROWN_MUSHROOM, 2,
            Items.NETHER_WART, 4,
            Items.COCOA_BEANS, 1);

    public BlessingIronStomach() {
        super(EffectCategory.BLESSING, EffectCostTier.MINOR, 24, () -> Items.CHICKEN);
    }

    /** you find out the first time you wolf down something rank and feel great for it (Rule 2). */
    @Override
    public boolean discoversOnTrigger() {
        return true;
    }
}
