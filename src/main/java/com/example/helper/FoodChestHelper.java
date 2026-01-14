package com.example.helper;

import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class FoodChestHelper {


    public static boolean isFoodChest(BlockPos pos, World world) {
        if (world == null || world.isClient) return false;

        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) return false;

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof ChestBlockEntity chest)) return false;

        // Check slot 0 có marker item
        ItemStack slotZero = chest.getStack(0);
        return slotZero.isOf(Items.BOOK);
    }

    public static Inventory getFoodChestInventory(BlockPos pos, World world) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof ChestBlockEntity chest) {
            return chest;
        }
        return null;
    }

    public static boolean hasFood(BlockPos pos, World world) {
        Inventory inv = getFoodChestInventory(pos, world);
        if (inv == null) return false;

        for (int i = 1; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && stack.isFood()) {
                return true;
            }
        }
        return false;
    }

    public static ItemStack takeFood(BlockPos pos, World world) {
        Inventory inv = getFoodChestInventory(pos, world);
        if (inv == null) return ItemStack.EMPTY;

        for (int i = 1; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && stack.isFood()) {
                return inv.removeStack(i, 1);
            }
        }
        return ItemStack.EMPTY;
    }

    public static boolean isValidFoodChest(BlockPos pos, World world) {
        if (world == null) return false;
        if (!world.isChunkLoaded(pos)) return false;

        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) return false;

        return isFoodChest(pos, world);
    }

}