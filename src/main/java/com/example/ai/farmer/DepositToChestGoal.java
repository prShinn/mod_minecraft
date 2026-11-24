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
    private final com.example.ai.DeliveryTarget delivery = new com.example.ai.DeliveryTarget();
    private int taskTicks = 0;
    private static final int MAX_TASK_TICKS = 240;
    private long retryAt;
    private static final int SEARCH_RADIUS = 18;

    public DepositToChestGoal(FarmerNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Control.MOVE));

    }
    private boolean hasItems() {
        for (int i = 0; i < npc.foodInventory.size(); i++) {
            ItemStack stack = npc.foodInventory.getStack(i);
            if (!stack.isEmpty() && !isMeat(stack)) return true;
        }
        return false;
    }
    @Override
    public boolean canStart() {
        if (!hasItems() || npc.getWorld().getTime() < retryAt) return false;

        // Chỉ cất đồ khi túi đầy hoặc đã rảnh rỗi (idle > 40 ticks = 2s)
        if (!npc.isInventoryFull() && npc.memory.idleTicks < 40) {
            return false;
        }

        npc.findNearestChest();
        chestPos = delivery.find(npc, npc.memory.lastChestPos, com.example.block.FarmerChestBlockEntity.class,
                npc.foodInventory, stack -> !isMeat(stack), 16, 2, npc.display);
        if (chestPos == null) return false;
        return true;
    }

    @Override
    public void start() {
        taskTicks = 0;
        delivery.start(npc);
    }

    @Override
    public void tick() {
        if (chestPos == null) return;
        taskTicks++;
        var world = npc.getWorld();
        if (!world.isChunkLoaded(chestPos)
                || !(world.getBlockEntity(chestPos) instanceof com.example.block.FarmerChestBlockEntity chest)) {
            npc.display.report(npc, "Rương nghề không còn dùng được");
            stop();
        } else if (!com.example.ai.DeliveryTarget.hasSpace(chest, npc.foodInventory, stack -> !isMeat(stack))) {
            npc.display.report(npc, "Rương nghề đã đầy");
            stop();
        } else if (com.example.ai.DeliveryTarget.canReach(npc, chestPos)) {
            depositItems();
            stop();
        } else if (npc.getNavigation().isIdle() || taskTicks >= MAX_TASK_TICKS) {
            delivery.markBlocked(npc, chestPos);
            npc.display.report(npc, "Không có đường tới rương nghề");
            stop();
        }
    }

    //    @Override
//    public boolean shouldContinue() {
//        return chestPos != null && npc.getNavigation().isFollowingPath() && hasItems();
//    }
    @Override
    public boolean shouldContinue() {
        return chestPos != null;
    }


    @Override
    public void stop() {
        npc.getNavigation().stop();
        retryAt = npc.getWorld().getTime() + 40;
        chestPos = null;
        taskTicks = 0;
    }



    private void depositItems() {
        if (!(npc.getWorld().getBlockEntity(chestPos)
                instanceof com.example.block.FarmerChestBlockEntity chestInv)) return;

        // Transfer tất cả items từ NPC inventory vào rương
        for (int i = 0; i < npc.foodInventory.size(); i++) {
            ItemStack npcStack = npc.foodInventory.getStack(i);
            if (npcStack.isEmpty() || isMeat(npcStack)) continue;

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

        chestInv.markDirty();
    }


    private boolean isMeat(ItemStack stack) {
        return stack.isFood()
                && stack.getItem().getFoodComponent() != null
                && stack.getItem().getFoodComponent().isMeat();
    }

}
