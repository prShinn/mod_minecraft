package com.example.ai.miner;

import com.example.entity.MinerNpcEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;
import java.util.Random;

/**
 * AI Goal — Lang thang khi không tìm thấy Mỏ Quặng nào trong bán kính 16 blocks.
 *
 * Điều kiện kích hoạt:
 * - NPC đã idle quá lâu (shouldWander() = true)
 * - Không tìm thấy ore vein nào trong phạm vi
 *
 * Hành vi:
 * - Chọn điểm ngẫu nhiên trong bán kính 16 blocks
 * - Di chuyển đến đó
 * - Trong lúc lang thang vẫn tìm ore vein mỗi 5 giây
 * - Nếu tìm thấy mỏ → dừng ngay
 */
public class MinerWanderGoal extends Goal {

    private final MinerNpcEntity npc;
    private final Random random = new Random();

    private BlockPos wanderTarget;
    private int wanderCooldown = 0;

    // Chọn điểm wander mới mỗi 3 giây
    private static final int WANDER_INTERVAL = 60;

    // Bán kính lang thang
    private static final int WANDER_RADIUS = 16;

    public MinerWanderGoal(MinerNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Goal.Control.MOVE));
    }

    @Override
    public boolean canStart() {
        // Không lang thang khi đang ngủ
        if (npc.sleeping.isSleeping()) return false;
        // Chỉ lang thang khi đã idle lâu
        if (!npc.memory.shouldWander()) return false;
        // The higher priority mining goal owns ore discovery.
        return !npc.memory.needsToDeposit;
    }

    @Override
    public void start() {
        wanderCooldown = 0;
        pickNewWanderTarget();
    }

    @Override
    public void tick() {
        // Chọn điểm wander mới nếu cần
        if (wanderCooldown-- <= 0) {
            wanderCooldown = WANDER_INTERVAL;

            // Nếu đã đến điểm cũ hoặc không đang di chuyển
            if (wanderTarget == null
                    || !npc.getNavigation().isFollowingPath()
                    || npc.squaredDistanceTo(
                            wanderTarget.getX() + 0.5,
                            wanderTarget.getY(),
                            wanderTarget.getZ() + 0.5) < 4.0) {
                pickNewWanderTarget();
            }
        }
    }

    @Override
    public boolean shouldContinue() {
        if (npc.sleeping.isSleeping()) return false;
        // Dừng nếu không còn idle
        return npc.memory.shouldWander();
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        wanderTarget = null;
    }

    /**
     * Chọn ngẫu nhiên một điểm trong bán kính WANDER_RADIUS
     */
    private void pickNewWanderTarget() {
        BlockPos current = npc.getBlockPos();

        int dx = random.nextInt(WANDER_RADIUS * 2 + 1) - WANDER_RADIUS;
        int dz = random.nextInt(WANDER_RADIUS * 2 + 1) - WANDER_RADIUS;

        wanderTarget = current.add(dx, 0, dz);

        // Tìm mặt đất tại điểm đích
        wanderTarget = npc.getWorld().getTopPosition(
                net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                wanderTarget
        );

        npc.getNavigation().startMovingTo(
                wanderTarget.getX() + 0.5,
                wanderTarget.getY(),
                wanderTarget.getZ() + 0.5,
                0.8 // Đi chậm khi lang thang
        );
    }
}
