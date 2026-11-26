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
    private long retryAt;
    private int taskTicks = 0;
    private int pickupDelay = 0;
    private BlockPos lastHarvestPos;

    private static final int MAX_TASK_TICKS = 100;// Nếu làm việc quá lâu thì từ bỏ
    private static final double HARVEST_DISTANCE_SQ = 2.25;
    /** Tầm thu hoạch (bình phương, tính từ chân NPC tới tâm ô cây) — khớp điều kiện trong tick(). */
    private static final double HARVEST_REACH_SQ = 2.5;
    private static final int MAX_REPATHS = 3;
    /** Thời gian tối đa = 5s + 0.5s mỗi nút đường đi (đường vòng qua cửa rào dài hơn). */
    private int maxTicks = MAX_TASK_TICKS;
    private int repathAttempts;

    public HarvestCropGoal(FarmerNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (npc.getWorld().getTime() < retryAt) return false;
        retryAt = npc.getWorld().getTime() + 5; // kiểm tra tối đa 4 lần/giây
        BlockPos found = npc.cachedMatureCrop;
        if (found == null || npc.isInventoryFull() || npc.findNearestChest() == null
                || !npc.isNearFarmChest(found.down()) || !npc.getWorld().isChunkLoaded(found)) return false;

        var state = npc.getWorld().getBlockState(found);
        if (!(state.getBlock() instanceof CropBlock crop)) return false;
        if (!crop.isMature(state)) return false;

        // Chỉ bỏ qua ô mà nông dân khác đang thao tác.
        if (npc.isWorkedByAnother(found.down())) return false;

        targetCrop = found;
        return true;
    }

    @Override
    public void start() {
        taskTicks = 0;
        repathAttempts = 0;
        // Tìm đường thật (đi vòng qua cửa rào) trước khi đi; không có đường → bỏ ô này một lúc
        var path = npc.findReachablePath(targetCrop, HARVEST_REACH_SQ);
        if (path == null) {
            npc.skipPlot(targetCrop.down());
            targetCrop = null;
            return;
        }
        maxTicks = MAX_TASK_TICKS + path.getLength() * 10;
        npc.getNavigation().startMovingAlong(path, 1.5F);

    }

    @Override
    public void tick() {
        if (targetCrop == null) return; // start() vừa bỏ ô không có đường
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

        if (npc.isWorkedByAnother(targetCrop.down())) {
            stop();
            return;
        }
        if (npc.squaredDistanceTo(Vec3d.ofCenter(targetCrop)) < HARVEST_REACH_SQ) {
            npc.activeFarmPlot = targetCrop.down();
            harvestCrop();
        } else if (pickupDelay == 0 && npc.getNavigation().isIdle()) {
            // Đứng lại giữa đường (bị chặn/đường cụt) → tính lại đường, quá 3 lần thì bỏ ô này
            var path = ++repathAttempts > MAX_REPATHS ? null : npc.findReachablePath(targetCrop, HARVEST_REACH_SQ);
            if (path == null) {
                npc.skipPlot(targetCrop.down());
                stop();
                return;
            }
            npc.getNavigation().startMovingAlong(path, 1.5F);
        }
    }

    @Override
    public boolean shouldContinue() {
// Dừng nếu quá lâu hoặc cây không chín nữa
//        if (taskTicks > MAX_TASK_TICKS) return false;
//        if (targetCrop == null || !npc.getWorld().isChunkLoaded(targetCrop)) return false;
//        var state = npc.getWorld().getBlockState(targetCrop);
//        return state.getBlock() instanceof CropBlock crop
//                && crop.isMature(state);
        if (targetCrop == null) return false;
        if (taskTicks > maxTicks) {
            if (pickupDelay == 0) npc.skipPlot(targetCrop.down()); // hết giờ mà chưa tới nơi
            return false;
        }

        // ✅ vẫn tiếp tục nếu đang chờ pickup
        if (pickupDelay > 0) return true;

        var state = npc.getWorld().getBlockState(targetCrop);
        return state.getBlock() instanceof CropBlock crop
                && crop.isMature(state);

    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        npc.activeFarmPlot = null;
        retryAt = npc.getWorld().getTime() + 10;

        targetCrop = null;
        lastHarvestPos = null;
        pickupDelay = 0;
        taskTicks = 0;
    }

    private void harvestCrop() {
        var world = npc.getWorld();
        var state = world.getBlockState(targetCrop);

        if (!(state.getBlock() instanceof CropBlock crop)) return;
        if (!crop.isMature(state)) {
            taskTicks++;
            return;
        }

        npc.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);

        // ✅ Lấy item thay vì vứt ra đất
        if (world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            java.util.List<ItemStack> drops = Block.getDroppedStacks(
                    state,
                    serverWorld,
                    targetCrop,
                    null,
                    npc,
                    ItemStack.EMPTY
            );
            
            for (ItemStack stack : drops) {
                boolean added = false;
                for (int i = 0; i < npc.getCargo().size(); i++) {
                    ItemStack slot = npc.getCargo().getStack(i);
                    if (slot.isEmpty()) {
                        npc.getCargo().setStack(i, stack.copy());
                        stack.setCount(0);
                        added = true;
                        break;
                    }
                    if (ItemStack.canCombine(slot, stack) && slot.getCount() < slot.getMaxCount()) {
                        int canMove = Math.min(stack.getCount(), slot.getMaxCount() - slot.getCount());
                        slot.increment(canMove);
                        stack.decrement(canMove);
                        if (stack.isEmpty()) {
                            added = true;
                            break;
                        }
                    }
                }
                if (!added && !stack.isEmpty()) {
                    // Nếu túi đầy thì vứt ra đất
                    Block.dropStack(world, targetCrop, stack);
                }
            }
        }

        // ✅ Reset crop (để PlantSeedGoal trồng lại)
        world.setBlockState(
                targetCrop,
                Blocks.AIR.getDefaultState(),
                Block.NOTIFY_ALL
        );

        lastHarvestPos = targetCrop.toImmutable();
        pickupDelay = 0; // Không cần chờ nhặt nữa

        // ✅ Cập nhật ngay lập tức bộ nhớ để PlantSeedGoal trồng ngay
        npc.cachedEmptyFarmland = targetCrop.down().toImmutable();
        npc.cachedMatureCrop = null;
        
        // Scan ngay lập tức ở tick tiếp theo
        // Ta cần gọi 1 hàm hoặc reset field. Vì cooldown bị ẩn trong entity, 
        // tốt nhất là ép scanFarmland chạy luôn.
        npc.scanFarmland();

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

            boolean addedAll = addToInventory(npc.getCargo(), stack);

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
