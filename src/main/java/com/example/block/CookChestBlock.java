package com.example.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Rương Đầu bếp — chỉ NPC Đầu bếp mới nhận ra. */
public class CookChestBlock extends ProfessionChestBlock {
    public CookChestBlock(Settings settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CookChestBlockEntity(pos, state);
    }
}
