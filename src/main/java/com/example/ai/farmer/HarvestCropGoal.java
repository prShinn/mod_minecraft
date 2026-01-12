package com.example.ai.farmer;

import com.example.entity.FarmerNpcEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;

public class HarvestCropGoal extends Goal {

    private final FarmerNpcEntity npc;
    private BlockPos targetCrop;
    private int taskTicks = 0;
    private static final int MAX_TASK_TICKS = 40;// Nếu làm việc quá lâu thì từ bỏ

    public HarvestCropGoal(FarmerNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (npc.isSleeping()) return false;
        targetCrop = npc.findNearestFarmland();

        // Chỉ harvest nếu là cây chín
        if (targetCrop != null &&
                npc.getWorld().getBlockState(targetCrop).getBlock() instanceof CropBlock crop) {
            return crop.isMature(npc.getWorld().getBlockState(targetCrop));
        }
        return false;

    }

    @Override
    public void start() {
        taskTicks = 0;
        npc.getNavigation().startMovingTo(
                targetCrop.getX(), targetCrop.getY(), targetCrop.getZ(), 1.0);
    }

    @Override
    public void tick() {
        taskTicks++;
        if (npc.squaredDistanceTo(Vec3d.ofCenter(targetCrop)) < 2.5) {
            harvestCrop();
        }
    }

    @Override
    public boolean shouldContinue() {
// Dừng nếu quá lâu hoặc cây không chín nữa
        if (taskTicks > MAX_TASK_TICKS) return false;
        if (targetCrop != null &&
                npc.getWorld().getBlockState(targetCrop).getBlock() instanceof CropBlock crop) {
            return crop.isMature(npc.getWorld().getBlockState(targetCrop));
        }
        return false;
    }
    @Override
    public void stop() {
        npc.getNavigation().stop();
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
                // Set lại farmland state
                world.setBlockState(targetCrop, crop.getDefaultState());
            }
        }
    }
}
