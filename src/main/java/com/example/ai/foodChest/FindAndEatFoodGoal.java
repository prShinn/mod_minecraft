package com.example.ai.foodChest;


import com.example.helper.FoodChestHelper;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.EnumSet;

public class FindAndEatFoodGoal extends Goal {
    private final PathAwareEntity npc;
    private final SimpleInventory npcInventory;
    private final int searchRadius;

    private BlockPos targetFoodChest = null;
    private int cooldown = 0;
    private static final int SEARCH_COOLDOWN = 100; // Search mỗi 5 giây nếu không có cache
    private static final int FOOD_THRESHOLD = 5; // Npc sẽ lấy food cho đến khi có này

    // ===== CACHING =====
    private final FoodChestCacheManager cacheManager = new FoodChestCacheManager();

    public FindAndEatFoodGoal(PathAwareEntity npc, SimpleInventory npcInventory, int searchRadius) {
        this.npc = npc;
        this.npcInventory = npcInventory;
        this.searchRadius = searchRadius;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        // ===== CONDITION 1: Inventory đủ food? =====
        if (hasEnoughFood()) {
            targetFoodChest = null;
            return false;
        }

        // ===== CONDITION 2: Có cached chest hợp lệ? =====
        BlockPos cachedChest = cacheManager.getCachedFoodChest(npc);
        if (cachedChest != null) {
            // ✅ Chest còn hợp lệ → dùng cache
            targetFoodChest = cachedChest;
            return true;
        }

        // ===== CONDITION 3: Cooldown (tránh spam search) =====
        if (cooldown > 0) {
            cooldown--;
            return false;
        }

        // ===== CONDITION 4: Scan chest mới =====
        targetFoodChest = findNearestFoodChest();
        cooldown = SEARCH_COOLDOWN;

        if (targetFoodChest != null) {
            // ✅ Tìm thấy → cache nó
            cacheManager.cacheChestPosition(targetFoodChest, npc.getWorld());
            return true;
        }

        return false;
    }

    @Override
    public void start() {
        if (targetFoodChest == null) return;

        npc.getNavigation().startMovingTo(
                targetFoodChest.getX() + 0.5,
                targetFoodChest.getY() + 0.5,
                targetFoodChest.getZ() + 0.5,
                1.0
        );
    }

    @Override
    public void tick() {
        if (targetFoodChest == null) return;

        World world = npc.getWorld();
        if (world == null) return;

        double dist = npc.squaredDistanceTo(
                targetFoodChest.getX() + 0.5,
                targetFoodChest.getY() + 0.5,
                targetFoodChest.getZ() + 0.5
        );

        // ===== CHECK: Quá xa chest =====
        if (dist > searchRadius * searchRadius) {
            targetFoodChest = null;
            return;
        }

        // ===== CHECK: Reached chest =====
        if (dist < 1.5) {
            // Check xem chest còn valid không
            if (!FoodChestHelper.isValidFoodChest(targetFoodChest, world)) {
                // Chest bị phá → reset cache
                cacheManager.invalidateCache();
                targetFoodChest = null;
                return;
            }

            // Lấy food từ chest
            ItemStack food = FoodChestHelper.takeFood(targetFoodChest, world);
            if (!food.isEmpty() && npcInventory != null) {
                npcInventory.addStack(food);

                // Nếu inventory đủ → stop
                if (hasEnoughFood()) {
                    targetFoodChest = null;
                }
            } else {
                // Chest hết food → không reset cache, có thể có food sau
                targetFoodChest = null;
            }
        }
    }

    @Override
    public boolean shouldContinue() {
        if (hasEnoughFood()) {
            return false;
        }

        if (targetFoodChest == null) {
            return false;
        }

        double dist = npc.squaredDistanceTo(
                targetFoodChest.getX() + 0.5,
                targetFoodChest.getY() + 0.5,
                targetFoodChest.getZ() + 0.5
        );

        return dist <= searchRadius * searchRadius;
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        // ⚠️ Không reset targetFoodChest - cho tick() xử lý
    }

    // ===== HELPER METHODS =====

    /**
     * Check xem NPC đã có đủ food không
     */
    private boolean hasEnoughFood() {
        int foodCount = getTotalFoodCount();
        return foodCount >= FOOD_THRESHOLD;
    }

    /**
     * Tính tổng food trong NPC inventory
     */
    private int getTotalFoodCount() {
        if(npcInventory == null) return 0;
        int total = 0;
        for (int i = 0; i < npcInventory.size(); i++) {
            ItemStack stack = npcInventory.getStack(i);
            if (!stack.isEmpty() && stack.isFood()) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Scan tìm vanilla FoodChest gần nhất
     * ✅ Chỉ scan khi cache invalid hoặc timeout
     */
    private BlockPos findNearestFoodChest() {
        BlockPos npcPos = npc.getBlockPos();
        World world = npc.getWorld();
        if (world == null) return null;

        double closestDist = Double.MAX_VALUE;
        BlockPos closestChest = null;

        // Scan radius
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    BlockPos checkPos = npcPos.add(dx, dy, dz);

                    if (!world.isChunkLoaded(checkPos)) continue;

                    // Check xem là FoodChest không
                    if (!FoodChestHelper.isFoodChest(checkPos, world)) {
                        continue;
                    }

                    // Check có food không
                    if (!FoodChestHelper.hasFood(checkPos, world)) {
                        continue;
                    }

                    double dist = npcPos.getSquaredDistance(checkPos);
                    if (dist < closestDist) {
                        closestDist = dist;
                        closestChest = checkPos;
                    }
                }
            }
        }

        return closestChest;
    }

    /**
     * Get cache info (cho debug logging)
     */
    public String getCacheInfo() {
        return cacheManager.getCacheInfo();
    }
}