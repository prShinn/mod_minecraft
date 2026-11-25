package com.example.ai.rancher;

import com.example.ai.worker.DepositCargoGoal;
import com.example.config.ModConfig;
import com.example.entity.RancherNpcEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.EnumSet;

/** Nhặt sản phẩm vật nuôi rơi trên đất (lông cừu, trứng, lông gà, da) quanh rương. */
public class CollectAnimalDropsGoal extends Goal {
    private static final int MAX_TICKS = 200;
    private static final double PICKUP_RANGE_SQ = 1.5 * 1.5;

    private final RancherNpcEntity npc;
    private ItemEntity item;
    private long nextScan;
    private int ticks;

    public CollectAnimalDropsGoal(RancherNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
    }

    public static boolean isAnimalProduct(ItemStack stack) {
        return stack.isOf(Items.EGG) || stack.isOf(Items.FEATHER) || stack.isOf(Items.LEATHER)
                || stack.isOf(Items.BEEF) || stack.isOf(Items.PORKCHOP)
                || stack.isOf(Items.MUTTON) || stack.isOf(Items.CHICKEN)
                || stack.isIn(ItemTags.WOOL);
    }

    @Override
    public boolean canStart() {
        if (npc.sleeping.isSleeping() || npc.needsToDeposit()) return false;
        long now = npc.getWorld().getTime();
        if (now < nextScan) return false;
        nextScan = now + 20;
        BlockPos center = npc.findWorkChest();
        if (center == null) return false;
        int r = ModConfig.get().rancherWorkRadius;
        item = npc.getWorld().getEntitiesByClass(ItemEntity.class, new Box(center).expand(r, 4, r),
                        e -> e.isAlive() && !e.cannotPickup() && isAnimalProduct(e.getStack()) && !npc.isSkipped(e))
                .stream().min(Comparator.comparingDouble(npc::squaredDistanceTo)).orElse(null);
        if (item == null) return false;
        // Túi đầy thì đi nộp trước
        if (DepositCargoGoal.insert(copyOf(npc), item.getStack()).getCount() == item.getStack().getCount()) {
            npc.setNeedsToDeposit(true);
            return false;
        }
        return true;
    }

    private static net.minecraft.inventory.SimpleInventory copyOf(RancherNpcEntity npc) {
        var source = npc.getCargo();
        var copy = new net.minecraft.inventory.SimpleInventory(source.size());
        for (int i = 0; i < source.size(); i++) copy.setStack(i, source.getStack(i).copy());
        return copy;
    }

    @Override
    public void start() {
        ticks = 0;
        npc.getNavigation().startMovingTo(item.getX(), item.getY(), item.getZ(), 1.1);
    }

    @Override
    public void tick() {
        ticks++;
        if (npc.squaredDistanceTo(item) <= PICKUP_RANGE_SQ) {
            ItemStack rest = DepositCargoGoal.insert(npc.getCargo(), item.getStack());
            if (rest.isEmpty()) item.discard();
            else item.setStack(rest);
            npc.getWorld().playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                    SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 0.3F, 1.2F);
            item = null;
        } else if (ticks % 20 == 0) {
            npc.getNavigation().startMovingTo(item.getX(), item.getY(), item.getZ(), 1.1);
        }
    }

    @Override
    public boolean shouldContinue() {
        return item != null && item.isAlive() && ticks < MAX_TICKS && !npc.sleeping.isSleeping();
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        if (item != null && item.isAlive() && ticks >= MAX_TICKS) {
            npc.skipTarget(item, 600); // không nhặt được trong 10 giây → bỏ qua 30 giây
        }
        item = null;
        npc.resetIdle();
    }
}
