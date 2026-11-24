package com.example.ai;

import com.example.entity.base.NpcSleepingComponent;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.mob.PathAwareEntity;

public class WanderForBedGoal extends WanderAroundFarGoal {
    private final java.util.function.Supplier<NpcSleepingComponent> sleeping;

    public WanderForBedGoal(PathAwareEntity npc, double speed, java.util.function.Supplier<NpcSleepingComponent> sleeping) {
        super(npc, speed);
        this.sleeping = sleeping;
    }

    @Override
    public boolean canStart() {
        return sleeping.get().shouldWander() && super.canStart();
    }

    @Override
    public boolean shouldContinue() {
        return sleeping.get().shouldWander() && super.shouldContinue();
    }
}
