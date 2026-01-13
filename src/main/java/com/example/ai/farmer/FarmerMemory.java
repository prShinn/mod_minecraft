package com.example.ai.farmer;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class FarmerMemory {

    public BlockPos lastChestPos;
    public BlockPos lastFarmPos;
    public BlockPos bedPos;
    public BlockPos reservedBed;

    public int idleTicks = 0;
    public boolean sleeping = false;

    public void resetIdle() {
        idleTicks = 0;
    }

    public void tickIdle() {
        idleTicks++;
    }

    public boolean shouldReturnToFarm() {
        return idleTicks > 20 * 30; // 30s
    }

    public boolean isNight(World world) {
        long time = world.getTimeOfDay() % 24000;
        return time >= 13000 && time <= 23000;
    }

}

