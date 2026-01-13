package com.example.ai.farmer;

import com.example.entity.FarmerNpcEntity;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.List;

public class SleepAtNightGoal extends Goal {
    private static final int SEARCH_RADIUS = 16;
    private static final int FIND_BED_COOLDOWN = 120;
    private int cooldownFindBed = 0;
    private BlockPos cachedBedPos = null;
    private final FarmerNpcEntity npc;
    private BlockPos bedPos;

    public SleepAtNightGoal(FarmerNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        return npc.getWorld().isNight()
                && !npc.sleeping.isSleeping()
                && findNearestBed() != null;

    }

    @Override
    public void start() {
        npc.getNavigation().startMovingTo(
                bedPos.getX() + 0.5,
                bedPos.getY(),
                bedPos.getZ() + 0.5,
                1.0
        );

    }

    @Override
    public void tick() {
        if (npc.sleeping.isSleeping()) return;
        if (!isBedValid(bedPos)) {
            stop();
            return;
        }
        if (npc.squaredDistanceTo(Vec3d.ofCenter(bedPos)) < 1.4) {
            npc.sleeping.startSleeping(npc, bedPos, npc.getReservedBeds());
        }
    }

    @Override
    public boolean shouldContinue() {
        return !npc.sleeping.isSleeping()
                && npc.getWorld().isNight()
                && bedPos != null;
    }

    @Override
    public void stop() {
        if (bedPos != null && !npc.sleeping.isSleeping()) {
            npc.getReservedBeds().remove(bedPos); // Cleanup nếu chưa ngủ
        }
        npc.getNavigation().stop();
        bedPos = null;
    }

    private void sleep() {
        npc.getNavigation().stop();
        npc.setPose(EntityPose.SLEEPING);
        npc.setVelocity(Vec3d.ZERO);
        npc.setPosition(
                bedPos.getX() + 0.5,
                bedPos.getY() + 0.1,
                bedPos.getZ() + 0.5
        );
        npc.memory.bedPos = bedPos;
    }

    private BlockPos findNearestBed() {
        if (cachedBedPos != null && isBedValid(cachedBedPos) && isBedFree(cachedBedPos)) {
            return cachedBedPos; // dùng lại giường cũ
        }
        if (cooldownFindBed > 0) {
            cooldownFindBed--;
            return cachedBedPos != null ? cachedBedPos : null; // chờ cooldown
        }
        cooldownFindBed = FIND_BED_COOLDOWN;
        BlockPos center = npc.getBlockPos();

        for (BlockPos pos : BlockPos.iterate(
                center.add(-SEARCH_RADIUS, -2, -SEARCH_RADIUS),
                center.add(SEARCH_RADIUS, 2, SEARCH_RADIUS))) {
            if (!npc.getWorld().isChunkLoaded(pos)) continue;

            BlockState state = npc.getWorld().getBlockState(pos);
            if (!(state.getBlock() instanceof BedBlock)) continue;
            // Luôn dùng FOOT của giường
            if (state.get(BedBlock.PART) == BedPart.HEAD) {
                pos = pos.offset(state.get(BedBlock.FACING).getOpposite());
            }
            if (isBedFree(pos) && !npc.getReservedBeds().contains(pos)) {
                npc.getReservedBeds().add(pos);
                cachedBedPos = pos.toImmutable();
                return pos;
            }
        }
        cachedBedPos = null; // không tìm thấy
        return null;
    }

    private boolean isBedValid(BlockPos pos) {
        return pos != null
                && npc.getWorld().getBlockState(pos).getBlock() instanceof BedBlock;
    }

    private boolean isBedFree(BlockPos bedPos) {
        if (npc.getReservedBeds().contains(bedPos)) {
            return false;
        }
        Box box = new Box(bedPos).expand(0.5);

        List<LivingEntity> sleepers =
                npc.getWorld().getEntitiesByClass(
                        LivingEntity.class,
                        box,
                        e -> e.getPose() == EntityPose.SLEEPING
                );
        return sleepers.isEmpty();
    }

}
