package com.example.build;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

import java.util.function.Predicate;

/**
 * Loại vật liệu của công trình. Thợ xây nhận bất kỳ item khớp loại (vd mọi loại ván gỗ cho PLANKS)
 * và đặt đúng block của item thật lấy từ rương.
 */
public enum BuildMaterial {
    PLANKS("Gỗ", stack -> stack.isIn(ItemTags.PLANKS)),
    LOGS("Thân gỗ", stack -> stack.isIn(ItemTags.LOGS)),
    COBBLE("Đá", stack -> stack.isOf(Items.COBBLESTONE)),
    BRICKS("Đá mịn", stack -> stack.isOf(Items.STONE_BRICKS)),
    GLASS("Kính", stack -> stack.isOf(Items.GLASS));

    private final String displayName;
    private final Predicate<ItemStack> matcher;

    BuildMaterial(String displayName, Predicate<ItemStack> matcher) {
        this.displayName = displayName;
        this.matcher = matcher;
    }

    public String displayName() {
        return displayName;
    }

    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && matcher.test(stack);
    }

    /** Loại vật liệu của stack, null nếu không phải vật liệu xây. */
    public static BuildMaterial of(ItemStack stack) {
        for (BuildMaterial material : values()) {
            if (material.matches(stack)) return material;
        }
        return null;
    }

    /** Trạng thái block sẽ đặt từ item này, null nếu item không có block. */
    public static BlockState stateFor(ItemStack stack) {
        Item item = stack.getItem();
        Block block = Block.getBlockFromItem(item);
        return block == Blocks.AIR ? null : block.getDefaultState();
    }
}
