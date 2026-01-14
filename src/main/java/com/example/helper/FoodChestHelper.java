package com.example.helper;

import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class FoodChestHelper {
    public static final String FOOD_CHEST_MARKER_NAME = "FOOD CHEST";

    public static boolean isFoodChest(BlockPos pos, World world) {
        if (world == null || world.isClient) return false;

        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) return false;

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof ChestBlockEntity chest)) return false;

        // Check slot 0 có marker item
        ItemStack slotZero = chest.getStack(0);
        return isFoodChestMarker(slotZero);
    }

    /**
     * Check xem ItemStack là marker
     */
    private static boolean isFoodChestMarker(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // Check custom name
        if (stack.hasCustomName()) {
            String name = stack.getName().getString();
            // Kiểm tra xem tên có chứa "FOOD CHEST" không
            return name.contains("FOOD CHEST");
        }

        // Hoặc check NBT tag
        NbtCompound nbt = stack.getNbt();
        if (nbt != null && nbt.getBoolean("IsFoodChestMarker")) {
            return true;
        }

        return false;
    }

    /**
     * Tạo marker item
     * Có thể gửi cho player qua recipe hoặc command
     */
    public static ItemStack createFoodChestMarker() {
        // Dùng WRITTEN_BOOK làm marker
        ItemStack marker = new ItemStack(net.minecraft.item.Items.WRITTEN_BOOK);

        // Set custom name
        marker.setCustomName(
                net.minecraft.text.Text.literal("§c§lFOOD CHEST§r")
                        .formatted(net.minecraft.util.Formatting.RED, net.minecraft.util.Formatting.BOLD)
        );

        // Set NBT tag
        NbtCompound nbt = marker.getOrCreateNbt();
        nbt.putBoolean("IsFoodChestMarker", true);
        nbt.putString("author", "Npc");
        nbt.putString("title", "Food Chest Marker");

        return marker;
    }

    /**
     * Get inventory từ vanilla chest (bỏ qua slot 0 - marker)
     */
    public static Inventory getFoodChestInventory(BlockPos pos, World world) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof ChestBlockEntity chest) {
            return chest;
        }
        return null;
    }

    /**
     * Check xem chest có food không (bỏ qua slot 0)
     */
    public static boolean hasFood(BlockPos pos, World world) {
        Inventory inv = getFoodChestInventory(pos, world);
        if (inv == null) return false;

        // Bắt đầu từ slot 1 (slot 0 là marker)
        for (int i = 1; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && stack.isFood()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lấy 1 food từ chest (bỏ qua slot 0)
     */
    public static ItemStack takeFood(BlockPos pos, World world) {
        Inventory inv = getFoodChestInventory(pos, world);
        if (inv == null) return ItemStack.EMPTY;

        // Bắt đầu từ slot 1
        for (int i = 1; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && stack.isFood()) {
                return inv.removeStack(i, 1);
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Check xem chest vẫn tồn tại & là FoodChest
     */
    public static boolean isValidFoodChest(BlockPos pos, World world) {
        if (world == null) return false;
        if (!world.isChunkLoaded(pos)) return false;

        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) return false;

        return isFoodChest(pos, world);
    }

    /**
     * Tính tổng food trong chest (bỏ qua slot 0)
     */
    public static int getTotalFoodCount(BlockPos pos, World world) {
        Inventory inv = getFoodChestInventory(pos, world);
        if (inv == null) return 0;

        int total = 0;
        // Bắt đầu từ slot 1
        for (int i = 1; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && stack.isFood()) {
                total += stack.getCount();
            }
        }
        return total;
    }
}