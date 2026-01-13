package com.example.ai.farmer;

import com.example.entity.FarmerNpcEntity;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;
import net.minecraft.block.BlockState;

public class DepositToChestGoal extends Goal {

    private final FarmerNpcEntity npc;
    private BlockPos chestPos;
    private int taskTicks = 0;
    private static final int MAX_TASK_TICKS = 60;
    private static final int SEARCH_RADIUS = 18;

    public DepositToChestGoal(FarmerNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Control.MOVE));

    }
    private boolean hasItems() {
        for (int i = 0; i < npc.foodInventory.size(); i++) {
            if (!npc.foodInventory.getStack(i).isEmpty()) return true;
        }
        return false;
    }
    @Override
    public boolean canStart() {
        if (npc.memory.sleeping) return false;
// Check xem inventory có item chưa
//        boolean hasItems = false;
//        for (int i = 0; i < npc.foodInventory.size(); i++) {
//            if (!npc.foodInventory.getStack(i).isEmpty()) {
//                hasItems = true;
//                break;
//            }
//        }
        if (!hasItems()) return false;

        chestPos = findNearestChest();
        return chestPos != null;
    }

    @Override
    public void start() {
        taskTicks = 0;
        npc.getNavigation().startMovingTo(
                chestPos.getX() + 0.5,
                chestPos.getY(),
                chestPos.getZ() + 0.5,
                1.3);
        npc.reserveChest(chestPos);
    }

    @Override
    public void tick() {
        taskTicks++;
        if (taskTicks > MAX_TASK_TICKS || !npc.getWorld().isChunkLoaded(chestPos)) {
            stop();
            return;
        }
        // Nếu đến gần rương thì cất items
        if (npc.squaredDistanceTo(Vec3d.ofCenter(chestPos)) < 2.5) {
            depositItems();
            stop();
        }
    }

    //    @Override
//    public boolean shouldContinue() {
//        return chestPos != null && npc.getNavigation().isFollowingPath() && hasItems();
//    }
    @Override
    public boolean shouldContinue() {
        return chestPos != null && taskTicks < MAX_TASK_TICKS;
    }


    @Override
    public void stop() {
        npc.getNavigation().stop();
        if (chestPos != null) {
            npc.releaseChest(chestPos);
        }
        chestPos = null;
        taskTicks = 0;
    }



    private void depositItems() {
        BlockEntity be = npc.getWorld().getBlockEntity(chestPos);
        Inventory chestInv = null;

        if (be instanceof ChestBlockEntity) {
            BlockState state = npc.getWorld().getBlockState(chestPos);

            if (state.getBlock() instanceof ChestBlock chestBlock) {
                chestInv = ChestBlock.getInventory(
                        chestBlock,
                        state,
                        npc.getWorld(),
                        chestPos,
                        true
                );
            }
        }

        if (chestInv == null) {
            // Nếu không phải chest, check xem có phải Inventory không
            if (be instanceof Inventory) {
                chestInv = (Inventory) be;
            } else {
                return; // Không phải chest hoặc inventory
            }
        }


        // Transfer tất cả items từ NPC inventory vào rương
        for (int i = 0; i < npc.foodInventory.size(); i++) {
            ItemStack npcStack = npc.foodInventory.getStack(i);
            if (npcStack.isEmpty()) continue;

            // Tìm slot trống trong rương
            for (int j = 0; j < chestInv.size(); j++) {
                ItemStack chestStack = chestInv.getStack(j);

                if (chestStack.isEmpty()) {
                    // Slot trống, cất vào
                    chestInv.setStack(j, npcStack.copy());
                    npc.foodInventory.setStack(i, ItemStack.EMPTY);
                    break;
                } else if (ItemStack.canCombine(chestStack, npcStack) &&
                        chestStack.getCount() < chestStack.getMaxCount()) {
                    // Cùng item, add vào stack hiện tại
                    int canAdd = chestStack.getMaxCount() - chestStack.getCount();
                    int toAdd = Math.min(canAdd, npcStack.getCount());
                    chestStack.increment(toAdd);
                    npcStack.decrement(toAdd);

                    if (npcStack.isEmpty()) {
                        npc.foodInventory.setStack(i, ItemStack.EMPTY);
                        break;
                    }
                }
            }

            // Nếu rương đầy, cập nhật lại stack
            if (!npcStack.isEmpty()) {
                npc.foodInventory.setStack(i, npcStack);
            }
        }

        npc.memory.lastChestPos = chestPos;
    }

    private BlockPos findNearestChest() {
        BlockPos center = npc.getBlockPos();
        double closestDist = Double.MAX_VALUE;
        BlockPos closestChest = null;
        World world = npc.getWorld();

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (!world.isChunkLoaded(pos)) continue;

                    BlockEntity be = world.getBlockEntity(pos);

                    // Check xem có phải chest và chưa bị reserve
                    if (be instanceof ChestBlockEntity || be instanceof net.minecraft.block.entity.BarrelBlockEntity) {
                        // Check chest chưa bị reserve
                        if (npc.isChestReserved(pos)) continue;

                        double dist = npc.squaredDistanceTo(Vec3d.ofCenter(pos));
                        if (dist < closestDist) {
                            closestDist = dist;
                            closestChest = pos;
                        }
                    }
                }
            }
        }
        return closestChest;
    }


}
