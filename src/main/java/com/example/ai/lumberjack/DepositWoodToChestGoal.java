package com.example.ai.lumberjack;

import com.example.entity.LumberjackNpcEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;

public class DepositWoodToChestGoal extends Goal {
    private final LumberjackNpcEntity npc;
    private BlockPos chestPos;
    private final com.example.ai.DeliveryTarget delivery = new com.example.ai.DeliveryTarget();
    private int taskTicks = 0;
    private static final int MAX_TASK_TICKS = 240;
    private long retryAt;
    private static final int SEARCH_RADIUS = 7;

    public DepositWoodToChestGoal(LumberjackNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Control.MOVE));
    }
    private boolean hasItems() {
        for (int i = 0; i < npc.getCargo().size(); i++) {
            if (!npc.getCargo().getStack(i).isEmpty()) return true;
        }
        return false;
    }

    @Override
    public boolean canStart() {
        if (npc.activeTree != null) return false;
        if (!hasItems()) {
            npc.needsWoodDelivery = false;
            return false;
        }
        if (npc.getWorld().getTime() < retryAt) return false;

        // Chỉ cất đồ khi túi đầy hoặc đã rảnh rỗi (idle > 40 ticks = 2s)
        if (!npc.needsWoodDelivery && !npc.isInventoryFull() && npc.memory.idleTicks < 40) {
            return false;
        }

        npc.findNearestChest();
        chestPos = delivery.find(npc, npc.getChestPos(), com.example.block.LumberjackChestBlockEntity.class,
                npc.getCargo(), stack -> true, 16, 2, npc.display);
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
                || !(world.getBlockEntity(chestPos) instanceof com.example.block.LumberjackChestBlockEntity chest)) {
            npc.display.report(npc, "Rương nghề không còn dùng được");
            stop();
        } else if (!com.example.ai.DeliveryTarget.hasSpace(chest, npc.getCargo(), stack -> true)) {
            npc.display.report(npc, "Rương nghề đã đầy");
            stop();
        } else if (com.example.ai.DeliveryTarget.canReach(npc, chestPos)) {
            depositItems();
            com.example.helper.NpcFeedback.success(npc);
            stop();
        } else if (npc.getNavigation().isIdle() || taskTicks >= MAX_TASK_TICKS) {
            delivery.markBlocked(npc, chestPos);
            npc.display.report(npc, "Không có đường tới rương nghề");
            stop();
        }
    }

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
                instanceof com.example.block.LumberjackChestBlockEntity chestInv)) return;

        // Transfer items từ NPC vào chest
        for (int i = 0; i < npc.getCargo().size(); i++) {
            ItemStack npcStack = npc.getCargo().getStack(i);
            if (npcStack.isEmpty()) continue;

            for (int j = 0; j < chestInv.size(); j++) {
                ItemStack chestStack = chestInv.getStack(j);

                if (chestStack.isEmpty()) {
                    chestInv.setStack(j, npcStack.copy());
                    npc.getCargo().setStack(i, ItemStack.EMPTY);
                    break;
                } else if (ItemStack.canCombine(chestStack, npcStack) &&
                        chestStack.getCount() < chestStack.getMaxCount()) {
                    int canAdd = chestStack.getMaxCount() - chestStack.getCount();
                    int toAdd = Math.min(canAdd, npcStack.getCount());
                    chestStack.increment(toAdd);
                    npcStack.decrement(toAdd);

                    if (npcStack.isEmpty()) {
                        npc.getCargo().setStack(i, ItemStack.EMPTY);
                        break;
                    }
                }
            }

            if (!npcStack.isEmpty()) {
                npc.getCargo().setStack(i, npcStack);
            }
        }

        chestInv.markDirty();
        npc.needsWoodDelivery = hasItems();
        npc.memory.resetIdle();
    }




}
