package com.example.item;

import com.example.registry.ModBlocks;
import com.example.registry.ModRecipes;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

/**
 * Custom recipe kiểm tra:
 * 1. Đúng công thức: 1 Chest + 1 Nông cụ tương ứng.
 * 2. Nông cụ phải còn NGUYÊN VẸN (damage == 0).
 * 3. Output: Block rương nghề tương ứng.
 * 4. Công cụ được lưu vào NBT của output để trả lại khi phá rương.
 *
 * Dùng SpecialCraftingRecipe để có thể kiểm tra NBT/damage,
 * thứ mà recipe JSON thông thường không hỗ trợ.
 */
public abstract class ProfessionChestRecipe extends SpecialCraftingRecipe {

    public ProfessionChestRecipe(Identifier id, CraftingRecipeCategory category) {
        super(id, category);
    }

    /** Override trong subclass để kiểm tra loại nông cụ phù hợp. */
    protected abstract boolean isMatchingTool(ItemStack stack);

    /** Override trong subclass để trả về block output tương ứng. */
    protected abstract ItemStack createOutput();

    /** Nguyên liệu công cụ hiển thị trong sách công thức / REI / JEI. */
    protected abstract Ingredient getToolIngredient();

    @Override
    public boolean isIgnoredInRecipeBook() {
        return false;
    }

    @Override
    public DefaultedList<Ingredient> getIngredients() {
        return DefaultedList.copyOf(Ingredient.EMPTY, Ingredient.ofItems(Items.CHEST), getToolIngredient());
    }

    @Override
    public boolean matches(RecipeInputInventory inventory, World world) {
        boolean hasChest = false;
        boolean hasFullDurabilityTool = false;
        int nonEmptyCount = 0;

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;
            nonEmptyCount++;

            if (stack.isOf(Items.CHEST)) {
                hasChest = true;
            } else if (isMatchingTool(stack)) {
                // Kiểm tra full độ bền: damage == 0 (nguyên vẹn)
                if (stack.getDamage() == 0) {
                    hasFullDurabilityTool = true;
                }
            }
        }

        return nonEmptyCount == 2 && hasChest && hasFullDurabilityTool;
    }

    @Override
    public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registryManager) {
        ItemStack output = createOutput();

        // Lưu công cụ vào NBT của output để block entity trả lại khi phá
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && isMatchingTool(stack)) {
                NbtCompound nbt = output.getOrCreateNbt();
                nbt.put("StoredTool", stack.writeNbt(new NbtCompound()));
                break;
            }
        }

        return output;
    }

    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public ItemStack getOutput(DynamicRegistryManager registryManager) {
        return createOutput();
    }

    // ==================== SUBCLASSES ====================

    /**
     * Rương Nông dân: Chest + Cuốc (Hoe) bất kỳ, full độ bền.
     */
    public static class FarmerChestRecipe extends ProfessionChestRecipe {
        public FarmerChestRecipe(Identifier id, CraftingRecipeCategory category) {
            super(id, category);
        }

        @Override
        protected boolean isMatchingTool(ItemStack stack) {
            return stack.getItem() instanceof HoeItem;
        }

        @Override
        protected ItemStack createOutput() {
            return new ItemStack(ModBlocks.FARMER_CHEST);
        }

        @Override
        protected Ingredient getToolIngredient() {
            return Ingredient.fromTag(ItemTags.HOES);
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return ModRecipes.FARMER_CHEST_RECIPE;
        }
    }

    /**
     * Rương Thợ mỏ: Chest + Cuốc chim (Pickaxe) bất kỳ, full độ bền.
     */
    public static class MinerChestRecipe extends ProfessionChestRecipe {
        public MinerChestRecipe(Identifier id, CraftingRecipeCategory category) {
            super(id, category);
        }

        @Override
        protected boolean isMatchingTool(ItemStack stack) {
            return stack.getItem() instanceof PickaxeItem;
        }

        @Override
        protected ItemStack createOutput() {
            return new ItemStack(ModBlocks.MINER_CHEST);
        }

        @Override
        protected Ingredient getToolIngredient() {
            return Ingredient.fromTag(ItemTags.PICKAXES);
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return ModRecipes.MINER_CHEST_RECIPE;
        }
    }

    /**
     * Rương Tiều phu: Chest + Rìu (Axe) bất kỳ, full độ bền.
     */
    public static class LumberjackChestRecipe extends ProfessionChestRecipe {
        public LumberjackChestRecipe(Identifier id, CraftingRecipeCategory category) {
            super(id, category);
        }

        @Override
        protected boolean isMatchingTool(ItemStack stack) {
            return stack.getItem() instanceof AxeItem;
        }

        @Override
        protected ItemStack createOutput() {
            return new ItemStack(ModBlocks.LUMBERJACK_CHEST);
        }

        @Override
        protected Ingredient getToolIngredient() {
            return Ingredient.fromTag(ItemTags.AXES);
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return ModRecipes.LUMBERJACK_CHEST_RECIPE;
        }
    }

    /**
     * Rương Ngư dân: Chest + Cần câu (Fishing Rod), full độ bền.
     */
    public static class FishermanChestRecipe extends ProfessionChestRecipe {
        public FishermanChestRecipe(Identifier id, CraftingRecipeCategory category) {
            super(id, category);
        }

        @Override
        protected boolean isMatchingTool(ItemStack stack) {
            return stack.isOf(Items.FISHING_ROD);
        }

        @Override
        protected ItemStack createOutput() {
            return new ItemStack(ModBlocks.FISHERMAN_CHEST);
        }

        @Override
        protected Ingredient getToolIngredient() {
            return Ingredient.ofItems(Items.FISHING_ROD);
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return ModRecipes.FISHERMAN_CHEST_RECIPE;
        }
    }
}
