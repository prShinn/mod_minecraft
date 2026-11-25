package com.example.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Rương Thợ xây — chứa vật liệu cho công trình; chỉ NPC Thợ xây nhận ra. */
public class BuilderChestBlock extends ProfessionChestBlock {
    public BuilderChestBlock(Settings settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BuilderChestBlockEntity(pos, state);
    }
}
