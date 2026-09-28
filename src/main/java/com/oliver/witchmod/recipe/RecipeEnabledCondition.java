package com.oliver.witchmod.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.conditions.ICondition;

import com.oliver.witchmod.Config;

/**
 * datapack condition on a witchmod recipe: it loads only while its {@code recipe} (the output item id) is NOT
 * in the server config's disabledRecipes list — so any mod item's crafting recipe can be switched off. read at
 * recipe-load, so it takes effect on world load / {@code /reload}.
 */
public record RecipeEnabledCondition(ResourceLocation recipe) implements ICondition {
    public static final MapCodec<RecipeEnabledCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("recipe").forGetter(RecipeEnabledCondition::recipe)
    ).apply(i, RecipeEnabledCondition::new));

    @Override
    public boolean test(IContext context) {
        return !Config.isRecipeDisabled(recipe);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
