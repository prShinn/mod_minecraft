package com.example.ai;

import com.example.entity.base.NpcSleepingComponent;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.mob.PathAwareEntity;

public class WanderForBedGoal extends WanderAroundFarGoal {
    private final NpcSleepingComponent sleepComponent;

    public WanderForBedGoal(PathAwareEntity npc, double speed, NpcSleepingComponent sleepComponent) {
        super(npc, speed);
        this.sleepComponent = sleepComponent;
    }

    @Override
    public boolean canStart() {
        if(sleepComponent == null) return false;
        return sleepComponent.shouldWander() && super.canStart();
    }

    @Override
    public void tick() {
        super.tick();
        sleepComponent.tickWandering();
    }

    @Override
    public boolean shouldContinue() {
        return sleepComponent.shouldWander() && super.shouldContinue();
    }
}
