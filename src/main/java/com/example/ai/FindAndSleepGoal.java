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
        if(sleepComponent == null) return false;
        if (sleepComponent.isSleeping()) {
            return false;
        }
        long time = npc.getWorld().getTimeOfDay() % 24000;
        if (time < 12541 || time > 23458) return false;


        if (sleepComponent.getTargetBed() != null) {
            return true;
        }

        return sleepComponent.findAndReserveBed(npc) != null;
    }

    @Override
    public void start() {
        boolean started = sleepComponent.startMovingToBed(npc);
        System.out.println("[FindAndSleepGoal] Navigation started: " + started);

        if (!started) {
            sleepComponent.findAndReserveBed(npc); // Thử tìm giường khác
        }
    }

    @Override
    public void tick() {
        sleepComponent.tickMovingToBed(npc);
        if (sleepComponent.isSleeping()) {
            long time = npc.getWorld().getTimeOfDay() % 24000;
            boolean isNight = time >= 12541 && time <= 23458;

            // Thức dậy nếu hết đêm hoặc bị tấn công
            if (!isNight || npc.hurtTime > 0) {
                sleepComponent.wakeUp(npc);
            }
        }
    }

    @Override
    public boolean shouldContinue() {
        if (sleepComponent.isSleeping()) {
            long time = npc.getWorld().getTimeOfDay() % 24000;
            boolean isNight = time >= 12541 && time <= 23458;
            return isNight; // Tiếp tục ngủ trong đêm
        }
        long time = npc.getWorld().getTimeOfDay() % 24000;
        boolean isNight = time >= 12541 && time <= 23458;

        return isNight
                && sleepComponent.getTargetBed() != null
                && !sleepComponent.shouldWander();
    }

    @Override
    public void stop() {
        // Component handles cleanup
        if (!sleepComponent.isSleeping()) {
            sleepComponent.cancelMovingToBed(npc);
        }
    }
}