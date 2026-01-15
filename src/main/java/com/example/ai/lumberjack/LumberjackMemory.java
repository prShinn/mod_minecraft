package com.example.ai.lumberjack;

import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

public class LumberjackMemory {

    public BlockPos lastChestPos;
    public BlockPos lastTreePos;
    public Set<BlockPos> remainingTreeBlocks;  // ✅ Blocks còn lại cần chặt
    public int idleTicks = 0;
    public boolean isChoppingTree = false;  // ✅ Flag: đang chặt cây?

    public LumberjackMemory() {
        remainingTreeBlocks = new HashSet<>();
    }
    public void resetIdle() {
        idleTicks = 0;
    }

    public void tickIdle() {
        idleTicks++;
    }

    public boolean shouldWander() {
        return idleTicks > 20 * 15; // 15 giây không làm gì thì đi lang thang
    }
    public void startChoppingTree(BlockPos tree, Set<BlockPos> blocks) {
        this.lastTreePos = tree;
        this.remainingTreeBlocks = new HashSet<>(blocks);
        this.isChoppingTree = true;
    }

    public void updateRemainingBlocks(Set<BlockPos> blocks) {
        this.remainingTreeBlocks = new HashSet<>(blocks);
    }

    public void finishChoppingTree() {
        this.lastTreePos = null;
        this.remainingTreeBlocks.clear();
        this.isChoppingTree = false;
    }

    public boolean hasUnfinishedTree() {
        return isChoppingTree && lastTreePos != null && !remainingTreeBlocks.isEmpty();
    }

}