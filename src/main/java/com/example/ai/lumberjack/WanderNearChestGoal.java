package com.example.ai.lumberjack;

import com.example.entity.LumberjackNpcEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.Random;

public class WanderNearChestGoal extends Goal {
    private final LumberjackNpcEntity npc;
    private final Random random = new Random();
    private int searchChestCooldown = 0;
    private int wanderCooldown = 0;
    private BlockPos wanderTarget;
    private static final int WANDER_RADIUS = 12;
    private static final int SEARCH_CHEST_INTERVAL = 100; // Tìm chest mỗi 5 giây
    private static final int WANDER_INTERVAL = 60; // Chọn điểm mới mỗi 3 giây

    public WanderNearChestGoal(LumberjackNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        // Chỉ lang thang khi không có việc gì khác (idle lâu)
        if (!npc.memory.shouldWander()) return false;

        // Nếu đang có việc rõ ràng thì KHÔNG wander
        return npc.findNearestTree() == null;
    }

    @Override
    public void start() {
        searchChestCooldown = 0;
        wanderCooldown = 0;
        pickNewWanderTarget();
    }

    @Override
    public void tick() {
        // Liên tục tìm chest trong khi lang thang
        if (searchChestCooldown-- <= 0) {
            searchChestCooldown = SEARCH_CHEST_INTERVAL;

            Inventory chest = npc.findNearestChest();
            if (chest != null) {
                // Tìm thấy chest -> kiểm tra có cây không
                BlockPos tree = npc.findNearestTree();
                if (tree != null) {
                    // Có cây để chặt -> dừng lang thang, để ChopTreeGoal đảm nhiệm
                    npc.memory.resetIdle();
                    return;
                }
            }
        }

        // Chọn điểm lang thang mới
        if (wanderCooldown-- <= 0) {
            wanderCooldown = WANDER_INTERVAL;

            // Nếu đã đến điểm cũ hoặc không có đường đi
            if (wanderTarget == null ||
                    !npc.getNavigation().isFollowingPath() ||
                    npc.squaredDistanceTo(Vec3d.ofCenter(wanderTarget)) < 4.0) {
                pickNewWanderTarget();
            }
        }
    }

    @Override
    public boolean shouldContinue() {
        // Tiếp tục lang thang nếu vẫn idle
        // Nhưng nếu tìm thấy chest + cây thì dừng
        return npc.memory.shouldWander();
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        wanderTarget = null;
    }

    /**
     * Chọn vị trí ngẫu nhiên để lang thang
     */
    private void pickNewWanderTarget() {
        BlockPos current = npc.getBlockPos();

        // Random một điểm trong bán kính 12 blocks
        int dx = random.nextInt(WANDER_RADIUS * 2 + 1) - WANDER_RADIUS;
        int dz = random.nextInt(WANDER_RADIUS * 2 + 1) - WANDER_RADIUS;

        wanderTarget = current.add(dx, 0, dz);

        // Tìm vị trí y hợp lệ (mặt đất)
        wanderTarget = npc.getWorld().getTopPosition(
                net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                wanderTarget
        );

        // Di chuyển đến điểm đó
        npc.getNavigation().startMovingTo(
                wanderTarget.getX() + 0.5,
                wanderTarget.getY(),
                wanderTarget.getZ() + 0.5,
                0.8 // Đi chậm khi lang thang
        );
    }

}
