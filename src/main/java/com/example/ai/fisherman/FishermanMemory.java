package com.example.ai.fisherman;

import net.minecraft.util.math.BlockPos;

public class FishermanMemory {
    public BlockPos lastChestPos;
    public BlockPos fishingSpot;   // Vị trí nước 3x3 để câu cá
    public boolean needsToDeposit = false;
    public int idleTicks = 0;

    public void resetIdle() {
        idleTicks = 0;
    }

    public void tickIdle() {
        idleTicks++;
    }

    public boolean shouldWander() {
        // 15 giây không làm gì thì đi lang thang
        return idleTicks > 20 * 15;
    }
}
