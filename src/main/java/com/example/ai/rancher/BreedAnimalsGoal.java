package com.example.ai.rancher;

import com.example.ai.DeliveryTarget;
import com.example.ai.worker.DepositCargoGoal;
import com.example.config.ModConfig;
import com.example.entity.RancherNpcEntity;
import com.example.helper.NpcFeedback;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cho vật nuôi (bò, cừu, lợn, gà) ăn để sinh sản khi đàn chưa đạt ngưỡng cấu hình.
 * Thức ăn (lúa mì, hạt, cà rốt...) lấy từ Rương Thợ chăn nuôi.
 */
public class BreedAnimalsGoal extends Goal {
    private static final int MAX_TICKS = 600;
    private static final int FEED_PER_TRIP = 2;
    private static final double FEED_RANGE_SQ = 2.5 * 2.5;

    private enum Phase { FETCH, FEED }

    private final RancherNpcEntity npc;
    private final List<AnimalEntity> pair = new ArrayList<>();
    private Phase phase;
    private BlockPos chest;
    private long nextScan;
    private int ticks;
    private boolean done;

    public BreedAnimalsGoal(RancherNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
    }

    private static boolean isFarmAnimal(AnimalEntity animal) {
        return animal instanceof CowEntity || animal instanceof SheepEntity
                || animal instanceof PigEntity || animal instanceof ChickenEntity;
    }

    private static boolean canBreedNow(AnimalEntity animal) {
        return animal.isAlive() && !animal.isBaby() && !animal.isInLove() && animal.getBreedingAge() == 0;
    }

    @Override
    public boolean canStart() {
        if (npc.sleeping.isSleeping() || npc.needsToDeposit()) return false;
        long now = npc.getWorld().getTime();
        if (now < nextScan) return false;
        nextScan = now + 100;
        chest = npc.findWorkChest();
        if (chest == null) return false;

        // Gom vật nuôi theo loài trong vùng làm việc
        int r = ModConfig.get().rancherWorkRadius;
        Map<EntityType<?>, List<AnimalEntity>> herds = new HashMap<>();
        for (AnimalEntity animal : npc.getWorld().getEntitiesByClass(AnimalEntity.class,
                new Box(chest).expand(r, 4, r), a -> a.isAlive() && isFarmAnimal(a))) {
            herds.computeIfAbsent(animal.getType(), t -> new ArrayList<>()).add(animal);
        }

        Inventory chestInv = npc.getWorld().getBlockEntity(chest) instanceof Inventory inv ? inv : null;
        boolean hungryHerd = false;
        for (List<AnimalEntity> herd : herds.values()) {
            if (herd.size() >= ModConfig.get().rancherMaxAnimalsPerType) continue;
            // Con không tới được vẫn tính vào tổng đàn (ngưỡng), chỉ không chọn để cho ăn
            List<AnimalEntity> ready = herd.stream()
                    .filter(a -> canBreedNow(a) && !npc.isSkipped(a)).toList();
            if (ready.size() < 2) continue;
            hungryHerd = true;
            AnimalEntity sample = ready.get(0);
            if (hasFeed(npc.getFeed(), sample) || (chestInv != null && hasFeed(chestInv, sample))) {
                pair.clear();
                pair.add(ready.get(0));
                pair.add(ready.get(1));
                phase = hasFeed(npc.getFeed(), sample, FEED_PER_TRIP) ? Phase.FEED : Phase.FETCH;
                return true;
            }
        }
        if (hungryHerd) npc.display.report(npc, "Rương chăn nuôi thiếu thức ăn cho vật nuôi");
        return false;
    }

    private static boolean hasFeed(Inventory inv, AnimalEntity animal) {
        return hasFeed(inv, animal, 1);
    }

    private static boolean hasFeed(Inventory inv, AnimalEntity animal, int amount) {
        int count = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && animal.isBreedingItem(stack)) count += stack.getCount();
        }
        return count >= amount;
    }

    @Override
    public void start() {
        ticks = 0;
        done = false;
        moveToCurrentTarget();
    }

    private void moveToCurrentTarget() {
        if (phase == Phase.FETCH) {
            npc.getNavigation().startMovingTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5, 1.0);
        } else if (!pair.isEmpty()) {
            npc.getNavigation().startMovingTo(pair.get(0), 1.0);
        }
    }

    @Override
    public void tick() {
        ticks++;
        if (phase == Phase.FETCH) tickFetch();
        else tickFeed();
        if (!done && ticks % 20 == 0) moveToCurrentTarget();
        if (ticks >= MAX_TICKS) {
            if (phase == Phase.FEED && !pair.isEmpty()) npc.skipTarget(pair.get(0), 600);
            done = true;
        }
    }

    private void tickFetch() {
        if (!DeliveryTarget.canReach(npc, chest)) return;
        if (!(npc.getWorld().getBlockEntity(chest) instanceof Inventory chestInv)) {
            done = true;
            return;
        }
        AnimalEntity sample = pair.get(0);
        int needed = FEED_PER_TRIP;
        for (int i = 0; i < chestInv.size() && needed > 0; i++) {
            ItemStack stack = chestInv.getStack(i);
            if (stack.isEmpty() || !sample.isBreedingItem(stack)) continue;
            ItemStack taken = stack.split(Math.min(needed, stack.getCount()));
            ItemStack rest = DepositCargoGoal.insert(npc.getFeed(), taken);
            stack.increment(rest.getCount()); // túi thức ăn đầy thì trả lại
            needed -= taken.getCount() - rest.getCount();
            chestInv.markDirty();
        }
        if (!hasFeed(npc.getFeed(), sample)) {
            done = true;
            return;
        }
        phase = Phase.FEED;
        moveToCurrentTarget();
    }

    private void tickFeed() {
        if (pair.isEmpty()) {
            done = true;
            return;
        }
        AnimalEntity animal = pair.get(0);
        if (!canBreedNow(animal)) {
            pair.remove(0);
            moveToCurrentTarget();
            return;
        }
        npc.getLookControl().lookAt(animal, 30.0F, 30.0F);
        if (npc.squaredDistanceTo(animal) > FEED_RANGE_SQ) return;
        if (!consumeFeed(npc.getFeed(), animal)) {
            done = true;
            return;
        }
        npc.swingHand(Hand.MAIN_HAND);
        animal.lovePlayer(null); // vào trạng thái sinh sản, AnimalMateGoal của vanilla lo phần còn lại
        pair.remove(0);
        if (pair.isEmpty()) {
            NpcFeedback.success(npc);
            done = true;
        } else {
            moveToCurrentTarget();
        }
    }

    private static boolean consumeFeed(SimpleInventory feed, AnimalEntity animal) {
        for (int i = 0; i < feed.size(); i++) {
            ItemStack stack = feed.getStack(i);
            if (!stack.isEmpty() && animal.isBreedingItem(stack)) {
                stack.decrement(1);
                feed.markDirty();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean shouldContinue() {
        return !done && !npc.sleeping.isSleeping();
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        pair.clear();
        npc.resetIdle();
    }
}
