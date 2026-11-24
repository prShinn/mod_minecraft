package com.example.block;

import com.example.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/** BlockEntity cho Rương Ngư dân. */
public class FishermanChestBlockEntity extends ProfessionChestBlockEntity {
    public FishermanChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FISHERMAN_CHEST_BE, pos, state);
    }
}
