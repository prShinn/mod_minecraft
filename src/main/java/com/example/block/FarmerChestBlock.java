package com.example.block;

import com.example.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Rương Nông dân — chỉ NPC Nông dân (Farmer) mới nhận ra. */
public class FarmerChestBlock extends ProfessionChestBlock {
    public FarmerChestBlock(Settings settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new FarmerChestBlockEntity(pos, state);
    }
}
