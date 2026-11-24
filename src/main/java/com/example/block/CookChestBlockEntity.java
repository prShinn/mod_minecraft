package com.example.block;

import com.example.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/** BlockEntity cho Rương Đầu bếp. */
public class CookChestBlockEntity extends ProfessionChestBlockEntity {
    public CookChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COOK_CHEST_BE, pos, state);
    }
}
