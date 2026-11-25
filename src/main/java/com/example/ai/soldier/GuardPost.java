package com.example.ai.soldier;

import com.example.block.ProfessionChestBlockEntity;
import com.example.entity.SoldierNPCEntity;
import com.example.registry.ModBlocks;
import net.minecraft.block.entity.BedBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Điểm neo canh gác của Binh lính khi không đi theo chủ:
 * Rương binh lính của chủ > giường > rương nghề của chủ, gần nhất trong {@link #SEARCH_RADIUS} block.
 * Duyệt danh sách block entity của chunk (không quét từng block) nên chi phí thấp.
 */
public final class GuardPost {
    public static final int SEARCH_RADIUS = 40;
    private static final int SEARCH_HEIGHT = 16;
    /** Bán kính lính được đi lang thang quanh điểm neo. */
    public static final double WANDER_RADIUS = 12.0;

    private GuardPost() {
    }

    @Nullable
    public static BlockPos find(SoldierNPCEntity soldier, BlockPos center) {
        World world = soldier.getWorld();
        double maxSq = (double) SEARCH_RADIUS * SEARCH_RADIUS;
        BlockPos soldierChest = null, bed = null, workChest = null;
        double soldierChestSq = maxSq, bedSq = maxSq, workChestSq = maxSq;

        for (int cx = (center.getX() - SEARCH_RADIUS) >> 4; cx <= (center.getX() + SEARCH_RADIUS) >> 4; cx++) {
            for (int cz = (center.getZ() - SEARCH_RADIUS) >> 4; cz <= (center.getZ() + SEARCH_RADIUS) >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                for (BlockEntity be : world.getChunk(cx, cz).getBlockEntities().values()) {
                    BlockPos pos = be.getPos();
                    if (Math.abs(pos.getY() - center.getY()) > SEARCH_HEIGHT) continue;
                    double distSq = pos.getSquaredDistance(center);
                    if (distSq > maxSq) continue;
                    if (be instanceof ProfessionChestBlockEntity box && box.isAccessibleBy(soldier.getOwnerUuid())) {
                        if (be.getCachedState().isOf(ModBlocks.SOLDIER_CHEST)) {
                            if (distSq < soldierChestSq) {
                                soldierChestSq = distSq;
                                soldierChest = pos.toImmutable();
                            }
                        } else if (distSq < workChestSq) {
                            workChestSq = distSq;
                            workChest = pos.toImmutable();
                        }
                    } else if (be instanceof BedBlockEntity && distSq < bedSq) {
                        bedSq = distSq;
                        bed = pos.toImmutable();
                    }
                }
            }
        }
        if (soldierChest != null) return soldierChest;
        return bed != null ? bed : workChest;
    }
}
