package com.example.ai.farmer;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class FarmerMemory {

    public BlockPos lastChestPos;
    public BlockPos lastFarmPos;
    public BlockPos bedPos;

    public int idleTicks = 0;

    public void resetIdle() {
        idleTicks = 0;
    }

    public void tickIdle() {
        idleTicks++;
    }
    public boolean shouldReturnToFarm() {
        return idleTicks > 20 * 30; // 30s
    }


}

