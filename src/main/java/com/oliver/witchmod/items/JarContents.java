package com.oliver.witchmod.items;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.oliver.witchmod.data.CapturedEffect;
import com.oliver.witchmod.data.Effect;
import com.oliver.witchmod.data.EffectCategory;
import com.oliver.witchmod.data.WitchModDataComponents;
import com.oliver.witchmod.data.WitchModRegistries;

/**
 * The stored-effect logic shared by every jar. A jar is a DYNAMIC item: its variant (Cursed / Blessed /
 * Mixed) is derived from what it holds, so adding a blessing to a Cursed Jar turns the stack into a Mixed
 * Jar. Holds up to {@link #MAX} attachments.
 */
public final class JarContents {
    /** Max attachments any jar holds. */
    public static final int MAX = 3;

    /** Splash-colour kind. */
    public static final int CURSED = 0, BLESSED = 1, MIXED = 2;

    private JarContents() {}

    public static List<CapturedEffect> contents(ItemStack jar) {
        return jar.getOrDefault(WitchModDataComponents.CAPTURED_EFFECTS, List.of());
    }

    public static boolean isFull(ItemStack jar) {
        return contents(jar).size() >= MAX;
    }

    /** A NEW jar stack of the correct variant for {@code list}, keeping {@code count}. */
    public static ItemStack stackFor(List<CapturedEffect> list, int count) {
        ItemStack stack = new ItemStack(itemFor(list), Math.max(1, count));
        stack.set(WitchModDataComponents.CAPTURED_EFFECTS, List.copyOf(list));
        return stack;
    }

    /** {@code jar} with one more effect added, as the (possibly different) correct variant. */
    public static ItemStack withAdded(ItemStack jar, ResourceLocation effectId, int remainingTicks) {
        List<CapturedEffect> list = new ArrayList<>(contents(jar));
        list.add(new CapturedEffect(effectId, remainingTicks));
        return stackFor(list, jar.getCount());
    }

    /** The registered item that matches these contents (empty → the plain Jar). */
    public static Item itemFor(List<CapturedEffect> list) {
        boolean curse = false;
        boolean bless = false;
        for (CapturedEffect e : list) {
            EffectCategory cat = categoryOf(e.effectId());
            if (cat == EffectCategory.CURSE) {
                curse = true;
            } else if (cat == EffectCategory.BLESSING) {
                bless = true;
            }
        }
        if (curse && bless) {
            return WitchModItems.MIXED_JAR.get();
        }
        if (bless) {
            return WitchModItems.BLESSED_JAR.get();
        }
        if (curse) {
            return WitchModItems.CURSED_JAR.get();
        }
        return WitchModItems.JAR.get();
    }

    /** Colour kind for splash/lash particles, from the contents. */
    public static int kind(List<CapturedEffect> list) {
        boolean curse = false;
        boolean bless = false;
        for (CapturedEffect e : list) {
            EffectCategory cat = categoryOf(e.effectId());
            if (cat == EffectCategory.CURSE) {
                curse = true;
            } else if (cat == EffectCategory.BLESSING) {
                bless = true;
            }
        }
        if (curse && bless) {
            return MIXED;
        }
        return bless ? BLESSED : CURSED;
    }

    public static EffectCategory categoryOf(ResourceLocation effectId) {
        return WitchModRegistries.EFFECT_REGISTRY.getOptional(effectId).map(Effect::category).orElse(null);
    }
}
