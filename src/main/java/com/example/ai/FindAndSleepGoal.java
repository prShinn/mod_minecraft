package com.example.ai;

import com.example.entity.base.NpcSleepingComponent;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;

import java.util.EnumSet;

public class FindAndSleepGoal extends Goal {
    private final PathAwareEntity npc;
    private final NpcSleepingComponent sleepComponent;

    public FindAndSleepGoal(PathAwareEntity npc, NpcSleepingComponent sleepComponent) {
        this.npc = npc;
        this.sleepComponent = sleepComponent;
        this.setControls(EnumSet.of(Control.MOVE, Goal.Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (!npc.getWorld().isNight() || sleepComponent.isSleeping()) {
            return false;
        }

        if (sleepComponent.shouldWander()) {
            return false;
        }

        if (sleepComponent.getTargetBed() != null) {
            return true;
        }

        return sleepComponent.shouldFindBed(npc)
                && sleepComponent.findAndReserveBed(npc) != null;
    }

    @Override
    public void start() {
        sleepComponent.startMovingToBed(npc);
    }

    @Override
    public void tick() {
        sleepComponent.tickMovingToBed(npc);
    }

    @Override
    public boolean shouldContinue() {
        return !sleepComponent.isSleeping()
                && npc.getWorld().isNight()
                && sleepComponent.getTargetBed() != null
                && !sleepComponent.shouldWander();
    }

    @Override
    public void stop() {
        // Component handles cleanup
    }
}