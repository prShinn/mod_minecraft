package com.example.block;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Block "Mỏ Quặng" — Custom ore vein block được ghép bằng crafting.
 * Độ bền khai thác được quản lý bởi OreVeinBlockEntity.
 */
public class OreVeinBlock extends BlockWithEntity {

    public OreVeinBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new OreVeinBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        // BlockWithEntity mặc định là INVISIBLE, cần đổi thành MODEL để nó hiển thị texture
        return BlockRenderType.MODEL;
    }
}
