package com.example.block;

import com.example.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/** BlockEntity cho Rương Thợ xây. */
public class BuilderChestBlockEntity extends ProfessionChestBlockEntity {
    public BuilderChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BUILDER_CHEST_BE, pos, state);
    }
}
