package com.example.ai.foodChest;

import com.example.helper.FoodChestHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class GlobalFoodChestCache {
    private static GlobalFoodChestCache instance;
    private BlockPos cachedChestPos = null;
    private long lastScanTime = 0;
    private static final long CACHE_TIMEOUT = 24000L;

    private GlobalFoodChestCache() {}

    public static GlobalFoodChestCache getInstance() {
        if (instance == null) {
            instance = new GlobalFoodChestCache();
        }
        return instance;
    }

    public BlockPos getCachedFoodChest(World world) {
        if (cachedChestPos == null) {
            return null;
        }
        if (world.getTime() - lastScanTime > CACHE_TIMEOUT) {
            cachedChestPos = null;
            return null;
        }
        if (!FoodChestHelper.isValidFoodChest(cachedChestPos, world)) {
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