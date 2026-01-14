package com.example.ai.foodChest;

import com.example.helper.FoodChestHelper;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;


public class FoodChestCacheManager {
    private BlockPos cachedChestPos = null;
    private long lastScanTime = 0;

    // ===== CONFIGURATIONS =====
    private static final long CACHE_TIMEOUT = 24000L; // 1 ngày game (20 ticks/s * 1200 = 24000)
    private static final int MAX_SEARCH_RADIUS = 32; // Scan radius when needed

    /**
     * Get cached food chest (nếu vẫn valid)
     * @return BlockPos hoặc null
     */
    public BlockPos getCachedFoodChest(PathAwareEntity npc) {
        // ===== CHECK 1: Có cached position? =====
        if (cachedChestPos == null) {
            return null;
        }

        World world = npc.getWorld();
        if (world == null) return null;

        // ===== CHECK 2: Chest vẫn tồn tại & là FoodChest? =====
        if (!FoodChestHelper.isValidFoodChest(cachedChestPos, world)) {
            // Chest bị phá → reset cache
            invalidateCache();
            return null;
        }

        // ===== CHECK 3: Có timeout? (1 ngày game) =====
        long currentTime = world.getTime();
        if (currentTime - lastScanTime > CACHE_TIMEOUT) {
            // Đã quá 1 ngày → reset cache để re-scan
            invalidateCache();
            return null;
        }

        // ===== CHECK 4: Chest còn food? =====
        if (!FoodChestHelper.hasFood(cachedChestPos, world)) {
            // Chest không có food → không cần re-scan, chỉ return null
            return null;
        }

        // ✅ Cache hợp lệ
        return cachedChestPos;
    }

    /**
     * Cache chest position sau khi tìm thấy
     */
    public void cacheChestPosition(BlockPos pos, World world) {
        this.cachedChestPos = pos;
        this.lastScanTime = world.getTime();
    }

    /**
     * Invalidate cache (khi chest bị phá hoặc timeout)
     */
    public void invalidateCache() {
        this.cachedChestPos = null;
        this.lastScanTime = 0;
    }

    /**
     * Get cache info (cho debug)
     */
    public String getCacheInfo() {
        if (cachedChestPos == null) {
            return "No cached chest";
        }
        return String.format("Cached: [%d, %d, %d]", cachedChestPos.getX(), cachedChestPos.getY(), cachedChestPos.getZ());
    }

    /**
     * Check xem cache có expired chưa
     */
    public boolean isCacheExpired(World world) {
        if (cachedChestPos == null) return true;
        if (world == null) return true;
        return world.getTime() - lastScanTime > CACHE_TIMEOUT;
    }

    /**
     * Get cached position (dù invalid hay không - cho navigation)
     */
    public BlockPos getRawCachedPos() {
        return cachedChestPos;
    }
}