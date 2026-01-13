package com.example.entity.base;

import net.minecraft.block.BedBlock;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Set;

public class NpcSleepingComponent {
    private boolean sleeping = false;
    private BlockPos bedPos;

    public boolean isSleeping() {
        return sleeping;
    }

    public BlockPos getBedPos() {
        return bedPos;
    }

    public void startSleeping(PathAwareEntity npc, BlockPos bed, Set<BlockPos> reservedBeds) {
        this.sleeping = true;
        this.bedPos = bed;
        reservedBeds.add(bed);

        npc.setPose(EntityPose.SLEEPING);
        npc.setVelocity(Vec3d.ZERO);
        npc.getNavigation().stop();

        npc.setPosition(
                bed.getX() + 0.5,
                bed.getY() + 0.1,
                bed.getZ() + 0.5
        );
    }



    public void tick(PathAwareEntity npc, Set<BlockPos> reservedBeds) {
        if (!sleeping) return;

        World world = npc.getWorld();

        // Bị đánh → thức
        if (npc.hurtTime > 0 || !world.isNight()) {
            wakeUp(npc, reservedBeds);
            return;
        }


        // Giường bị phá
        if (bedPos == null ||
                !(world.getBlockState(bedPos).getBlock() instanceof BedBlock)) {
            wakeUp(npc, reservedBeds);
        }
    }

    public void wakeUp(PathAwareEntity npc, Set<BlockPos> reservedBeds) {
        sleeping = false;
        npc.setPose(EntityPose.STANDING);

        if (bedPos != null) {
            reservedBeds.remove(bedPos);
            bedPos = null;
        }
    }
}
