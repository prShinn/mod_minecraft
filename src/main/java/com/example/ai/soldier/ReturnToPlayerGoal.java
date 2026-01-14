package com.example.ai.soldier;

import com.example.entity.SoldierNPCEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;

import java.util.EnumSet;

public class ReturnToPlayerGoal extends Goal {
    private final SoldierNPCEntity soldier;
    private PlayerEntity owner;
    private int wanderTimer = 0;

    // Khoảng cách mục tiêu (6-8 blocks)
    private static final double MIN_DISTANCE = 4.0;
    private static final double MAX_DISTANCE = 6.0;

    // Thời gian lang thang trước khi quay về (giây)
    private static final int WANDER_TIME = 30; // 30 giây = 600 ticks
    private static final int WANDER_TICKS = WANDER_TIME * 20;

    // Tốc độ di chuyển
    private static final double RETURN_SPEED = 1.2; // Nhanh hơn bình thường

    public ReturnToPlayerGoal(SoldierNPCEntity soldier) {
        this.soldier = soldier;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        // Không có owner → không cần quay về
        this.owner = soldier.getOwner();
        if (owner == null || !owner.isAlive()) {
            return false;
        }

        // Đang trong combat → ưu tiên chiến đấu
        if (soldier.getTarget() != null) {
            wanderTimer = 0; // Reset timer khi combat
            return false;
        }

        // Kiểm tra khoảng cách
        double distanceSq = soldier.squaredDistanceTo(owner);
        double minDistSq = MIN_DISTANCE * MIN_DISTANCE;
        double maxDistSq = MAX_DISTANCE * MAX_DISTANCE;

        // Đã ở gần player (6-8 blocks) → reset timer
        if (distanceSq >= minDistSq && distanceSq <= maxDistSq) {
            wanderTimer = 0;
            return false;
        }

        // Quá gần (< 6 blocks) → không cần về
        if (distanceSq < minDistSq) {
            wanderTimer = 0;
            return false;
        }

        // Tăng timer mỗi tick
        wanderTimer++;

        // Đã lang thang đủ lâu → quay về
        return wanderTimer >= WANDER_TICKS;
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
        double minDistSq = MIN_DISTANCE * MIN_DISTANCE;
        double maxDistSq = MAX_DISTANCE * MAX_DISTANCE;

        // Đã về đến vùng 6-8 blocks → dừng
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
        // soldier.playSound(SoundEvents.ENTITY_WOLF_WHINE, 0.5F, 1.0F);
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
        wanderTimer = 0;
        soldier.getNavigation().stop();
        soldier.playSound(SoundEvents.ENTITY_VILLAGER_YES, 0.5F, 1.0F);
    }

}
