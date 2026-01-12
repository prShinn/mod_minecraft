package com.example.ai.farmer;

import com.example.entity.FarmerNpcEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class PlantSeedGoal extends Goal {

    private final FarmerNpcEntity npc;
    private BlockPos farmland;

    public PlantSeedGoal(FarmerNpcEntity npc) {
        this.npc = npc;
    }

    @Override
    public boolean canStart() {
        farmland = findEmptyFarmland();
        return farmland != null && npc.hasSeeds();
    }

    @Override
    public void start() {
        npc.getNavigation().startMovingTo(
                farmland.getX(), farmland.getY(), farmland.getZ(), 1.0);
    }

    @Override
    public void tick() {
        if (npc.squaredDistanceTo(Vec3d.ofCenter(farmland)) < 2) {
            npc.plantSeedAt(farmland);
            npc.memory.lastFarmPos = farmland;
            npc.memory.resetIdle();
        }
    }

    private BlockPos findEmptyFarmland() {
        BlockPos center = npc.getBlockPos();
        for (BlockPos pos : BlockPos.iterate(center.add(-8, -1, -8), center.add(8, 1, 8))) {
            BlockState soil = npc.getWorld().getBlockState(pos);
            BlockState above = npc.getWorld().getBlockState(pos.up());

            if (soil.getBlock() instanceof FarmlandBlock && above.isAir()) {
                return pos.up().toImmutable();
            }
        }
        return null;
    }
}
