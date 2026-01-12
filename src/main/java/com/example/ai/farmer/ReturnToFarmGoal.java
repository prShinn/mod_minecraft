package com.example.ai.farmer;

import com.example.entity.FarmerNpcEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

public class ReturnToFarmGoal extends Goal {

    private final FarmerNpcEntity npc;
    private BlockPos targetPos;
    private int taskTicks;
    private static final int MAX_TASK_TICKS = 100;
    private static final double REACH_DIST_SQ = 2.25;
    public ReturnToFarmGoal(FarmerNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (!npc.memory.shouldReturnToFarm()) return false;

        BlockPos lastFarm = npc.memory.lastFarmPos;
        BlockPos lastChest = npc.memory.lastChestPos;

        if (lastFarm == null && lastChest == null) return false;

        targetPos = lastFarm != null ? lastFarm : lastChest;

        // chunk check
        return npc.getWorld().isChunkLoaded(targetPos);
    }

    @Override
    public void start() {
        taskTicks = 0;
        npc.getNavigation().startMovingTo(
                targetPos.getX() + 0.5,
                targetPos.getY(),
                targetPos.getZ() + 0.5,
                1.0
        );
        npc.memory.resetIdle();

    }
    @Override
    public void tick() {
        taskTicks++;

        if (taskTicks > MAX_TASK_TICKS || !npc.getWorld().isChunkLoaded(targetPos)) {
            stop();
            return;
        }

        if (npc.squaredDistanceTo(Vec3d.ofCenter(targetPos)) <= REACH_DIST_SQ) {
            stop(); // đã đến farm/chest
        }
    }
    @Override
    public boolean shouldContinue() {
        return targetPos != null && npc.getNavigation().isFollowingPath();
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        targetPos = null;
        taskTicks = 0;
    }
}
