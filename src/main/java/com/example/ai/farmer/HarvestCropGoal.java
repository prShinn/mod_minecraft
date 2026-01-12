package com.example.ai.farmer;

import com.example.entity.FarmerNpcEntity;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

public class HarvestCropGoal extends Goal {

    private final FarmerNpcEntity npc;
    private BlockPos targetCrop;
    private int taskTicks = 0;
    private static final int MAX_TASK_TICKS = 40;// Nếu làm việc quá lâu thì từ bỏ
    private static final double HARVEST_DISTANCE_SQ = 2.25;

    public HarvestCropGoal(FarmerNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (npc.isSleeping()) return false;
        BlockPos found = npc.findNearestFarmland(); // trả về cropPos chín
        if (found == null) return false;

        var state = npc.getWorld().getBlockState(found);
        if (!(state.getBlock() instanceof CropBlock crop)) return false;
        if (!crop.isMature(state)) return false;

        // lock crop để NPC khác không giành
        if (!npc.reserveCrop(found)) return false;

        targetCrop = found;
        return true;


    }

    @Override
    public void start() {
        taskTicks = 0;
        npc.getNavigation().startMovingTo(targetCrop.getX() + 0.5, targetCrop.getY(), targetCrop.getZ() + 0.5, 1.0);

    }

    @Override
    public void tick() {
        taskTicks++;
        if (!npc.getWorld().isChunkLoaded(targetCrop)) {
            stop();
            return;
        }
        if (npc.squaredDistanceTo(Vec3d.ofCenter(targetCrop)) < 2.5) {
            harvestCrop();
        }
    }

    @Override
    public boolean shouldContinue() {
// Dừng nếu quá lâu hoặc cây không chín nữa
        if (taskTicks > MAX_TASK_TICKS) return false;
        if (targetCrop == null) return false;
        var state = npc.getWorld().getBlockState(targetCrop);
        return state.getBlock() instanceof CropBlock crop
                && crop.isMature(state);

    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        if (targetCrop != null) {
            npc.releaseCrop(targetCrop);
        }

        targetCrop = null;
        taskTicks = 0;
    }

    private void harvestCrop() {
        var world = npc.getWorld();
        var state = world.getBlockState(targetCrop);

        if (state.getBlock() instanceof CropBlock crop) {
            if (crop.isMature(state)) {
                // Break cây (drops items)
                world.breakBlock(targetCrop, true, npc);
//                // Set lại farmland state
                // KHÔNG setBlockState lại crop ở đây
                // trồng lại là nhiệm vụ của PlantSeedGoal
//                world.setBlockState(targetCrop, crop.getDefaultState());
            }
        }
    }
}
