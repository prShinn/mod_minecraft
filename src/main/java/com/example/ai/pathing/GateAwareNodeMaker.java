package com.example.ai.pathing;

import net.minecraft.block.BlockState;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

/**
 * Bộ tìm đường cho NPC nghề: cổng rào gỗ đang đóng được coi như cửa gỗ đóng
 * (vanilla coi là tường) → khi bật mở cửa, NPC tính đường xuyên qua cổng và {@link DoorGateOpener} mở/đóng giúp.
 */
public class GateAwareNodeMaker extends LandPathNodeMaker {
    private final boolean openGates;

    public GateAwareNodeMaker(boolean openGates) {
        this.openGates = openGates;
    }

    @Override
    public PathNodeType getDefaultNodeType(BlockView world, int x, int y, int z) {
        if (openGates) {
            BlockState state = world.getBlockState(new BlockPos(x, y, z));
            if (state.getBlock() instanceof FenceGateBlock && !state.get(FenceGateBlock.OPEN)) {
                return PathNodeType.DOOR_WOOD_CLOSED;
            }
        }
        return super.getDefaultNodeType(world, x, y, z);
    }
}
