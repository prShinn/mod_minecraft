package com.example.ai.farmer;

import com.example.entity.FarmerNpcEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.List;

public class HarvestCropGoal extends Goal {

    private final FarmerNpcEntity npc;
    private BlockPos targetCrop;
    private int taskTicks = 0;
    private int pickupDelay = 0;
    private BlockPos lastHarvestPos;

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
        npc.getNavigation().startMovingTo(targetCrop.getX() + 0.5, targetCrop.getY(), targetCrop.getZ() + 0.5, 1.5F);

    }

    @Override
    public void tick() {
        taskTicks++;
        if (!npc.getWorld().isChunkLoaded(targetCrop)) {
            stop();
            return;
        }
        if (pickupDelay > 0) {
            pickupDelay--;
            if (pickupDelay == 0 && lastHarvestPos != null) {
                pickupNearbyDrops();
            }
        }


        if (npc.squaredDistanceTo(Vec3d.ofCenter(targetCrop)) < 2.5) {
            harvestCrop();
        }
    }

    @Override
    public boolean shouldContinue() {
// Dừng nếu quá lâu hoặc cây không chín nữa
//        if (taskTicks > MAX_TASK_TICKS) return false;
//        if (targetCrop == null) return false;
//        var state = npc.getWorld().getBlockState(targetCrop);
//        return state.getBlock() instanceof CropBlock crop
//                && crop.isMature(state);
        if (taskTicks > MAX_TASK_TICKS) return false;
        if (targetCrop == null) return false;

        // ✅ vẫn tiếp tục nếu đang chờ pickup
        if (pickupDelay > 0) return true;

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
        lastHarvestPos = null;
        pickupDelay = 0;
        taskTicks = 0;
    }

    private void harvestCrop() {
        var world = npc.getWorld();
        var state = world.getBlockState(targetCrop);

        if (!(state.getBlock() instanceof CropBlock crop)) return;
        if (!crop.isMature(state)) return;

        // ✅ Drop item CHẮC CHẮN spawn ItemEntity
        Block.dropStacks(
                state,
                world,
                targetCrop,
                null,
                npc,
                ItemStack.EMPTY
        );

        // ✅ Reset crop (để PlantSeedGoal trồng lại)
        world.setBlockState(
                targetCrop,
                Blocks.AIR.getDefaultState(),
                Block.NOTIFY_ALL
        );

        lastHarvestPos = targetCrop.toImmutable();
        pickupDelay = 2;

//        var world = npc.getWorld();
//        var state = world.getBlockState(targetCrop);
//
//        if (state.getBlock() instanceof CropBlock crop) {
//            if (crop.isMature(state)) {
//                // Break cây (drops items)
//                world.breakBlock(targetCrop, true, npc);
//                lastHarvestPos = targetCrop.toImmutable();
//                pickupDelay = 2; // đợi 2 tick
////                // Set lại farmland state
//                // KHÔNG setBlockState lại crop ở đây
//                // trồng lại là nhiệm vụ của PlantSeedGoal
////                world.setBlockState(targetCrop, crop.getDefaultState());
//            }
//        }
    }

    private void pickupNearbyDrops() {
        List<ItemEntity> drops = npc.getWorld().getEntitiesByClass(
                ItemEntity.class,
                new Box(lastHarvestPos).expand(1.5),
                item -> item.isAlive()
        );

        for (ItemEntity item : drops) {
            ItemStack stack = item.getStack();

            boolean addedAll = addToInventory(npc.getInventory(), stack);

            if (addedAll) {
                item.discard();
            }
        }
    }


    private boolean addToInventory(SimpleInventory inv, ItemStack stack) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack slot = inv.getStack(i);

            if (slot.isEmpty()) {
                inv.setStack(i, stack.copy());
                stack.setCount(0);
                return true;
            }

            if (ItemStack.canCombine(slot, stack)
                    && slot.getCount() < slot.getMaxCount()) {

                int move = Math.min(
                        stack.getCount(),
                        slot.getMaxCount() - slot.getCount()
                );

                slot.increment(move);
                stack.decrement(move);

                if (stack.isEmpty()) return true;
            }
        }
        return stack.isEmpty();
    }


}
