package com.example.block;

import com.example.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/** BlockEntity cho Rương Thợ mỏ. */
public class MinerChestBlockEntity extends ProfessionChestBlockEntity {
    public MinerChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MINER_CHEST_BE, pos, state);
    }
}
