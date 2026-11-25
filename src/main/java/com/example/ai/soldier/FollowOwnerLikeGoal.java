package com.example.ai.soldier;

import com.example.entity.SoldierNPCEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;

public class FollowOwnerLikeGoal extends Goal {

    private final SoldierNPCEntity npc;
    private final double speed;
    private float followDistance;
    private float teleportDistance;
    private PlayerEntity owner;

    public FollowOwnerLikeGoal(
            SoldierNPCEntity npc,
            double speed) {
        this.npc = npc;
        this.speed = speed;
        this.setControls(java.util.EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        owner = npc.getOwner();
        if (owner == null) return false;
        // Đang combat thì không follow
        if (npc.getTarget() != null) return false;
        // Lính giữ tuyến tuần tra hoặc canh điểm neo → ở lại, không chạy theo chủ
        if (npc.isHeldAtPost()) return false;

        // CHỈ CHECK OWNER
        double distSq = npc.squaredDistanceTo(owner);
        float followDist = getFollowDistance(); // ✅ Tính động

        return distSq > followDist * followDist;
    }

    @Override
    public boolean shouldContinue() {
        if (owner == null) return false;
        if (npc.isHeldAtPost()) return false;
        // ✅ QUAN TRỌNG: Nếu đổi sang chế độ không theo chủ (canh gác/đi tuần) → dừng follow ngay
        if (npc.getMoveMode() != ModeNpc.ModeMove.FOLLOW) {
            double distSq = npc.squaredDistanceTo(owner);
            float followDist = getFollowDistance();
            // Chỉ tiếp tục nếu quá xa (theo ngưỡng WANDER)
            return distSq > followDist * followDist;
        }
        return npc.squaredDistanceTo(owner)
                > getFollowDistance() * getFollowDistance();
    }

    @Override
    public void start() {
        npc.setSprinting(true);
    }

    @Override
    public void tick() {
        if (npc.getWorld().isClient) return;

        owner = npc.getOwner();
        if (owner == null) return;

        double distSq = npc.squaredDistanceTo(owner);
        float followDist = getFollowDistance();       // ✅ Tính theo mode hiện tại
        float teleportDist = getTeleportDistance();
        // TELEPORT nếu quá xa
        if (distSq > teleportDist * teleportDist) {
            boolean teleported = false;
            for (int i = 0; i < 10; i++) {
                int dx = npc.getRandom().nextInt(7) - 3; // -3 to 3
                int dy = npc.getRandom().nextInt(3) - 1; // -1 to 1
                int dz = npc.getRandom().nextInt(7) - 3; // -3 to 3
                
                int targetX = (int)Math.floor(owner.getX()) + dx;
                int targetY = (int)Math.floor(owner.getY()) + dy;
                int targetZ = (int)Math.floor(owner.getZ()) + dz;
                
                net.minecraft.util.math.BlockPos targetPos = new net.minecraft.util.math.BlockPos(targetX, targetY, targetZ);
                
                // Safe check
                if (npc.getWorld().getBlockState(targetPos.down()).isOpaque() &&
                    npc.getWorld().isAir(targetPos) &&
                    npc.getWorld().isAir(targetPos.up())) {
                    
                    npc.refreshPositionAndAngles(targetX + 0.5, targetY, targetZ + 0.5, npc.getYaw(), npc.getPitch());
                    teleported = true;
                    break;
                }
            }
            if (teleported) {
                npc.getNavigation().stop();
            }
            distSq = npc.squaredDistanceTo(owner);
        }

        // START MOVING nếu xa
        if (distSq > followDist * followDist) {
            npc.getNavigation().startMovingTo(owner, speed);
        }
        // DỪNG nếu đủ gần
        else if (distSq < 5) {
            npc.getNavigation().stop();
        }
    }
    private float getFollowDistance() {
        return npc.getMoveMode() == ModeNpc.ModeMove.FOLLOW ? 5F : SoldierNPCEntity.FOLLOW_DISTANCE;
    }

    private float getTeleportDistance() {
        return npc.getMoveMode() == ModeNpc.ModeMove.FOLLOW ? 8F : SoldierNPCEntity.TELEPORT_DISTANCE;
    }
    @Override
    public void stop() {
        npc.setSprinting(false);
        npc.getNavigation().stop();
    }


}
