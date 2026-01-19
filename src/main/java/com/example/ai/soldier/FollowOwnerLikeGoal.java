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
    }

    @Override
    public boolean canStart() {
        owner = npc.getOwner();
        if (owner == null) return false;
        // Đang combat thì không follow
        if (npc.getFollowPlayerUUID() != null && npc.getTarget() != null) return false;

        // CHỈ CHECK OWNER
        double distSq = npc.squaredDistanceTo(owner);
        float followDist = getFollowDistance(); // ✅ Tính động

        return distSq > followDist * followDist;
    }

    @Override
    public boolean shouldContinue() {
        if (owner == null) return false;
        // ✅ QUAN TRỌNG: Nếu đổi sang WANDER mode → dừng follow ngay
        if (npc.getMoveMode() == ModeNpc.ModeMove.WANDER) {
            double distSq = npc.squaredDistanceTo(owner);
            float followDist = getFollowDistance();
            // Chỉ tiếp tục nếu quá xa (theo ngưỡng WANDER)
            return distSq > followDist * followDist;
        }
        return npc.squaredDistanceTo(owner)
                > this.followDistance * this.followDistance;
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
            npc.refreshPositionAndAngles(
                    owner.getX() + npc.getRandom().nextInt(3) - 1,
                    owner.getY(),
                    owner.getZ() + npc.getRandom().nextInt(3) - 1,
                    npc.getYaw(),
                    npc.getPitch()
            );
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
