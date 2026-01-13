package com.example.ai.soldỉier;

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
            double speed,
            float followDistance,
            float teleportDistance) {
        this.npc = npc;
        this.speed = speed;
        this.followDistance = followDistance;
        this.teleportDistance = teleportDistance;
    }

    @Override
    public boolean canStart() {
        updateDistancesByMode();
        owner = npc.getOwner();
        if (owner == null) return false;
        // Đang combat thì không follow
        if (npc.getFollowPlayerUUID() != null && npc.getTarget() != null) return false;

        // CHỈ CHECK OWNER
        double distSq = npc.squaredDistanceTo(owner);

        return distSq > this.followDistance * this.followDistance;
    }

    @Override
    public boolean shouldContinue() {
        if (owner == null) return false;
        return npc.squaredDistanceTo(owner)
                > this.followDistance * this.followDistance;
    }

    @Override
    public void start() {
        updateDistancesByMode();

        npc.setSprinting(true);
    }

    @Override
    public void tick() {
        if (npc.getWorld().isClient) return;

        owner = npc.getOwner();
        if (owner == null) return;

        double distSq = npc.squaredDistanceTo(owner);
        updateDistancesByMode();

        // TELEPORT nếu quá xa
        if (distSq > teleportDistance * teleportDistance) {
            npc.refreshPositionAndAngles(
                    owner.getX() + npc.getRandom().nextInt(3) - 1,
                    owner.getY(),
                    owner.getZ() + npc.getRandom().nextInt(3) - 1,
                    npc.getYaw(),
                    npc.getPitch()
            );
            // Sau khi teleport, vẫn follow tiếp
            distSq = npc.squaredDistanceTo(owner);
        }

        // START MOVING nếu xa
        if (distSq > followDistance * followDistance) {
            npc.getNavigation().startMovingTo(owner, speed);
        }
        // DỪNG nếu đủ gần
        else if (distSq < 4) {
            npc.getNavigation().stop();
        }
    }
    private void updateDistancesByMode() {
        ModeNpc.ModeMove currentMode = npc.getMoveMode();

        if (currentMode == ModeNpc.ModeMove.WANDER) {
            this.followDistance = SoldierNPCEntity.FOLLOW_DISTANCE;
            this.teleportDistance = SoldierNPCEntity.TELEPORT_DISTANCE;
        } else { // FOLLOW
            this.followDistance = 5F;
            this.teleportDistance = 7F;
        }
    }
    @Override
    public void stop() {
        npc.setSprinting(false);
        npc.getNavigation().stop();
    }


}
