package com.example.block;

import com.example.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/** BlockEntity cho Rương Thợ chăn nuôi. */
public class RancherChestBlockEntity extends ProfessionChestBlockEntity {
    public RancherChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RANCHER_CHEST_BE, pos, state);
    }
}
