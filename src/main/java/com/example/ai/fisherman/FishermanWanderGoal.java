package com.example.ai.fisherman;

import com.example.entity.FishermanNpcEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.ai.NoPenaltyTargeting;

import java.util.EnumSet;

public class FishermanWanderGoal extends Goal {

    private final FishermanNpcEntity npc;
    private double targetX, targetY, targetZ;

    public FishermanWanderGoal(FishermanNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Goal.Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (npc.sleeping.isSleeping()) return false;
        if (npc.needsToDeposit()) return false;
        if (npc.memory.fishingSpot != null) return false; // Nếu có hồ cá thì không lang thang

        if (!npc.memory.shouldWander()) return false;

        Vec3d target = NoPenaltyTargeting.find(npc, 10, 7);
        if (target == null) return false;

        this.targetX = target.x;
        this.targetY = target.y;
        this.targetZ = target.z;
        return true;
    }

    @Override
    public void start() {
        npc.getNavigation().startMovingTo(targetX, targetY, targetZ, 1.0);
    }

    @Override
    public boolean shouldContinue() {
        return !npc.getNavigation().isIdle();
    }

    @Override
    public void stop() {
        npc.memory.resetIdle();
    }
}
