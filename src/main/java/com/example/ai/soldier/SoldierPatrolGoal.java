package com.example.ai.soldier;

import com.example.block.PatrolFlagState;
import com.example.entity.SoldierNPCEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Soldier tự đi tuần vòng tròn giữa các Cờ Tuần Tra của chủ (cần >= 2 cờ trong bán kính).
 * Chỉ chạy ở chế độ PATROL ("Đi tuần tra"), ngoài giao tranh; các goal ưu tiên cao hơn (ngủ, ăn...) vẫn chen ngang được.
 * Khi có tuyến hợp lệ, lính PATROL không bị kéo về chủ (xem {@link #isOnPatrolDuty}).
 */
public class SoldierPatrolGoal extends Goal {
    private static final double SPEED = 1.0D;
    private static final double ARRIVE_HORIZONTAL_SQ = 2.5D * 2.5D;
    private static final double ARRIVE_VERTICAL = 3.0D;
    /** Tới mỗi cờ đứng canh đúng 3 giây rồi mới đi tiếp. */
    private static final int PAUSE_TICKS = 60;
    private static final int LEG_TIMEOUT_TICKS = 20 * 40; // kẹt đường > 40s → bỏ qua cờ này
    private static final int REPATH_COOLDOWN_TICKS = 20;
    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final double DIRECT_PATH_SQ = 24.0 * 24.0;

    private final SoldierNPCEntity soldier;
    private BlockPos target;
    private BlockPos lastReached;
    private int scanCooldown;
    private int pauseTicks;
    private int legTicks;
    private int repathCooldown;

    public SoldierPatrolGoal(SoldierNPCEntity soldier) {
        this.soldier = soldier;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (!canPatrol()) return false;
        if (scanCooldown-- > 0) return false;
        scanCooldown = SCAN_INTERVAL_TICKS;

        List<BlockPos> route = route();
        if (route.size() < 2) return false;
        target = nextAfter(route, lastReached);
        soldier.setPatrolHint(target);
        return target != null;
    }

    @Override
    public boolean shouldContinue() {
        return target != null && canPatrol();
    }

    @Override
    public void start() {
        pauseTicks = 0;
        legTicks = 0;
        repathCooldown = 0;
        moveToTarget();
    }

    @Override
    public void tick() {
        if (target == null) return;

        if (pauseTicks > 0) {
            // Đứng canh tại cờ rồi mới đi tiếp
            soldier.getLookControl().lookAt(target.getX() + 0.5, target.getY() + 1.5, target.getZ() + 0.5);
            if (--pauseTicks == 0) advance();
            return;
        }

        legTicks++;
        if (hasArrived()) {
            lastReached = target;
            soldier.getNavigation().stop();
            pauseTicks = PAUSE_TICKS;
            return;
        }
        if (legTicks > LEG_TIMEOUT_TICKS) {
            // Kẹt đường quá 40s → báo chủ qua Sổ quản lý làng rồi sang cờ kế tiếp (không đứng đơ mãi)
            com.example.entity.base.NpcDisplayComponent.reportWorker(soldier,
                    "Không tới được cờ tuần tra " + target.getX() + " " + target.getY() + " " + target.getZ());
            lastReached = target;
            advance();
            return;
        }
        if (repathCooldown > 0) repathCooldown--;
        if (soldier.getNavigation().isIdle() && repathCooldown == 0) {
            moveToTarget();
        }
    }

    @Override
    public void stop() {
        soldier.getNavigation().stop();
        target = null;
        pauseTicks = 0;
    }

    private boolean canPatrol() {
        return !soldier.getWorld().isClient
                && soldier.getMoveMode() == ModeNpc.ModeMove.PATROL
                && soldier.getTarget() == null
                && soldier.getPose() != net.minecraft.entity.EntityPose.SLEEPING
                && soldier.getOwnerUuid() != null;
    }

    private List<BlockPos> route() {
        return routeOf(soldier);
    }

    private static List<BlockPos> routeOf(SoldierNPCEntity soldier) {
        UUID owner = soldier.getOwnerUuid();
        if (owner == null || !(soldier.getWorld() instanceof ServerWorld world)) return List.of();
        // Tâm = cờ đang nhắm tới: lính đuổi quái ra xa tuyến vẫn nhận đúng tuyến cũ
        BlockPos center = soldier.getPatrolHint() != null ? soldier.getPatrolHint() : soldier.getBlockPos();
        return PatrolFlagState.get(world).routeFor(world, owner, center);
    }

    /**
     * Lính đang ở chế độ Đi tuần tra và có tuyến hợp lệ (≥ 2 cờ của chủ quanh đó).
     * Khi đúng, các goal theo/quay về chủ nhường để lính ở lại giữ tuyến;
     * không có tuyến thì lính xử lý như "Tự do canh gác".
     */
    public static boolean isOnPatrolDuty(SoldierNPCEntity soldier) {
        // Nhiều goal hỏi mỗi tick → nhớ kết quả 1 giây (duyệt cờ cả thế giới là đắt)
        return soldier.getMoveMode() == ModeNpc.ModeMove.PATROL
                && soldier.cachedPatrolDuty(() -> routeOf(soldier).size() >= 2);
    }

    /**
     * Cờ kế tiếp theo thứ tự đặt cờ sau {@code previous}. Bị ngắt (đánh quái, ăn) giữa đường thì đi tiếp
     * đúng cờ đang nhắm dở, không nhảy cờ. Chưa có mốc thì chọn cờ gần nhất.
     */
    private BlockPos nextAfter(List<BlockPos> route, BlockPos previous) {
        BlockPos pending = soldier.getPatrolHint();
        if (pending != null && !pending.equals(previous) && route.contains(pending)) return pending;
        int idx = previous == null ? -1 : route.indexOf(previous);
        if (idx >= 0) return route.get((idx + 1) % route.size());

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : route) {
            double d = pos.getSquaredDistance(soldier.getPos());
            if (d < bestDist) {
                bestDist = d;
                best = pos;
            }
        }
        return best;
    }

    private void advance() {
        List<BlockPos> route = route();
        legTicks = 0;
        if (route.size() < 2) {
            target = null; // cờ bị phá → shouldContinue() kết thúc goal
            return;
        }
        target = nextAfter(route, lastReached);
        soldier.setPatrolHint(target);
        moveToTarget();
    }

    private void moveToTarget() {
        repathCooldown = REPATH_COOLDOWN_TICKS;
        if (target == null) return;
        // Cờ xa hơn tầm tìm đường của lính → đi từng chặng ~16 block hướng về cờ
        if (soldier.squaredDistanceTo(net.minecraft.util.math.Vec3d.ofBottomCenter(target)) > DIRECT_PATH_SQ) {
            net.minecraft.util.math.Vec3d step = net.minecraft.entity.ai.NoPenaltyTargeting.findTo(
                    soldier, 16, 7, net.minecraft.util.math.Vec3d.ofBottomCenter(target), Math.PI / 2);
            if (step != null && soldier.getNavigation().startMovingTo(step.x, step.y, step.z, SPEED)) return;
        }
        soldier.getNavigation().startMovingTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, SPEED);
    }

    private boolean hasArrived() {
        double dx = target.getX() + 0.5 - soldier.getX();
        double dz = target.getZ() + 0.5 - soldier.getZ();
        double dy = Math.abs(target.getY() - soldier.getY());
        return dx * dx + dz * dz <= ARRIVE_HORIZONTAL_SQ && dy <= ARRIVE_VERTICAL;
    }
}
