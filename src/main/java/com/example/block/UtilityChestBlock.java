package com.example.block;

import com.example.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public class UtilityChestBlock extends ProfessionChestBlock {
    public UtilityChestBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ProfessionChestBlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ProfessionChestBlockEntity(ModBlockEntities.UTILITY_CHEST, pos, state);
    }
}
