package com.example.ai.soldier;

import com.example.entity.SoldierNPCEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/** Lính có điểm neo: đi lang thang quanh điểm neo, đi xa quá thì quay về. */
public class GuardPostWanderGoal extends Goal {
    private static final int IDLE_CHANCE = 60; // trung bình 3 giây đổi chỗ đứng một lần
    private static final int TRIES = 4;

    private final SoldierNPCEntity soldier;
    private Vec3d target;

    public GuardPostWanderGoal(SoldierNPCEntity soldier) {
        this.soldier = soldier;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (soldier.getTarget() != null || soldier.getPose() == EntityPose.SLEEPING || !soldier.hasGuardPost()) {
            return false;
        }
        BlockPos anchor = soldier.getGuardAnchor();
        Vec3d home = Vec3d.ofBottomCenter(anchor);
        double radiusSq = GuardPost.WANDER_RADIUS * GuardPost.WANDER_RADIUS;
        if (soldier.squaredDistanceTo(home) > radiusSq) {
            // Đi quá xa (đuổi quái, nhặt đồ...) → quay về điểm neo
            Vec3d back = NoPenaltyTargeting.findTo(soldier, 10, 7, home, Math.PI / 2);
            target = back != null ? back : home;
            return true;
        }
        if (soldier.getRandom().nextInt(IDLE_CHANCE) != 0) return false;
        for (int i = 0; i < TRIES; i++) {
            Vec3d candidate = NoPenaltyTargeting.find(soldier, 8, 4);
            if (candidate != null && candidate.squaredDistanceTo(home) <= radiusSq) {
                target = candidate;
                return true;
            }
        }
        return false;
    }

    @Override
    public void start() {
        soldier.getNavigation().startMovingTo(target.x, target.y, target.z, 1.0);
    }

    @Override
    public boolean shouldContinue() {
        return !soldier.getNavigation().isIdle() && soldier.getTarget() == null && soldier.hasGuardPost();
    }
}
