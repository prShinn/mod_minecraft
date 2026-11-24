package com.example.block;

import com.example.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/** BlockEntity cho Rương Nông dân. */
public class FarmerChestBlockEntity extends ProfessionChestBlockEntity {
    public FarmerChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FARMER_CHEST_BE, pos, state);
    }
}
