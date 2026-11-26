package com.example.ai;

import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Đi quá xa điểm neo (rương nghề / điểm canh gác) → quay về điểm neo MỘT lần rồi nhả goal,
 * để các goal lang thang bắt đầu lại từ điểm neo.
 * Xa hơn tầm tìm đường thì đi từng chặng ~10 block hướng về neo; tắc đường thì chờ rồi thử lại.
 */
public class ReturnToAnchorGoal extends Goal {
    private static final int STEP_RANGE = 10;
    /** Trong khoảng này thì tìm đường thẳng tới neo thay vì đi từng chặng. */
    private static final double DIRECT_PATH_SQ = 12.0 * 12.0;
    private static final int MAX_TICKS = 20 * 45;
    private static final int MAX_FAILS = 3;
    private static final int RETRY_TICKS = 200;

    private final PathAwareEntity mob;
    private final Supplier<BlockPos> anchor;
    private final BooleanSupplier allowed;
    private final double leaveSq;
    private final double arriveSq;
    private final double speed;

    @Nullable
    private BlockPos home;
    private int ticks;
    private int fails;
    private long retryAt;

    /**
     * @param anchor       điểm neo (null = chưa có, không làm gì)
     * @param allowed      điều kiện được phép quay về (không giao tranh, đang rảnh...)
     * @param leaveRadius  xa hơn bán kính này thì quay về
     * @param arriveRadius tới trong bán kính này coi như đã về
     */
    public ReturnToAnchorGoal(PathAwareEntity mob, Supplier<BlockPos> anchor, BooleanSupplier allowed,
                              double leaveRadius, double arriveRadius, double speed) {
        this.mob = mob;
        this.anchor = anchor;
        this.allowed = allowed;
        this.leaveSq = leaveRadius * leaveRadius;
        this.arriveSq = arriveRadius * arriveRadius;
        this.speed = speed;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    private double distSq(BlockPos pos) {
        return mob.squaredDistanceTo(Vec3d.ofBottomCenter(pos));
    }

    @Override
    public boolean canStart() {
        if (mob.getWorld().getTime() < retryAt || !allowed.getAsBoolean()) return false;
        BlockPos pos = anchor.get();
        if (pos == null || !mob.getWorld().isChunkLoaded(pos) || distSq(pos) <= leaveSq) return false;
        home = pos;
        return true;
    }

    @Override
    public void start() {
        ticks = 0;
        fails = 0;
        step();
    }

    @Override
    public void tick() {
        ticks++;
        if (home != null && ticks % 10 == 0 && mob.getNavigation().isIdle()) step();
    }

    private void step() {
        if (home == null) return;
        boolean ok;
        if (distSq(home) > DIRECT_PATH_SQ) {
            Vec3d next = NoPenaltyTargeting.findTo(mob, STEP_RANGE, 7, Vec3d.ofBottomCenter(home), Math.PI / 2);
            ok = next != null && mob.getNavigation().startMovingTo(next.x, next.y, next.z, speed);
        } else {
            ok = mob.getNavigation().startMovingTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, speed);
        }
        if (!ok || mob.getNavigation().isIdle()) fails++;
    }

    @Override
    public boolean shouldContinue() {
        return home != null && ticks < MAX_TICKS && fails < MAX_FAILS
                && allowed.getAsBoolean() && distSq(home) > arriveSq;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        // Chưa về tới (tắc đường / hết giờ / bị goal khác chen) → chờ rồi mới thử lại, tránh tính đường liên tục
        if (home != null && distSq(home) > arriveSq) retryAt = mob.getWorld().getTime() + RETRY_TICKS;
        home = null;
    }
}
