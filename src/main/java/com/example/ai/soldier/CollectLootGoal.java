package com.example.ai.soldier;

import com.example.ai.worker.DepositCargoGoal;
import com.example.entity.SoldierNPCEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.EnumSet;

/**
 * Sau khi giết quái: đi nhặt đồ rơi quanh chỗ quái chết, cho vào túi chiến lợi phẩm.
 * Chỉ chạy khi không giao tranh; ở chế độ Đi theo chủ chỉ nhặt đồ gần chủ.
 */
public class CollectLootGoal extends Goal {
    private static final double SEARCH_RADIUS = 24.0;
    private static final double FOLLOW_MODE_OWNER_RADIUS = 12.0;
    private static final double PICKUP_RANGE_SQ = 1.5 * 1.5;
    private static final int MAX_TICKS = 200;
    private static final int SCAN_INTERVAL = 10;

    private final SoldierNPCEntity soldier;
    private ItemEntity item;
    private int ticks;
    private long nextScan;

    public CollectLootGoal(SoldierNPCEntity soldier) {
        this.soldier = soldier;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    private boolean canWork() {
        return !soldier.getWorld().isClient && soldier.getTarget() == null
                && soldier.getPose() != EntityPose.SLEEPING;
    }

    @Override
    public boolean canStart() {
        if (!canWork()) return false;
        long now = soldier.getWorld().getTime();
        if (now < nextScan || !soldier.loot.hasRecentKills(now)) return false;
        nextScan = now + SCAN_INTERVAL;

        PlayerEntity owner = soldier.getOwner();
        boolean followMode = soldier.getMoveMode() == ModeNpc.ModeMove.FOLLOW;
        item = soldier.getWorld().getEntitiesByClass(ItemEntity.class,
                        new Box(soldier.getBlockPos()).expand(SEARCH_RADIUS, 8, SEARCH_RADIUS),
                        e -> e.isAlive() && !e.cannotPickup() && soldier.loot.isLoot(e, now)
                                && hasRoomFor(e.getStack())
                                && (!followMode || owner == null
                                    || e.squaredDistanceTo(owner) <= FOLLOW_MODE_OWNER_RADIUS * FOLLOW_MODE_OWNER_RADIUS))
                .stream().min(Comparator.comparingDouble(soldier::squaredDistanceTo)).orElse(null);
        return item != null;
    }

    /** Thử bỏ vào bản sao túi để biết còn chỗ không (không đổi túi thật). */
    private boolean hasRoomFor(ItemStack stack) {
        var copy = new net.minecraft.inventory.SimpleInventory(soldier.loot.bag.size());
        for (int i = 0; i < copy.size(); i++) copy.setStack(i, soldier.loot.bag.getStack(i).copy());
        return DepositCargoGoal.insert(copy, stack).getCount() < stack.getCount();
    }

    @Override
    public void start() {
        ticks = 0;
        soldier.getNavigation().startMovingTo(item, 1.2);
    }

    @Override
    public void tick() {
        if (item == null) return;
        ticks++;
        soldier.getLookControl().lookAt(item, 30.0F, 30.0F);
        if (soldier.squaredDistanceTo(item) <= PICKUP_RANGE_SQ) {
            ItemStack rest = DepositCargoGoal.insert(soldier.loot.bag, item.getStack());
            if (rest.isEmpty()) item.discard();
            else item.setStack(rest);
            soldier.getWorld().playSound(null, soldier.getX(), soldier.getY(), soldier.getZ(),
                    SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 0.3F, 1.2F);
            item = null;
        } else if (ticks % 20 == 0) {
            soldier.getNavigation().startMovingTo(item, 1.2);
        }
    }

    @Override
    public boolean shouldContinue() {
        return item != null && item.isAlive() && ticks < MAX_TICKS && canWork();
    }

    @Override
    public void stop() {
        soldier.getNavigation().stop();
        if (item != null && item.isAlive() && ticks >= MAX_TICKS) {
            soldier.loot.skip(item, soldier.getWorld().getTime()); // không tới được → bỏ qua một lúc
        }
        item = null;
    }
}
