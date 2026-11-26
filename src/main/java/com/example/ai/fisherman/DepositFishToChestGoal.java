package com.example.ai.fisherman;

import com.example.entity.FishermanNpcEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

public class DepositFishToChestGoal extends Goal {

    private final FishermanNpcEntity npc;
    private long retryAt;
    private BlockPos targetChestPos;
    private final com.example.ai.DeliveryTarget delivery = new com.example.ai.DeliveryTarget();
    private int taskTicks = 0;
    /** Số lần đã tính lại đường khi NPC đứng lại giữa chừng (trước khi báo "không có đường"). */
    private int repaths;
    private int idleWait;
    private static final int MAX_REPATHS = 3;
    private static final int MAX_TASK_TICKS = 400;
    private static final double DEPOSIT_RANGE = 3.5;
    private boolean deposited = false;

    public DepositFishToChestGoal(FishermanNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (npc.sleeping.isSleeping() || npc.getWorld().getTime() < retryAt) return false;
        if (isInventoryEmpty(npc.getCargo())) {
            npc.setNeedsToDeposit(false);
            return false;
        }
        if (!npc.needsToDeposit()) return false;

        npc.findNearestChest();
        targetChestPos = delivery.find(npc, npc.getChestPos(), com.example.block.FishermanChestBlockEntity.class,
                npc.getCargo(), stack -> true, 16, 4, npc.display);
        if (targetChestPos == null) return false;

        deposited = false;
        return true;
    }

    @Override
    public void start() {
        taskTicks = 0;
        repaths = 0;
        idleWait = 0;
        deposited = false;
        delivery.start(npc);
    }

    @Override
    public void tick() {
        if (targetChestPos == null) return;
        taskTicks++;
        var world = npc.getWorld();
        if (!world.isChunkLoaded(targetChestPos)
                || !(world.getBlockEntity(targetChestPos) instanceof com.example.block.FishermanChestBlockEntity chest)) {
            npc.display.report(npc, "Rương nghề không còn dùng được");
            deposited = true;
        } else if (!com.example.ai.DeliveryTarget.hasSpace(chest, npc.getCargo(), stack -> true)) {
            npc.display.report(npc, "Rương nghề đã đầy");
            deposited = true;
        } else if (com.example.ai.DeliveryTarget.canReach(npc, targetChestPos)) {
            dumpInventoryToChest();
            com.example.helper.NpcFeedback.success(npc);
            deposited = true;
        } else if (npc.getNavigation().isIdle() && repaths < MAX_REPATHS && taskTicks < MAX_TASK_TICKS) {
            // Đứng lại giữa chừng (dừng lệch, đang nhảy...) → chờ 0.5s rồi tính lại đường, tối đa 3 lần
            if (++idleWait >= 10) {
                idleWait = 0;
                repaths++;
                if (!com.example.ai.DeliveryTarget.resumeTowards(npc, targetChestPos)) repaths = MAX_REPATHS;
            }
        } else if (npc.getNavigation().isIdle() || taskTicks >= MAX_TASK_TICKS) {
            delivery.markBlocked(npc, targetChestPos);
            npc.display.report(npc, "Không có đường tới rương nghề");
            deposited = true;
        }
    }

    @Override
    public boolean shouldContinue() {
        if (npc.sleeping.isSleeping()) return false;
        return targetChestPos != null && !deposited;
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        retryAt = npc.getWorld().getTime() + 40;
        targetChestPos = null;
        taskTicks = 0;
        deposited = false;
        npc.memory.resetIdle();
    }

    private void dumpInventoryToChest() {
        SimpleInventory npcInv = npc.getCargo();
        if (!npc.getWorld().isChunkLoaded(targetChestPos)
                || !(npc.getWorld().getBlockEntity(targetChestPos) instanceof com.example.block.FishermanChestBlockEntity targetChest)) return;

        for (int i = 0; i < npcInv.size(); i++) {
            ItemStack stack = npcInv.getStack(i);
            if (stack.isEmpty()) continue;
            ItemStack remaining = addToChest(targetChest, stack);
            if (remaining.isEmpty()) {
                npcInv.setStack(i, ItemStack.EMPTY);
            } else {
                npcInv.setStack(i, remaining);
            }
        }
        npc.setNeedsToDeposit(!isInventoryEmpty(npcInv));
        npc.memory.resetIdle();
    }

    private ItemStack addToChest(Inventory chest, ItemStack stack) {
        ItemStack toInsert = stack.copy();
        for (int i = 0; i < chest.size(); i++) {
            ItemStack slot = chest.getStack(i);
            if (slot.isEmpty()) {
                chest.setStack(i, toInsert.copy());
                toInsert.setCount(0);
                chest.markDirty();
                return ItemStack.EMPTY;
            }
            if (ItemStack.canCombine(slot, toInsert) && slot.getCount() < slot.getMaxCount()) {
                int canMove = Math.min(toInsert.getCount(), slot.getMaxCount() - slot.getCount());
                slot.increment(canMove);
                toInsert.decrement(canMove);
                chest.markDirty();
                if (toInsert.isEmpty()) return ItemStack.EMPTY;
            }
        }
        return toInsert;
    }

    private boolean isInventoryEmpty(SimpleInventory inv) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty()) return false;
        }
        return true;
    }
}
