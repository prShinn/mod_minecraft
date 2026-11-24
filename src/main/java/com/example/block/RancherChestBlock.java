package com.example.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Rương Thợ chăn nuôi — chỉ NPC Thợ chăn nuôi mới nhận ra. */
public class RancherChestBlock extends ProfessionChestBlock {
    public RancherChestBlock(Settings settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new RancherChestBlockEntity(pos, state);
    }
}
