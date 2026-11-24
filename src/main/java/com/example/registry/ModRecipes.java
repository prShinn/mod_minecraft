package com.example.registry;

import com.example.ExampleMod;
import com.example.item.ProfessionChestRecipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Đăng ký Recipe Serializer cho 4 loại Rương Nghề Nghiệp.
 * Mỗi serializer tương ứng với 1 loại recipe JSON:
 *   "type": "datnc_mod:farmer_chest"
 */
public class ModRecipes {

    public static RecipeSerializer<ProfessionChestRecipe.FarmerChestRecipe> FARMER_CHEST_RECIPE;
    public static RecipeSerializer<ProfessionChestRecipe.MinerChestRecipe> MINER_CHEST_RECIPE;
    public static RecipeSerializer<ProfessionChestRecipe.LumberjackChestRecipe> LUMBERJACK_CHEST_RECIPE;
    public static RecipeSerializer<ProfessionChestRecipe.FishermanChestRecipe> FISHERMAN_CHEST_RECIPE;
    public static RecipeSerializer<ProfessionChestRecipe.RancherChestRecipe> RANCHER_CHEST_RECIPE;
    public static RecipeSerializer<ProfessionChestRecipe.CookChestRecipe> COOK_CHEST_RECIPE;

    public static void register() {
        FARMER_CHEST_RECIPE = Registry.register(
                Registries.RECIPE_SERIALIZER,
                new Identifier(ExampleMod.MODID, "farmer_chest"),
                new SpecialRecipeSerializer<>(ProfessionChestRecipe.FarmerChestRecipe::new)
        );

        MINER_CHEST_RECIPE = Registry.register(
                Registries.RECIPE_SERIALIZER,
                new Identifier(ExampleMod.MODID, "miner_chest"),
                new SpecialRecipeSerializer<>(ProfessionChestRecipe.MinerChestRecipe::new)
        );

        LUMBERJACK_CHEST_RECIPE = Registry.register(
                Registries.RECIPE_SERIALIZER,
                new Identifier(ExampleMod.MODID, "lumberjack_chest"),
                new SpecialRecipeSerializer<>(ProfessionChestRecipe.LumberjackChestRecipe::new)
        );

        FISHERMAN_CHEST_RECIPE = Registry.register(
                Registries.RECIPE_SERIALIZER,
                new Identifier(ExampleMod.MODID, "fisherman_chest"),
                new SpecialRecipeSerializer<>(ProfessionChestRecipe.FishermanChestRecipe::new)
        );

        RANCHER_CHEST_RECIPE = Registry.register(
                Registries.RECIPE_SERIALIZER,
                new Identifier(ExampleMod.MODID, "rancher_chest"),
                new SpecialRecipeSerializer<>(ProfessionChestRecipe.RancherChestRecipe::new)
        );

        COOK_CHEST_RECIPE = Registry.register(
                Registries.RECIPE_SERIALIZER,
                new Identifier(ExampleMod.MODID, "cook_chest"),
                new SpecialRecipeSerializer<>(ProfessionChestRecipe.CookChestRecipe::new)
        );
    }
}
