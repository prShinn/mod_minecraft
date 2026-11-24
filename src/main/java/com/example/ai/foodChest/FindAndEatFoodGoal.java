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
    private final java.util.function.Supplier<SimpleInventory> inventory;
    private final int searchRadius;
    private final java.util.function.BooleanSupplier hungry;
    private final java.util.function.IntConsumer feed;

    private BlockPos targetFoodChest = null;
    private int cooldown = 0;
    private static final int SEARCH_COOLDOWN = 100; // Search mỗi 5 giây nếu không có cache
    private static final int FOOD_THRESHOLD = 1; // Npc sẽ lấy food cho đến khi có này

    // ===== CACHING =====
    private final FoodChestCacheManager cacheManager = new FoodChestCacheManager();

    public FindAndEatFoodGoal(PathAwareEntity npc, java.util.function.Supplier<SimpleInventory> inventory, int searchRadius,
                              java.util.function.BooleanSupplier hungry, java.util.function.IntConsumer feed) {
        this.npc = npc;
        this.inventory = inventory;
        this.searchRadius = searchRadius;
        this.hungry = hungry;
        this.feed = feed;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        // ===== CONDITION 1: Inventory đủ food? =====
        if (npc.getPose() == net.minecraft.entity.EntityPose.SLEEPING || !hungry.getAsBoolean() || hasEnoughFood()) {
            targetFoodChest = null;
            return false;
        }

        if (cooldown > 0) {
            cooldown--;
            return false;
        }

        // ===== CONDITION 2: Có cached chest hợp lệ? =====
        BlockPos cachedChest = cacheManager.getCachedFoodChest(npc);
        if (cachedChest != null) {
            // ✅ Chest còn hợp lệ → dùng cache
            targetFoodChest = cachedChest;
            return true;
        }

        // ===== CONDITION 4: Scan chest mới =====
        targetFoodChest = findNearestFoodChest();
        cooldown = SEARCH_COOLDOWN;

        if (targetFoodChest != null) {
            // ✅ Tìm thấy → cache nó
            cacheManager.cacheChestPosition(targetFoodChest, npc.getWorld());
            return true;
        }

        com.example.entity.base.NpcDisplayComponent.reportWorker(npc, "Không tìm thấy rương thức ăn còn đồ");
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
        if (world == null || world.isClient()) return;
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
        if (dist <= 6.25) {
            // Check xem chest còn valid không
            if (!FoodChestHelper.isValidFoodChest(targetFoodChest, world)) {
                // Chest bị phá → reset cache
                cacheManager.invalidateCache();
                targetFoodChest = null;
                return;
            }

            ItemStack food = FoodChestHelper.takeFood(targetFoodChest, world);
            if (!food.isEmpty()) {
                npc.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
                feed.accept(food.getItem().getFoodComponent().getHunger());
            }
            targetFoodChest = null;
        } else if (npc.getNavigation().isIdle()) {
            cacheManager.invalidateCache();
            targetFoodChest = null;
        }
    }

    @Override
    public boolean shouldContinue() {
        if (npc.getPose() == net.minecraft.entity.EntityPose.SLEEPING || !hungry.getAsBoolean() || hasEnoughFood()) {
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
        targetFoodChest = null;
        cooldown = SEARCH_COOLDOWN;
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
        SimpleInventory npcInventory = inventory.get();
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