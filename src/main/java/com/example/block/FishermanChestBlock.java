package com.example.block;

import com.example.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Rương Ngư dân — chỉ NPC Ngư dân (Fisherman) mới nhận ra. */
public class FishermanChestBlock extends ProfessionChestBlock {
    public FishermanChestBlock(Settings settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new FishermanChestBlockEntity(pos, state);
    }
}
