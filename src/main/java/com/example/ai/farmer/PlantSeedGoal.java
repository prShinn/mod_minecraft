package com.example.ai.farmer;

import com.example.entity.FarmerNpcEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

public class PlantSeedGoal extends Goal {

    private final FarmerNpcEntity npc;
    private BlockPos farmland;
    private int taskTicks;

    private static final int MAX_TASK_TICKS = 80;
    private static final int FIND_DISTANCE = 80;
    private static final double PLANT_DIST_SQ = 2.25;

    public PlantSeedGoal(FarmerNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (npc.isSleeping()) return false;
        if (!npc.hasSeeds()) return false;

        BlockPos found = findEmptyFarmland();
        if (found == null) return false;

        // lock farmland
        if (!npc.reserveFarmland(found)) return false;

        farmland = found;
        return true;

    }

    @Override
    public void start() {
        taskTicks = 0;
        npc.getNavigation().startMovingTo(
                farmland.getX() + 0.5,
                farmland.getY(),
                farmland.getZ() + 0.5,
                1.3f
        );
    }

    @Override
    public void tick() {
        taskTicks++;
        if (!npc.getWorld().isChunkLoaded(farmland)) {
            stop();
            return;
        }
        if (npc.squaredDistanceTo(Vec3d.ofCenter(farmland)) < PLANT_DIST_SQ) {
            npc.plantSeedAt(farmland);
            npc.memory.lastFarmPos = farmland;
            npc.memory.resetIdle();
            stop();
        }
    }

    @Override
    public boolean shouldContinue() {
        if (taskTicks > MAX_TASK_TICKS) return false;
        if (farmland == null) return false;

        var world = npc.getWorld();
        return world.getBlockState(farmland).getBlock() instanceof FarmlandBlock
                && world.getBlockState(farmland.up()).isAir();
    }
    @Override
    public void stop() {
        npc.getNavigation().stop();
        if (farmland != null) {
            npc.releaseFarmland(farmland);
        }
        farmland = null;
        taskTicks = 0;
    }
    private BlockPos findEmptyFarmland() {
        BlockPos center = npc.getBlockPos();
        var world = npc.getWorld();

        for (BlockPos pos : BlockPos.iterate(center.add(-FIND_DISTANCE, -1, -FIND_DISTANCE), center.add(FIND_DISTANCE, 1, FIND_DISTANCE))) {
            if (!world.isChunkLoaded(pos)) continue;

            if (world.getBlockState(pos).getBlock() instanceof FarmlandBlock
                    && world.getBlockState(pos.up()).isAir()) {
                return pos.toImmutable(); // TRẢ VỀ farmlandPos
            }
        }
        return null;
    }
}
