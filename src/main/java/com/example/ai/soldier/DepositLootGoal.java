package com.example.ai.soldier;

import com.example.ai.DeliveryTarget;
import com.example.ai.worker.DepositCargoGoal;
import com.example.block.ProfessionChestBlockEntity;
import com.example.entity.SoldierNPCEntity;
import com.example.helper.NpcFeedback;
import com.example.registry.ModBlocks;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Mang chiến lợi phẩm về cất vào Rương binh lính của chủ (gần nhất trong 24 block).
 * Chỉ đi cất khi đã nhặt xong (không còn chỗ quái chết mới) hoặc túi đầy;
 * không có rương thì giữ đồ trong túi, khi nào gần rương thì cất.
 */
public class DepositLootGoal extends Goal {
    private static final int SEARCH_RADIUS = 24;
    private static final int SEARCH_HEIGHT = 6;
    private static final int RETRY_TICKS = 40;
    private static final int NOT_FOUND_RETRY_TICKS = 200;
    private static final int MAX_TICKS = 600;
    private static final int MAX_PATH_CHECKS = 4;

    private final SoldierNPCEntity soldier;
    private BlockPos chest;
    private Path path;
    private long nextScan;
    private int ticks;
    private boolean done;

    public DepositLootGoal(SoldierNPCEntity soldier) {
        this.soldier = soldier;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    private boolean canWork() {
        return !soldier.getWorld().isClient && soldier.getTarget() == null
                && soldier.getPose() != EntityPose.SLEEPING;
    }

    @Override
    public boolean canStart() {
        if (!canWork() || soldier.loot.bag.isEmpty()) return false;
        long now = soldier.getWorld().getTime();
        if (now < nextScan) return false;
        // Đang còn đồ rơi chờ nhặt thì nhặt trước (trừ khi túi đã đầy)
        if (soldier.loot.hasRecentKills(now) && !isBagFull()) return false;
        nextScan = now + RETRY_TICKS;
        chest = findChest();
        if (chest == null) {
            nextScan = now + NOT_FOUND_RETRY_TICKS;
            return false;
        }
        return true;
    }

    private boolean isBagFull() {
        for (int i = 0; i < soldier.loot.bag.size(); i++) {
            if (soldier.loot.bag.getStack(i).isEmpty()) return false;
        }
        return true;
    }

    /** Rương binh lính của chủ, còn chỗ, có đường tới — gần nhất trước. */
    private BlockPos findChest() {
        World world = soldier.getWorld();
        BlockPos center = soldier.getBlockPos();
        List<BlockPos> candidates = new ArrayList<>();
        for (var be : com.example.helper.BlockEntityScan.inBox(world, center, SEARCH_RADIUS, SEARCH_HEIGHT)) {
            if (isUsableChest(world, be.getPos())) candidates.add(be.getPos().toImmutable());
        }
        candidates.sort(Comparator.comparingDouble(center::getSquaredDistance));
        for (int i = 0; i < Math.min(MAX_PATH_CHECKS, candidates.size()); i++) {
            BlockPos pos = candidates.get(i);
            path = null;
            if (DeliveryTarget.canReach(soldier, pos)) return pos;
            Path found = soldier.getNavigation().findPathTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1);
            if (DeliveryTarget.pathReachesChest(found, pos)) {
                path = found;
                return pos;
            }
        }
        return null;
    }

    private boolean isUsableChest(World world, BlockPos pos) {
        return world.isChunkLoaded(pos) && world.getBlockState(pos).isOf(ModBlocks.SOLDIER_CHEST)
                && world.getBlockEntity(pos) instanceof ProfessionChestBlockEntity box
                && box.isAccessibleBy(soldier.getOwnerUuid())
                && DeliveryTarget.hasSpace(box, soldier.loot.bag, stack -> true);
    }

    @Override
    public void start() {
        ticks = 0;
        done = false;
        if (path != null) soldier.getNavigation().startMovingAlong(path, 1.2);
    }

    @Override
    public void tick() {
        ticks++;
        World world = soldier.getWorld();
        if (!isUsableChest(world, chest)) {
            done = true; // rương bị phá/đầy giữa đường
            return;
        }
        if (DeliveryTarget.canReach(soldier, chest)) {
            soldier.getNavigation().stop();
            soldier.getLookControl().lookAt(chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5);
            if (world.getBlockEntity(chest) instanceof ProfessionChestBlockEntity box
                    && DepositCargoGoal.moveAll(soldier.loot.bag, box)) {
                NpcFeedback.success(soldier);
            }
            done = true;
        } else if (ticks % 20 == 0 && soldier.getNavigation().isIdle()) {
            // Đứng lại giữa đường → tính lại đường, không có thì bỏ lượt này
            Path again = soldier.getNavigation().findPathTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5, 1);
            if (DeliveryTarget.pathReachesChest(again, chest)) soldier.getNavigation().startMovingAlong(again, 1.2);
            else done = true;
        }
    }

    @Override
    public boolean shouldContinue() {
        return !done && ticks < MAX_TICKS && chest != null && canWork();
    }

    @Override
    public void stop() {
        soldier.getNavigation().stop();
        chest = null;
        path = null;
    }
}
