package com.example.ai.miner;

import net.minecraft.util.math.BlockPos;

public class MinerMemory {
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
