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
    /** Đứng yên trong khoảng này quanh rương là lấy được thức ăn. */
    private static final double FETCH_REACH_SQ = 3.0 * 3.0;

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
        List<String> missing = new ArrayList<>();
        for (List<AnimalEntity> herd : herds.values()) {
            if (herd.size() >= ModConfig.get().rancherMaxAnimalsPerType) continue;
            // Con không tới được vẫn tính vào tổng đàn (ngưỡng), chỉ không chọn để cho ăn
            List<AnimalEntity> ready = herd.stream()
                    .filter(a -> canBreedNow(a) && !npc.isSkipped(a)).toList();
            if (ready.size() < 2) continue;
            AnimalEntity sample = ready.get(0);
            BlockPos source = null;
            if (chestInv != null && hasFeed(chestInv, sample, 1, 0)) source = chest;
            else if (!hasFeed(npc.getFeed(), sample)) source = findFarmerSupply(sample); // rương mình thiếu → rương Nông dân
            if (source != null || hasFeed(npc.getFeed(), sample)) {
                pair.clear();
                pair.add(ready.get(0));
                pair.add(ready.get(1));
                fetchFrom = source;
                phase = hasFeed(npc.getFeed(), sample, FEED_PER_TRIP) || source == null ? Phase.FEED : Phase.FETCH;
                return true;
            }
            missing.add(feedHint(sample));
        }
        if (!missing.isEmpty()) {
            npc.display.report(npc, "Thiếu thức ăn: " + String.join(", ", missing)
                    + " (bỏ vào rương chăn nuôi hoặc rương nông dân)");
        }
        return false;
    }

    /** Gợi ý thức ăn theo loài (luật sinh sản của Minecraft). */
    private static String feedHint(AnimalEntity animal) {
        if (animal instanceof PigEntity) return "lợn cần cà rốt/khoai tây/củ dền";
        if (animal instanceof ChickenEntity) return "gà cần hạt giống";
        return "bò/cừu cần lúa mì";
    }

    /** Rương lấy thức ăn của lượt này (rương mình hoặc rương Nông dân). */
    private BlockPos fetchFrom;

    /** Rương Nông dân cùng chủ gần rương chăn nuôi còn thức ăn cho loài này (sau khi chừa phần để gieo lại). */
    private BlockPos findFarmerSupply(AnimalEntity sample) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (var be : com.example.helper.BlockEntityScan.inBox(npc.getWorld(), chest, FARMER_SUPPLY_RADIUS, 4)) {
            if (!(be instanceof com.example.block.FarmerChestBlockEntity farm)
                    || !farm.isAccessibleBy(npc.getOwnerUUID())
                    || !hasFeed(farm, sample, 1, FARMER_RESERVE)) continue;
            double dist = be.getPos().getSquaredDistance(chest);
            if (dist < bestDist) {
                bestDist = dist;
                best = be.getPos().toImmutable();
            }
        }
        return best;
    }

    /** Bán kính tìm rương Nông dân quanh rương chăn nuôi. */
    private static final int FARMER_SUPPLY_RADIUS = 24;
    /** Chừa lại trong rương Nông dân mỗi loại thức ăn (hạt/củ giống để Nông dân gieo lại). */
    private static final int FARMER_RESERVE = 16;

    private static boolean hasFeed(Inventory inv, AnimalEntity animal) {
        return hasFeed(inv, animal, 1);
    }

    private static boolean hasFeed(Inventory inv, AnimalEntity animal, int amount) {
        return hasFeed(inv, animal, amount, 0);
    }

    /** Có ít nhất amount món thức ăn cho loài này sau khi chừa reserve món mỗi loại item. */
    private static boolean hasFeed(Inventory inv, AnimalEntity animal, int amount, int reserve) {
        Map<net.minecraft.item.Item, Integer> counts = new HashMap<>();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && animal.isBreedingItem(stack)) counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        int usable = 0;
        for (int count : counts.values()) usable += Math.max(0, count - reserve);
        return usable >= amount;
    }

    @Override
    public void start() {
        ticks = 0;
        done = false;
        moveToCurrentTarget();
    }

    private void moveToCurrentTarget() {
        if (phase == Phase.FETCH) {
            npc.getNavigation().startMovingTo(fetchFrom.getX() + 0.5, fetchFrom.getY(), fetchFrom.getZ() + 0.5, 1.0);
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
            if (phase == Phase.FETCH) npc.display.report(npc, "Không tới được rương để lấy thức ăn cho vật nuôi");
            done = true;
        }
    }

    private void tickFetch() {
        // Đi bằng startMovingTo(rương) thường dừng cách 2.5–3 block → canReach (2.5 + tầm nhìn) không bao giờ đạt.
        // Đã dừng lại trong FETCH_REACH thì coi như tới rương (Thợ chăn nuôi vốn thao tác từ xa qua hàng rào).
        boolean atChest = DeliveryTarget.canReach(npc, fetchFrom)
                || (npc.getNavigation().isIdle()
                    && npc.squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(fetchFrom)) <= FETCH_REACH_SQ);
        if (!atChest) return;
        if (!(npc.getWorld().getBlockEntity(fetchFrom) instanceof Inventory chestInv)) {
            done = true;
            return;
        }
        AnimalEntity sample = pair.get(0);
        // Rương Nông dân: chừa FARMER_RESERVE món mỗi loại để Nông dân còn hạt/củ gieo lại
        int reserve = fetchFrom.equals(chest) ? 0 : FARMER_RESERVE;
        Map<net.minecraft.item.Item, Integer> allowed = new HashMap<>();
        for (int i = 0; i < chestInv.size(); i++) {
            ItemStack s = chestInv.getStack(i);
            if (!s.isEmpty() && sample.isBreedingItem(s)) allowed.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        allowed.replaceAll((item, count) -> Math.max(0, count - reserve));
        int needed = FEED_PER_TRIP;
        for (int i = 0; i < chestInv.size() && needed > 0; i++) {
            ItemStack stack = chestInv.getStack(i);
            if (stack.isEmpty() || !sample.isBreedingItem(stack)) continue;
            int canTake = Math.min(needed, Math.min(stack.getCount(), allowed.getOrDefault(stack.getItem(), 0)));
            if (canTake <= 0) continue;
            allowed.merge(stack.getItem(), -canTake, Integer::sum);
            ItemStack taken = stack.split(canTake);
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
        if (!npc.canReachTarget(animal, FEED_RANGE_SQ, ticks)) return;
        npc.getNavigation().stop();
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
