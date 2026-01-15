package com.example.entity.base;

import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public class NpcSleepingComponent {
    private boolean sleeping = false;
    private BlockPos bedPos;
    private BlockPos targetBed = null;
    private BlockPos cachedBedPos = null;
    private int cooldownFindBed = 0;
    private boolean shouldWander = false;
    private int wanderTime = 0;
    private int moveTicks = 0;

    private int searchRadius = 16;
    private int findBedCooldown = 120;
    private int wanderDuration = 100;
    private static final Map<World, Set<BlockPos>> RESERVED_BEDS_MAP = new HashMap<>();

    public static Set<BlockPos> getReservedBeds(World world) {
        synchronized (RESERVED_BEDS_MAP) {
            return RESERVED_BEDS_MAP.computeIfAbsent(world, w -> new HashSet<>());
        }
    }


    public boolean isSleeping() {
        return sleeping;
    }

    public BlockPos getTargetBed() {
        return targetBed;
    }

    public NpcSleepingComponent withSearchRadius(int radius) {
        this.searchRadius = radius;
        return this;
    }

    public NpcSleepingComponent withCooldown(int cooldown) {
        this.findBedCooldown = cooldown;
        return this;
    }

    public NpcSleepingComponent withWanderDuration(int duration) {
        this.wanderDuration = duration;
        return this;
    }

    public boolean shouldFindBed(PathAwareEntity npc) {
        return npc.getWorld().isNight()
                && !sleeping
                && targetBed == null;
    }


    public BlockPos findAndReserveBed(PathAwareEntity npc) {
        Set<BlockPos> reservedBeds = getReservedBeds(npc.getWorld());

        shouldWander = false;
        wanderTime = 0;

        if (cachedBedPos != null && isBedValid(npc, cachedBedPos) && isBedFree(npc, cachedBedPos, reservedBeds)) {
            synchronized (reservedBeds) {
                if (!reservedBeds.contains(cachedBedPos)) {
                    reservedBeds.add(cachedBedPos);
                    targetBed = cachedBedPos;
                }
            }
            return cachedBedPos;
        }

        if (cooldownFindBed > 0) {
            cooldownFindBed--;
            if (cachedBedPos != null && isBedValid(npc, cachedBedPos) && isBedFree(npc, cachedBedPos, reservedBeds)) {
                synchronized (reservedBeds) {
                    if (!reservedBeds.contains(cachedBedPos)) {
                        reservedBeds.add(cachedBedPos);
                        targetBed = cachedBedPos;
                    }
                }
                return cachedBedPos;
            }
            cachedBedPos = null;
            return null;
        }

        return scanForNewBed(npc, reservedBeds);
    }

    public boolean startMovingToBed(PathAwareEntity npc) {
        if (targetBed == null) return false;
        moveTicks = 0;

        shouldWander = false;
        wanderTime = 0;

        return npc.getNavigation().startMovingTo(
                targetBed.getX() + 0.5,
                targetBed.getY(),
                targetBed.getZ() + 0.5,
                1.0
        );
    }

    public void tickMovingToBed(PathAwareEntity npc) {
        if (sleeping || targetBed == null) return;
        moveTicks++;

        // ⛔ CHƯA check idle khi path chưa build
        if (moveTicks < 10) return;
        Set<BlockPos> reservedBeds = getReservedBeds(npc.getWorld());

        if (npc.getNavigation().isIdle() || !isBedValid(npc, targetBed) || !isBedFree(npc, targetBed, reservedBeds)) {
            cancelMovingToBed(npc);
            startWandering();
            return;
        }

        if (npc.squaredDistanceTo(Vec3d.ofCenter(targetBed)) < 1.4) {
            startSleeping(npc, targetBed);
        }

    }

    public void tickWandering() {
        if (!shouldWander) return;

        if (wanderTime > 0) {
            wanderTime--;
        } else {
            shouldWander = false;
        }
    }

    public void startSleeping(PathAwareEntity npc, BlockPos bed) {
        Set<BlockPos> reservedBeds = getReservedBeds(npc.getWorld());

        this.sleeping = true;
        this.bedPos = bed;
        this.targetBed = null;
        this.shouldWander = false;
        this.wanderTime = 0;

        synchronized (reservedBeds) {
            if (!reservedBeds.contains(bed)) {
                reservedBeds.add(bed);
            }
        }

        npc.setPose(EntityPose.SLEEPING);
        npc.setVelocity(Vec3d.ZERO);
        npc.getNavigation().stop();

        npc.setPosition(
                bed.getX() + 0.5,
                bed.getY() + 0.1,
                bed.getZ() + 0.5
        );
    }

    public void cancelMovingToBed(PathAwareEntity npc) {
        if (targetBed != null && !sleeping) {
            Set<BlockPos> reservedBeds = getReservedBeds(npc.getWorld());
            synchronized (reservedBeds) {
                reservedBeds.remove(targetBed);
            }
        }
        npc.getNavigation().stop();
        targetBed = null;
        cachedBedPos = null;

    }

    public void tick(PathAwareEntity npc) {
        tickWandering();
//
//        if (!sleeping) return;
//
//        World world = npc.getWorld();
//
//        if (npc.hurtTime > 0 || !world.isNight()) {
//            wakeUp(npc);
//            return;
//        }
//
//        if (bedPos == null || !(world.getBlockState(bedPos).getBlock() instanceof BedBlock)) {
//            wakeUp(npc);
//        }
    }

    private void startWandering() {
        shouldWander = true;
        wanderTime = wanderDuration;
        cooldownFindBed = findBedCooldown;
    }

    public void wakeUp(PathAwareEntity npc) {
        sleeping = false;
        npc.setPose(EntityPose.STANDING);
        cachedBedPos = null;
        if (bedPos != null) {
            Set<BlockPos> reservedBeds = getReservedBeds(npc.getWorld());
            synchronized (reservedBeds) {
                reservedBeds.remove(bedPos);
            }
            bedPos = null;
        }

        shouldWander = false;
        wanderTime = 0;
    }

    private BlockPos scanForNewBed(PathAwareEntity npc, Set<BlockPos> reservedBeds) {
        cooldownFindBed = findBedCooldown;
        BlockPos center = npc.getBlockPos();
        World world = npc.getWorld();
        double radiusSquared = searchRadius * searchRadius;

        System.out.println("[SleepComponent] 🔍 Quét giường từ " + center + ", bán kính: " + searchRadius);

        BlockPos nearestBed = null;
        double nearestDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.iterate(
                center.add(-searchRadius, -2, -searchRadius),
                center.add(searchRadius, 2, searchRadius))) {

            double distSquared = center.getSquaredDistance(pos);
            if (distSquared > radiusSquared) continue;

            if (!world.isChunkLoaded(pos)) continue;

            BlockState state = world.getBlockState(pos);
            if (!(state.getBlock() instanceof BedBlock)) continue;

            System.out.println("[SleepComponent] ✅ Tìm thấy giường tại: " + pos);

            BlockPos footPos = pos;
            if (state.get(BedBlock.PART) == BedPart.HEAD) {
                footPos = pos.offset(state.get(BedBlock.FACING).getOpposite());
                if (!world.isChunkLoaded(footPos)) continue;
            }
            if (isBedFree(npc, footPos, reservedBeds)) {
                if (distSquared < nearestDist) {
                    nearestDist = distSquared;
                    nearestBed = footPos;
                }
            }
        }
        if (nearestBed != null) {
            System.out.println("[SleepComponent] ✅ Đặt target bed: " + nearestBed);
            synchronized (reservedBeds) {
                reservedBeds.add(nearestBed);
            }
            cachedBedPos = nearestBed.toImmutable();
            targetBed = nearestBed.toImmutable();
            return nearestBed;
        }
        System.out.println("[SleepComponent] ❌ Không tìm thấy giường nào");
        return null;
    }

    private boolean isBedValid(PathAwareEntity npc, BlockPos pos) {
        return pos != null
                && npc.getWorld().getBlockState(pos).getBlock() instanceof BedBlock;
    }

    private boolean isBedFree(PathAwareEntity npc, BlockPos bedPos, Set<BlockPos> reservedBeds) {
        synchronized (reservedBeds) {
            if (reservedBeds.contains(bedPos) && !bedPos.equals(targetBed)) {
                return false;
            }
        }
        Box box = new Box(bedPos).expand(0.5);
        List<LivingEntity> occupants = npc.getWorld().getEntitiesByClass(
                LivingEntity.class,
                box,
                e -> e.getPose() == EntityPose.SLEEPING
        );
        return occupants.isEmpty();
    }

    public boolean shouldWander() {
        return shouldWander;
    }
}
