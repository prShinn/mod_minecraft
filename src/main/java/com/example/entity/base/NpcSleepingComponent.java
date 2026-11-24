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

    private int searchRadius = 16;
    private BlockPos searchCenter;

    public void setSearchCenter(BlockPos center) {
        searchCenter = center;
    }
    private int findBedCooldown = 120;
    private int wanderDuration = 100;
    private static final Map<World, Set<BlockPos>> RESERVED_BEDS_MAP = new WeakHashMap<>();
    public static Set<BlockPos> getReservedBeds(World world) {
        synchronized (RESERVED_BEDS_MAP) {
            return RESERVED_BEDS_MAP.computeIfAbsent(world, w -> new HashSet<>());
        }
    }
    public static void clearReservationsForWorld(World world) {
        synchronized (RESERVED_BEDS_MAP) {
            RESERVED_BEDS_MAP.remove(world);
        }
    }
    public static void clearAllReservations() {
        synchronized (RESERVED_BEDS_MAP) {
            RESERVED_BEDS_MAP.clear();
        }
    }


    public boolean isSleeping() {
        return sleeping;
    }

    public BlockPos getBedPos() {
        return bedPos;
    }
    public BlockPos getTargetBed() {
        return targetBed;
    }
    public boolean shouldWander() {
        return shouldWander;
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
        if (!npc.getWorld().isNight() || sleeping) {
            return false;
        }

        if (targetBed != null) {
            return false;
        }

        if (shouldWander && wanderTime > 0) {
            return false;
        }

        return true;
    }
    /**
     * Tìm và reserve giường gần nhất
     * @return BlockPos của giường hoặc null
     */
    public BlockPos findAndReserveBed(PathAwareEntity npc) {
        Set<BlockPos> reservedBeds = getReservedBeds(npc.getWorld());

        shouldWander = false;
        wanderTime = 0;

        // Case 1: Cache hợp lệ
        if (cachedBedPos != null && isBedValid(npc, cachedBedPos) && isBedFree(npc, cachedBedPos, reservedBeds)) {
            synchronized (reservedBeds) {
                if (!reservedBeds.contains(cachedBedPos)) {
                    reservedBeds.add(cachedBedPos);
                    targetBed = cachedBedPos;
                }
            }
            return cachedBedPos;
        }

        // Case 2: Đang cooldown
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

        // Case 3: Scan giường mới
        BlockPos found = scanForNewBed(npc, reservedBeds);

        if (found == null) {
            startWandering();
        }

        return found;
    }
    public boolean startMovingToBed(PathAwareEntity npc) {
        if (targetBed == null) return false;

        shouldWander = false;
        wanderTime = 0;

        return npc.getNavigation().startMovingTo(
                targetBed.getX() + 0.5,
                targetBed.getY(),
                targetBed.getZ() + 0.5,
                1.0
        );
    }



    /**
     * Tick khi đang di chuyển đến giường
     */
    public void tickMovingToBed(PathAwareEntity npc) {
        if (sleeping || targetBed == null) return;

        // Reservation already belongs to this NPC. Check actual occupancy.
        if (!isBedValid(npc, targetBed) || !isBedUnoccupied(npc, targetBed)) {
            cancelMovingToBed(npc);
            startWandering();
            return;
        }

        // Đã đến gần giường
        if (npc.squaredDistanceTo(Vec3d.ofCenter(targetBed)) < 1.4) {
            startSleeping(npc, targetBed);
        } else if (npc.getNavigation().isIdle()) {
            cancelMovingToBed(npc);
            startWandering();
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
    /**
     * Bắt đầu ngủ tại giường
     */
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


    /**
     * Hủy di chuyển đến giường
     */
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

        if (!sleeping) return;

        World world = npc.getWorld();

        if (npc.hurtTime > 0 || npc.getTarget() != null || !world.isNight()) {
            wakeUp(npc);
            return;
        }

        if (bedPos == null || !(world.getBlockState(bedPos).getBlock() instanceof BedBlock)) {
            wakeUp(npc);
        }
    }
    private void startWandering() {
        shouldWander = true;
        wanderTime = wanderDuration;
        cooldownFindBed = findBedCooldown;
    }
    /**
     * Thức dậy
     */
    public void wakeUp(PathAwareEntity npc) {
        sleeping = false;
        npc.setPose(EntityPose.STANDING);

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


    // ===================================
    // PRIVATE HELPERS
    // ===================================

    private BlockPos scanForNewBed(PathAwareEntity npc, Set<BlockPos> reservedBeds) {
        cooldownFindBed = findBedCooldown;
        BlockPos center = searchCenter == null ? npc.getBlockPos() : searchCenter;
        World world = npc.getWorld();
        double radiusSquared = searchRadius * searchRadius;

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

            BlockPos footPos = pos;
            if (state.get(BedBlock.PART) == BedPart.HEAD) {
                footPos = pos.offset(state.get(BedBlock.FACING).getOpposite());
                if (!world.isChunkLoaded(footPos)) continue;
            }

            if (isBedFree(npc, footPos, reservedBeds)) {
                if (distSquared < nearestDist) {
                    nearestDist = distSquared;
                    nearestBed = footPos.toImmutable();
                }
            }
        }

        if (nearestBed != null) {
            synchronized (reservedBeds) {
                reservedBeds.add(nearestBed);
            }
            cachedBedPos = nearestBed.toImmutable();
            targetBed = nearestBed.toImmutable();
            return nearestBed;
        }

        cachedBedPos = null;
        return null;
    }

    private boolean isBedValid(PathAwareEntity npc, BlockPos pos) {
        if (pos == null || !npc.getWorld().isChunkLoaded(pos)) return false;
        BlockPos center = searchCenter == null ? npc.getBlockPos() : searchCenter;
        return center.getSquaredDistance(pos) <= searchRadius * searchRadius
                && npc.getWorld().getBlockState(pos).getBlock() instanceof BedBlock;
    }

    private boolean isBedFree(PathAwareEntity npc, BlockPos bedPos, Set<BlockPos> reservedBeds) {
        synchronized (reservedBeds) {
            if (reservedBeds.contains(bedPos)) {
                return false;
            }
        }

        return isBedUnoccupied(npc, bedPos);
    }

    private boolean isBedUnoccupied(PathAwareEntity npc, BlockPos bedPos) {
        Box box = new Box(bedPos).expand(0.5);
        List<LivingEntity> occupants = npc.getWorld().getEntitiesByClass(
                LivingEntity.class,
                box,
                e -> e.getPose() == EntityPose.SLEEPING
        );

        return occupants.isEmpty();

    }
}
