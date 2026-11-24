package com.example.ai.miner;

import net.minecraft.util.math.BlockPos;

public class MinerMemory {
    public BlockPos lastOrePos;   // Vị trí ore vein gần nhất đã đào
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
