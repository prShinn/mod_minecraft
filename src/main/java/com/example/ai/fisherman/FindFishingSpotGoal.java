package com.example.ai.fisherman;

import com.example.entity.FishermanNpcEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

public class FindFishingSpotGoal extends Goal {

    private final FishermanNpcEntity npc;
    private BlockPos targetSpot;
    private int travelTicks;
    private boolean failed;

    public FindFishingSpotGoal(FishermanNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (npc.sleeping.isSleeping()) return false;
        if (npc.needsToDeposit()) return false;
        if (npc.memory.fishingSpot != null) return false; // Đã có spot thì ko tìm nữa
        
        BlockPos spot = npc.findFishingSpot();
        if (spot != null) {
            targetSpot = spot;
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        failed = false;
        travelTicks = 0;
        if (arrived()) return;
        var navigation = npc.getNavigation();
        var path = navigation.findPathTo(targetSpot.getX() + 0.5, targetSpot.getY(), targetSpot.getZ() + 0.5, 0);
        failed = path == null || !path.reachesTarget() || !navigation.startMovingAlong(path, 1.0);
    }

    @Override
    public boolean shouldContinue() {
        if (targetSpot == null || arrived()) return false;
        if (failed || ++travelTicks >= 240 || npc.getNavigation().isIdle()) {
            failed = true;
            return false;
        }
        return true;
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        if (targetSpot != null) {
            if (arrived() && npc.findFishingWater(targetSpot) != null) {
                npc.memory.fishingSpot = targetSpot;
                npc.display.clearReport();
            } else if (failed) {
                npc.skipFishingSpot(targetSpot);
            }
        }
        targetSpot = null;
    }

    private boolean arrived() {
        return npc.squaredDistanceTo(targetSpot.getX() + 0.5, targetSpot.getY(),
                targetSpot.getZ() + 0.5) < 4.0;
    }
}
