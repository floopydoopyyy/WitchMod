package com.oliver.witchmod.items;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.oliver.witchmod.WitchMod;

/** the mod's custom recipe serializers. */
public final class WitchModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, WitchMod.MODID);

    public static final Supplier<RecipeSerializer<VoodooBindRecipe>> VOODOO_BIND =
            SERIALIZERS.register("crafting_special_voodoo_bind",
                    () -> new SimpleCraftingRecipeSerializer<>(VoodooBindRecipe::new));

    private WitchModRecipes() {}

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
    }
}
