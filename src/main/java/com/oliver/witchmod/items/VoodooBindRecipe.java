package com.oliver.witchmod.items;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import com.oliver.witchmod.data.PlayerEssenceData;
import com.oliver.witchmod.data.WitchModDataComponents;

/**
 * crafting-grid binding: doll + a bound player essence → a doll bound to that player (essence consumed). a
 * shapeless special recipe so it can copy the essence's bound-player component onto the result.
 */
public final class VoodooBindRecipe extends CustomRecipe {
    public VoodooBindRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        int dolls = 0;
        int essences = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (s.getItem() instanceof ItemVoodooDoll) {
                dolls++;
            } else if (s.getItem() == WitchModItems.PLAYER_ESSENCE.get() && s.has(WitchModDataComponents.BOUND_PLAYER)) {
                essences++;
            } else {
                return false; // anything else in the grid → not this recipe
            }
        }
        return dolls == 1 && essences == 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        PlayerEssenceData essence = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.getItem() == WitchModItems.PLAYER_ESSENCE.get()) {
                essence = s.get(WitchModDataComponents.BOUND_PLAYER);
            }
        }
        ItemStack doll = new ItemStack(WitchModItems.VOODOO_DOLL.get());
        if (essence != null) {
            ItemVoodooDoll.bind(doll, essence);
        }
        return doll;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return WitchModRecipes.VOODOO_BIND.get();
    }
}
