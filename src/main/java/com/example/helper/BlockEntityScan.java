package com.example.helper;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Tìm block entity (rương, giường...) trong một hộp quanh tâm bằng cách duyệt danh sách block entity
 * của các chunk đã nạp — thay cho quét từng block (hộp 49×11×49 = 26 nghìn lần đọc block → vài chục phần tử).
 * Không nạp chunk mới.
 */
public final class BlockEntityScan {
    private BlockEntityScan() {
    }

    /** Block entity có |dx| ≤ radius, |dz| ≤ radius, |dy| ≤ height so với tâm. */
    public static List<BlockEntity> inBox(World world, BlockPos center, int radius, int height) {
        List<BlockEntity> result = new ArrayList<>();
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                for (BlockEntity be : world.getChunk(cx, cz).getBlockEntities().values()) {
                    BlockPos pos = be.getPos();
                    if (Math.abs(pos.getX() - center.getX()) <= radius && Math.abs(pos.getZ() - center.getZ()) <= radius
                            && Math.abs(pos.getY() - center.getY()) <= height && !be.isRemoved()) {
                        result.add(be);
                    }
                }
            }
        }
        return result;
    }
}
