package com.example.ai.worker;

import com.example.entity.base.WorkerNpcEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/** Rảnh 15 giây thì đi dạo, ưu tiên quay về gần rương nghề. */
public class WorkerWanderGoal extends Goal {
    private static final int IDLE_TICKS = 20 * 15;
    private static final double FAR_FROM_CHEST_SQ = 12 * 12;

    private final WorkerNpcEntity npc;
    private Vec3d target;

    public WorkerWanderGoal(WorkerNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Goal.Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (npc.sleeping.isSleeping() || npc.needsToDeposit() || !npc.isIdleFor(IDLE_TICKS)) return false;
        BlockPos chest = npc.getChestPos();
        target = chest != null && npc.squaredDistanceTo(Vec3d.ofCenter(chest)) > FAR_FROM_CHEST_SQ
                ? NoPenaltyTargeting.findTo(npc, 10, 5, Vec3d.ofCenter(chest), Math.PI / 2)
                : NoPenaltyTargeting.find(npc, 8, 4);
        return target != null;
    }

    @Override
    public void start() {
        npc.getNavigation().startMovingTo(target.x, target.y, target.z, 1.0);
    }

    @Override
    public boolean shouldContinue() {
        return !npc.sleeping.isSleeping() && !npc.getNavigation().isIdle();
    }

    @Override
    public void stop() {
        npc.resetIdle();
    }
}
