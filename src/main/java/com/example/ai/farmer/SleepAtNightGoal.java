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
    private static final int SEARCH_RADIUS = 10;
    private static final int FIND_BED_COOLDOWN = 30;
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
        if (!npc.memory.isNight(npc.getWorld())) return false;
        if (npc.memory.sleeping) return false;
        bedPos = findNearestBed();
        return bedPos != null;
    }

    @Override
    public void start() {
        npc.memory.bedPos = bedPos;
        npc.getNavigation().startMovingTo(
                bedPos.getX() + 0.5,
                bedPos.getY(),
                bedPos.getZ() + 0.5,
                1.0
        );

    }

    @Override
    public void tick() {
        if (npc.memory.sleeping) return;

        if (!isBedValid(bedPos) || !isBedFree(bedPos)) {
            releaseBed();
            stop();
            return;
        }
        if (npc.squaredDistanceTo(Vec3d.ofCenter(bedPos)) < 1.4) {
            sleep();
        }
    }

    @Override
    public boolean shouldContinue() {
        return !npc.memory.sleeping
                && npc.memory.isNight(npc.getWorld())
                && bedPos != null;
    }

    @Override
    public void stop() {
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
        npc.memory.sleeping = true;
        npc.memory.bedPos = bedPos;
    }

    public void wakeUp() {
        if (!npc.memory.sleeping) return;

        npc.setPose(EntityPose.STANDING);
        npc.memory.sleeping = false;

        releaseBed();
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
                center.add(-10, -2, -10),
                center.add(10, 2, 10))) {
            BlockState state = npc.getWorld().getBlockState(pos);
            if (!(state.getBlock() instanceof BedBlock)) continue;
            // Luôn dùng FOOT của giường
            if (state.get(BedBlock.PART) == BedPart.HEAD) {
                pos = pos.offset(state.get(BedBlock.FACING).getOpposite());
            }
            if (isBedFree(pos) && !npc.getReservedBeds().contains(pos)) {
                npc.getReservedBeds().add(pos);
                cachedBedPos = pos;
                return pos;
            }
        }
        cachedBedPos = null; // không tìm thấy
        return null;
    }
    private boolean isBedValid(BlockPos pos) {
        return npc.getWorld().getBlockState(pos).getBlock() instanceof BedBlock;
    }
    private boolean isBedFree(BlockPos bedPos) {
        Box box = new Box(bedPos).expand(0.5);

        List<LivingEntity> sleepers =
                npc.getWorld().getEntitiesByClass(
                        LivingEntity.class,
                        box,
                        e -> e.getPose() == EntityPose.SLEEPING
                );
        return sleepers.isEmpty();
    }
    private void releaseBed() {
        if (bedPos != null) {
            npc.getReservedBeds().remove(bedPos);
        }
        npc.memory.bedPos = null;
    }

}
