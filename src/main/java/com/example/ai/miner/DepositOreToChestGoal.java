package com.example.ai.miner;

import com.example.entity.MinerNpcEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

/**
 * AI Goal — Nộp khoáng sản vào chest gần nhất.
 *
 * Được kích hoạt theo lịch từ MineOreVeinGoal (mỗi 10-15 giây),
 * KHÔNG dựa vào điều kiện inventory đầy.
 *
 * Sau khi dump xong → goal tự dừng → MineOreVeinGoal tiếp tục đào.
 */
public class DepositOreToChestGoal extends Goal {

    private final MinerNpcEntity npc;
    private long retryAt;
    private BlockPos targetChestPos;
    private final com.example.ai.DeliveryTarget delivery = new com.example.ai.DeliveryTarget();
    private int taskTicks = 0;
    private static final int MAX_TASK_TICKS = 400; // 20 giây tối đa

    // Khoảng cách để dump vào chest
    private static final double DEPOSIT_RANGE = 3.5;

    // Trạng thái: đã dump xong chưa
    private boolean deposited = false;

    public DepositOreToChestGoal(MinerNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
    }

    @Override
    public boolean canStart() {
        // Không chạy khi đang ngủ
        if (npc.sleeping.isSleeping() || npc.getWorld().getTime() < retryAt) return false;

        // Không chạy nếu inventory trống (không có gì để cất)
        if (isInventoryEmpty(npc.getCargo())) return false;

        // CHỈ chạy khi có cờ báo hiệu cần cất đồ (do MineOreVeinGoal bật)
        if (!npc.needsToDeposit()) return false;

        // Tìm chest gần nhất
        npc.findNearestChest();
        targetChestPos = delivery.find(npc, npc.getChestPos(), com.example.block.MinerChestBlockEntity.class,
                npc.getCargo(), stack -> !stack.isFood(), 24, 5, npc.display);
        if (targetChestPos == null) return false;

        deposited = false;
        return true;
    }

    @Override
    public void start() {
        taskTicks = 0;
        deposited = false;
        delivery.start(npc);
    }

    @Override
    public void tick() {
        if (targetChestPos == null) return;
        taskTicks++;
        var world = npc.getWorld();
        if (!world.isChunkLoaded(targetChestPos)
                || !(world.getBlockEntity(targetChestPos) instanceof com.example.block.MinerChestBlockEntity chest)) {
            npc.display.report(npc, "Rương nghề không còn dùng được");
            deposited = true;
        } else if (!com.example.ai.DeliveryTarget.hasSpace(chest, npc.getCargo(), stack -> !stack.isFood())) {
            npc.display.report(npc, "Rương nghề đã đầy");
            deposited = true;
        } else if (com.example.ai.DeliveryTarget.canReach(npc, targetChestPos)) {
            dumpInventoryToChest();
            com.example.helper.NpcFeedback.success(npc);
            deposited = true;
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

    // ==================== PRIVATE HELPERS ====================

    /**
     * Dump toàn bộ inventory NPC vào chest
     */
    private void dumpInventoryToChest() {
        SimpleInventory npcInv = npc.getCargo();
        if (!npc.getWorld().isChunkLoaded(targetChestPos)
                || !(npc.getWorld().getBlockEntity(targetChestPos) instanceof com.example.block.MinerChestBlockEntity targetChest)) return;

        for (int i = 0; i < npcInv.size(); i++) {
            ItemStack stack = npcInv.getStack(i);
            if (stack.isEmpty()) continue;
            // Không cất food vào rương (giữ lại để ăn)
            if (stack.isFood()) continue;

            ItemStack remaining = addToChest(targetChest, stack);
            if (remaining.isEmpty()) {
                npcInv.setStack(i, ItemStack.EMPTY);
            } else {
                // Chest đầy → dừng
                npcInv.setStack(i, remaining);
            }
        }
        npc.setNeedsToDeposit(!isInventoryEmpty(npcInv));
        npc.memory.resetIdle();
    }

    /**
     * Thêm item vào chest, trả về phần dư nếu chest đầy
     */
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

    /**
     * Kiểm tra inventory có trống không
     */
    private boolean isInventoryEmpty(SimpleInventory inv) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            // Có ít nhất 1 item không phải food → không trống
            if (!s.isEmpty() && !s.isFood()) return false;
        }
        return true;
    }
}
