package com.example.ai.worker;

import com.example.ai.DeliveryTarget;
import com.example.entity.base.WorkerNpcEntity;
import com.example.helper.NpcFeedback;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

/** Mang hàng trong cargo về nơi nộp (rương nghề, hoặc nơi do NPC chỉ định). */
public class DepositCargoGoal extends Goal {
    private static final int MAX_TASK_TICKS = 400;

    private final WorkerNpcEntity npc;
    private final DeliveryTarget delivery = new DeliveryTarget();
    private BlockPos target;
    private long retryAt;
    private int taskTicks;
    private boolean finished;

    public DepositCargoGoal(WorkerNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (npc.sleeping.isSleeping() || npc.getWorld().getTime() < retryAt) return false;
        if (npc.getCargo().isEmpty()) {
            npc.setNeedsToDeposit(false);
            return false;
        }
        if (!npc.needsToDeposit()) return false;
        target = npc.findDepositTarget(delivery);
        if (target == null) {
            retryAt = npc.getWorld().getTime() + 40;
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        taskTicks = 0;
        finished = false;
        if (!DeliveryTarget.canReach(npc, target)) {
            npc.getNavigation().startMovingTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.3);
        }
    }

    @Override
    public void tick() {
        taskTicks++;
        var world = npc.getWorld();
        var be = world.isChunkLoaded(target) ? world.getBlockEntity(target) : null;
        if (!(be instanceof Inventory chest) || !npc.isDepositTarget(be)) {
            npc.display.report(npc, "Rương nộp hàng không còn dùng được");
            finished = true;
        } else if (!DeliveryTarget.hasSpace(chest, npc.getCargo(), stack -> true)) {
            npc.display.report(npc, "Rương nộp hàng đã đầy");
            finished = true;
        } else if (DeliveryTarget.canReach(npc, target)) {
            npc.getLookControl().lookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
            if (moveAll(npc.getCargo(), chest)) NpcFeedback.success(npc);
            npc.setNeedsToDeposit(!npc.getCargo().isEmpty());
            finished = true;
        } else if (npc.getNavigation().isIdle() || taskTicks >= MAX_TASK_TICKS) {
            delivery.markBlocked(npc, target);
            npc.display.report(npc, "Không có đường tới rương nộp hàng");
            finished = true;
        }
    }

    @Override
    public boolean shouldContinue() {
        return !npc.sleeping.isSleeping() && target != null && !finished;
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        retryAt = npc.getWorld().getTime() + 40;
        target = null;
        npc.resetIdle();
    }

    /** Chuyển toàn bộ đồ từ from sang to; trả về true nếu chuyển được ít nhất 1 món. */
    public static boolean moveAll(SimpleInventory from, Inventory to) {
        boolean moved = false;
        for (int i = 0; i < from.size(); i++) {
            ItemStack stack = from.getStack(i);
            if (stack.isEmpty()) continue;
            ItemStack remaining = insert(to, stack);
            if (remaining.getCount() != stack.getCount()) moved = true;
            from.setStack(i, remaining);
        }
        return moved;
    }

    /** Thêm stack vào inventory, trả về phần còn thừa (có thể EMPTY). */
    public static ItemStack insert(Inventory inventory, ItemStack stack) {
        ItemStack rest = stack.copy();
        // Gộp vào stack cùng loại trước
        for (int i = 0; i < inventory.size() && !rest.isEmpty(); i++) {
            ItemStack slot = inventory.getStack(i);
            if (slot.isEmpty() || !ItemStack.canCombine(slot, rest) || !inventory.isValid(i, rest)) continue;
            int max = Math.min(inventory.getMaxCountPerStack(), slot.getMaxCount());
            int move = Math.min(rest.getCount(), max - slot.getCount());
            if (move <= 0) continue;
            slot.increment(move);
            rest.decrement(move);
            inventory.markDirty();
        }
        // Sau đó mới dùng slot trống
        for (int i = 0; i < inventory.size() && !rest.isEmpty(); i++) {
            if (!inventory.getStack(i).isEmpty() || !inventory.isValid(i, rest)) continue;
            int move = Math.min(rest.getCount(), Math.min(inventory.getMaxCountPerStack(), rest.getMaxCount()));
            inventory.setStack(i, rest.split(move));
        }
        return rest.isEmpty() ? ItemStack.EMPTY : rest;
    }
}
