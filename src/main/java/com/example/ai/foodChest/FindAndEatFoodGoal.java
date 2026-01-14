package com.example.ai.foodChest;


import com.example.entity.FarmerNpcEntity;
import com.example.entity.LumberjackNpcEntity;
import com.example.entity.base.NpcDisplayComponent;
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
    private int searchCooldown = 0;
    private int actionCooldown = 0;
    private static final int SEARCH_COOLDOWN = 100; // Search mỗi 5 giây nếu không có cache
    private static final int FOOD_THRESHOLD = 1; // Npc sẽ lấy food cho đến khi có này

    private final FoodChestCacheManager cacheManager = new FoodChestCacheManager();

    public FindAndEatFoodGoal(PathAwareEntity npc, SimpleInventory npcInventory, int searchRadius) {
        this.npc = npc;
        this.npcInventory = npcInventory;
        this.searchRadius = searchRadius;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (searchCooldown > 0) {
            searchCooldown--;
            return false;
        }

        if (hasEnoughFood()) {
            return false;
        }

        BlockPos cached = cacheManager.getCachedFoodChest(npc);
        if (cached != null) {
            targetFoodChest = cached;
            return true;
        }
        targetFoodChest = findNearestFoodChest();
        if (targetFoodChest != null) {
            cacheManager.cacheChestPosition(targetFoodChest, npc.getWorld());
            return true;
        } else {
            searchCooldown = SEARCH_COOLDOWN;
            return false;
        }
    }

    @Override
    public void start() {
        if (targetFoodChest == null) return;

        if (npc.getNavigation().isIdle() && targetFoodChest != null || npc.age % 40 == 0) {
            npc.getNavigation().startMovingTo(
                    targetFoodChest.getX() + 0.5,
                    targetFoodChest.getY() + 0.5,
                    targetFoodChest.getZ() + 0.5,
                    1.3f
            );
        }

    }

    @Override
    public void tick() {
        if (targetFoodChest == null) return;

        World world = npc.getWorld();
        if (world == null || world.isClient()) return;
        if (actionCooldown > 0) {
            actionCooldown--;
            return;
        }

        double dist = npc.squaredDistanceTo(
                targetFoodChest.getX() + 0.5,
                targetFoodChest.getY() + 0.5,
                targetFoodChest.getZ() + 0.5
        );

        if (dist > searchRadius * searchRadius) {
            targetFoodChest = null;
            return;
        }

        if (dist < 3) {
            if (!FoodChestHelper.hasFood(targetFoodChest, world)) {
                targetFoodChest = null;
                return;
            }
            if (!FoodChestHelper.isValidFoodChest(targetFoodChest, world)) {
                cacheManager.invalidateCache();
                targetFoodChest = null;
                return;
            }

            ItemStack food = FoodChestHelper.takeFood(targetFoodChest, world);
            if (!food.isEmpty() && npcInventory != null) {
                npcInventory.addStack(food);
                if (npc instanceof FarmerNpcEntity farmer) {
                    farmer.display.npcEatFood(npc, npcInventory);
                } else if (npc instanceof LumberjackNpcEntity lumberjack) {
                    lumberjack.display.npcEatFood(npc, npcInventory);
                }
                if (hasEnoughFood()) {
                    targetFoodChest = null;
                }
                actionCooldown = SEARCH_COOLDOWN;
            } else {
                targetFoodChest = null;
            }
        }
    }

    @Override
    public boolean shouldContinue() {
        if (targetFoodChest == null) {
            return false;
        }

        if (!FoodChestHelper.isValidFoodChest(targetFoodChest, npc.getWorld())) {
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
    }

    private boolean hasEnoughFood() {
        int foodCount = getTotalFoodCount();
        return foodCount >= FOOD_THRESHOLD;
    }

    private int getTotalFoodCount() {
        if (npcInventory == null) return 0;
        int total = 0;
        for (int i = 0; i < npcInventory.size(); i++) {
            ItemStack stack = npcInventory.getStack(i);
            if (!stack.isEmpty() && stack.isFood()) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private BlockPos findNearestFoodChest() {
        BlockPos npcPos = npc.getBlockPos();
        World world = npc.getWorld();
        if (world == null) return null;

        double closestDist = Double.MAX_VALUE;
        BlockPos closestChest = null;

        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    BlockPos checkPos = npcPos.add(dx, dy, dz);

                    if (!world.isChunkLoaded(checkPos)) continue;

                    if (!FoodChestHelper.isFoodChest(checkPos, world)) {
                        continue;
                    }

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

}