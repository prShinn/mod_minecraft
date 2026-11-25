package com.example.ai.soldier;

import com.example.entity.SoldierNPCEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;

import java.util.EnumSet;

public class ReturnToPlayerGoal extends Goal {
    private final SoldierNPCEntity soldier;
    private PlayerEntity owner;
    /** Thời điểm (world time) bắt đầu ở quá xa chủ; -1 = đang trong vùng. canStart chỉ được gọi 2 tick/lần nên không đếm bằng ++. */
    private long outOfRangeSince = -1;

    // Thời gian lang thang trước khi quay về (giây)
    private static final int WANDER_TIME = 30; // 30 giây = 600 ticks
    private static final int WANDER_TICKS = WANDER_TIME * 20;

    // Tốc độ di chuyển
    private static final double RETURN_SPEED = 1.5F; // Nhanh hơn bình thường

    public ReturnToPlayerGoal(SoldierNPCEntity soldier) {
        this.soldier = soldier;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    private double getMinDist() {
        return soldier.getMoveMode() == com.example.ai.soldier.ModeNpc.ModeMove.FOLLOW ? 4.0 : SoldierNPCEntity.FOLLOW_DISTANCE * 0.5;
    }

    private double getMaxDist() {
        return soldier.getMoveMode() == com.example.ai.soldier.ModeNpc.ModeMove.FOLLOW ? 6.0 : SoldierNPCEntity.FOLLOW_DISTANCE * 0.8;
    }

    @Override
    public boolean canStart() {
        // Không có owner → không cần quay về
        this.owner = soldier.getOwner();
        if (owner == null || !owner.isAlive()) {
            return false;
        }

        // Lính giữ tuyến tuần tra hoặc canh điểm neo → ở lại, không quay về chủ
        if (soldier.isHeldAtPost()) {
            outOfRangeSince = -1;
            return false;
        }

        // Đang trong combat → ưu tiên chiến đấu
        if (soldier.getTarget() != null) {
            outOfRangeSince = -1; // Reset timer khi combat
            return false;
        }

        // Kiểm tra khoảng cách
        double distanceSq = soldier.squaredDistanceTo(owner);
        double minDistSq = getMinDist() * getMinDist();
        double maxDistSq = getMaxDist() * getMaxDist();

        // Đã ở gần player → reset timer
        if (distanceSq >= minDistSq && distanceSq <= maxDistSq) {
            outOfRangeSince = -1;
            return false;
        }

        // Quá gần → không cần về
        if (distanceSq < minDistSq) {
            outOfRangeSince = -1;
            return false;
        }

        // Tăng timer mỗi tick
        long now = soldier.getWorld().getTime();
        if (outOfRangeSince < 0) outOfRangeSince = now;

        // Đã lang thang đủ lâu → quay về
        return now - outOfRangeSince >= WANDER_TICKS;
    }

    @Override
    public boolean shouldContinue() {
        if (owner == null || !owner.isAlive()) {
            return false;
        }

        // Nếu bị tấn công → dừng quay về
        if (soldier.getTarget() != null) {
            return false;
        }

        // Kiểm tra đã về đến chưa
        double distanceSq = soldier.squaredDistanceTo(owner);
        double minDistSq = getMinDist() * getMinDist();
        double maxDistSq = getMaxDist() * getMaxDist();

        // Đã về đến vùng mục tiêu → dừng
        if (distanceSq >= minDistSq && distanceSq <= maxDistSq) {
            return false;
        }

        // Quá gần → dừng
        if (distanceSq < minDistSq) {
            return false;
        }

        // Tiếp tục di chuyển
        return true;
    }

    @Override
    public void start() {
        super.start();
        // Optional: Sound effect khi bắt đầu quay về
         soldier.playSound(SoundEvents.ENTITY_WOLF_WHINE, 0.5F, 1.0F);
    }

    @Override
    public void tick() {
        if (owner == null) return;

        // Di chuyển về phía player
        soldier.getNavigation().startMovingTo(owner, RETURN_SPEED);

        // Nhìn về phía player
        soldier.getLookControl().lookAt(owner, 10.0F, soldier.getMaxLookPitchChange());
    }

    @Override
    public void stop() {
        super.stop();
        // Reset timer khi về đến
        outOfRangeSince = -1;
        soldier.getNavigation().stop();
        soldier.playSound(SoundEvents.ENTITY_VILLAGER_YES, 0.5F, 1.0F);
    }

}
