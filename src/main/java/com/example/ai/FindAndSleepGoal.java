package com.example.ai;

import com.example.entity.base.NpcSleepingComponent;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;

import java.util.EnumSet;

public class FindAndSleepGoal extends Goal {
    private final PathAwareEntity npc;
    private final java.util.function.Supplier<NpcSleepingComponent> sleeping;

    public FindAndSleepGoal(PathAwareEntity npc, java.util.function.Supplier<NpcSleepingComponent> sleeping) {
        this.npc = npc;
        this.sleeping = sleeping;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
    }

    @Override
    public boolean canStart() {
        NpcSleepingComponent sleepComponent = sleeping.get();
        if (sleepComponent == null || npc.getTarget() != null) return false;
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
        NpcSleepingComponent sleepComponent = sleeping.get();
        sleepComponent.startMovingToBed(npc);
    }

    @Override
    public void tick() {
        NpcSleepingComponent sleepComponent = sleeping.get();
        sleepComponent.tickMovingToBed(npc);
    }

    @Override
    public boolean shouldContinue() {
        NpcSleepingComponent sleepComponent = sleeping.get();
        return npc.getWorld().isNight() && npc.getTarget() == null
                && (sleepComponent.isSleeping()
                    || sleepComponent.getTargetBed() != null && !sleepComponent.shouldWander());
    }

    @Override
    public void stop() {
        NpcSleepingComponent sleepComponent = sleeping.get();
        if (sleepComponent.isSleeping()) sleepComponent.wakeUp(npc);
        else sleepComponent.cancelMovingToBed(npc);
    }
}