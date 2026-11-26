package com.example.ai.soldier;

import com.example.entity.SoldierNPCEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

/**
 * Đứng canh gác: thỉnh thoảng (trung bình 6 giây) bước sang một ô trong bán kính nhỏ quanh điểm gác,
 * còn lại đứng yên nhìn quanh. Chi phí: 1 lần tìm đường ngắn mỗi lần bước.
 */
public class StandGuardWanderGoal extends Goal {
    private static final int CHANCE = 60; // canStart ~10 lần/giây → trung bình 6 giây bước một lần
    private static final int TIMEOUT_TICKS = 100;

    private final SoldierNPCEntity soldier;
    private final int radius;
    private BlockPos target;
    private int ticks;

    public StandGuardWanderGoal(SoldierNPCEntity soldier, int radius) {
        this.soldier = soldier;
        this.radius = radius;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (!soldier.isStandingGuard() || soldier.getTarget() != null || soldier.getPose() == EntityPose.SLEEPING) return false;
        if (soldier.getRandom().nextInt(CHANCE) != 0) return false;
        BlockPos anchor = soldier.getGuardAnchor();
        if (anchor == null) return false;
        target = anchor.add(soldier.getRandom().nextInt(radius * 2 - 1) - (radius - 1), 0,
                soldier.getRandom().nextInt(radius * 2 - 1) - (radius - 1));
        return true;
    }

    @Override
    public void start() {
        ticks = 0;
        soldier.getNavigation().startMovingTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.8);
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public boolean shouldContinue() {
        return ticks < TIMEOUT_TICKS && !soldier.getNavigation().isIdle() && soldier.getTarget() == null
                && soldier.isStandingGuard();
    }

    @Override
    public void stop() {
        soldier.getNavigation().stop();
        target = null;
    }
}
