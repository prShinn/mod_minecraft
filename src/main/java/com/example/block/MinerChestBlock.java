package com.example.block;

import com.example.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Rương Thợ mỏ — chỉ NPC Thợ mỏ (Miner) mới nhận ra. */
public class MinerChestBlock extends ProfessionChestBlock {
    public MinerChestBlock(Settings settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MinerChestBlockEntity(pos, state);
    }
}
