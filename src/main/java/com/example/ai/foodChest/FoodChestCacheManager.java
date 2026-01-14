package com.example.ai.foodChest;

import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;


public class FoodChestCacheManager {
    private BlockPos cachedChestPos = null;
    private long lastScanTime = 0;

    private static final long CACHE_TIMEOUT = 24000L; // 1 ngày game (20 ticks/s * 1200 = 24000)

    public BlockPos getCachedFoodChest(PathAwareEntity npc) {
        if (cachedChestPos == null) {
            return null;
        }
        long time = npc.getWorld().getTime();
        if (time - lastScanTime > CACHE_TIMEOUT) {
            cachedChestPos = null;
            return null;
        }
        return cachedChestPos;
    }

    public void cacheChestPosition(BlockPos pos, World world) {
        this.cachedChestPos = pos;
        this.lastScanTime = world.getTime();
    }

    public void invalidateCache() {
        this.cachedChestPos = null;
        this.lastScanTime = 0;
    }
}