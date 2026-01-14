package com.example.ai.lumberjack;

import net.minecraft.util.math.BlockPos;

public class LumberjackMemory {

    public BlockPos lastChestPos;
    public BlockPos lastTreePos;
    public int idleTicks = 0;

    public void resetIdle() {
        idleTicks = 0;
    }

    public void tickIdle() {
        idleTicks++;
    }

    public boolean shouldWander() {
        return idleTicks > 20 * 15; // 15 giây không làm gì thì đi lang thang
    }
}