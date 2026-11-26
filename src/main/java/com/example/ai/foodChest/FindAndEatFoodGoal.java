package com.example.ai.foodChest;

import com.example.ai.worker.DepositCargoGoal;
import com.example.helper.FoodChestHelper;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Đói mà túi không còn đồ ăn → tới rương thức ăn CHUNG gần nhất của làng (tra {@link FoodChestRegistry},
 * tầm {@link #VILLAGE_RANGE} block), ăn ngay 1 món và mang theo một khẩu phần để khỏi đi lại mỗi bữa.
 * Xa quá tầm tìm đường thì đi từng chặng; không tới được thì bỏ rương đó 1 phút, lần sau thử rương khác.
 */
public class FindAndEatFoodGoal extends Goal {
    /** Một rương thức ăn phục vụ NPC trong bán kính này (làng rộng hơn thì đặt thêm rương). */
    public static final int VILLAGE_RANGE = 96;
    public static final int VERTICAL_RANGE = 32;
    /** Số món mang theo mỗi lần (kể cả món ăn ngay) — đủ vài bữa, không vét sạch rương chung. */
    public static final int RATION = 8;
    private static final int NOT_FOUND_RETRY_TICKS = 200;
    private static final int MAX_TRIP_TICKS = 20 * 60;
    private static final int MAX_FAILS = 3;
    private static final int UNREACHABLE_TICKS = 1200;
    private static final double REACH_SQ = 2.5 * 2.5;
    private static final double DIRECT_PATH_SQ = 20.0 * 20.0;
    private static final int FOOD_THRESHOLD = 1;

    private final PathAwareEntity npc;
    private final Supplier<SimpleInventory> inventory;
    /** Bán kính nhỏ quanh NPC để tự ghi sổ rương thường có sách đánh dấu. */
    private final int discoverRadius;
    private final BooleanSupplier hungry;
    private final IntConsumer feed;
    private final Map<BlockPos, Long> unreachable = new HashMap<>();

    @Nullable
    private BlockPos target;
    private long retryAt;
    private int tripTicks;
    private int fails;

    public FindAndEatFoodGoal(PathAwareEntity npc, Supplier<SimpleInventory> inventory, int discoverRadius,
                              BooleanSupplier hungry, IntConsumer feed) {
        this.npc = npc;
        this.inventory = inventory;
        this.discoverRadius = discoverRadius;
        this.hungry = hungry;
        this.feed = feed;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (npc.getPose() == EntityPose.SLEEPING || !hungry.getAsBoolean() || hasEnoughFood()) return false;
        if (!(npc.getWorld() instanceof ServerWorld world)) return false;
        long now = world.getTime();
        if (now < retryAt) return false;
        retryAt = now + NOT_FOUND_RETRY_TICKS;

        FoodChestRegistry registry = FoodChestRegistry.get(world);
        registry.discover(world, npc.getBlockPos(), discoverRadius, 3);
        unreachable.values().removeIf(until -> until <= now);
        target = registry.nearest(world, npc.getBlockPos(), VILLAGE_RANGE, VERTICAL_RANGE,
                pos -> !unreachable.containsKey(pos) && FoodChestHelper.hasFood(pos, world));
        if (target == null) {
            com.example.entity.base.NpcDisplayComponent.reportWorker(npc, registry.size() == 0
                    ? "Chưa có rương thức ăn nào — đặt rương thức ăn ở khu chung của làng"
                    : "Không có rương thức ăn còn đồ trong " + VILLAGE_RANGE + " block");
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        tripTicks = 0;
        fails = 0;
        step();
    }

    @Override
    public void tick() {
        if (target == null) return;
        tripTicks++;
        if (npc.squaredDistanceTo(Vec3d.ofCenter(target)) <= REACH_SQ) {
            npc.getNavigation().stop();
            takeRation();
            target = null;
            return;
        }
        if (tripTicks > MAX_TRIP_TICKS || fails >= MAX_FAILS) {
            // Không tới được rương này → bỏ 1 phút, lần sau chọn rương khác
            unreachable.put(target, npc.getWorld().getTime() + UNREACHABLE_TICKS);
            retryAt = 0;
            target = null;
            return;
        }
        if (tripTicks % 10 == 0 && npc.getNavigation().isIdle()) step();
    }

    @Override
    public boolean shouldContinue() {
        return target != null && npc.getPose() != EntityPose.SLEEPING && !hasEnoughFood();
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        target = null;
    }

    /** Đi tới rương: xa thì từng chặng ~16 block hướng về rương (tầm tìm đường có hạn), gần thì đi thẳng. */
    private void step() {
        if (target == null) return;
        Vec3d goal = Vec3d.ofBottomCenter(target);
        boolean ok;
        if (npc.squaredDistanceTo(goal) > DIRECT_PATH_SQ) {
            Vec3d next = NoPenaltyTargeting.findTo(npc, 16, 7, goal, Math.PI / 2);
            ok = next != null && npc.getNavigation().startMovingTo(next.x, next.y, next.z, 1.0);
        } else {
            ok = npc.getNavigation().startMovingTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
        }
        if (!ok || npc.getNavigation().isIdle()) fails++;
    }

    /** Ăn ngay 1 món, mang theo thêm tới đủ khẩu phần (túi đầy thì để lại trong rương). */
    private void takeRation() {
        if (!(npc.getWorld() instanceof ServerWorld world) || target == null) return;
        if (!FoodChestHelper.isValidFoodChest(target, world)) {
            FoodChestRegistry.get(world).remove(target);
            return;
        }
        ItemStack first = FoodChestHelper.takeFood(target, world);
        if (first.isEmpty()) return;
        npc.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
        if (first.getItem().getFoodComponent() != null) feed.accept(first.getItem().getFoodComponent().getHunger());

        SimpleInventory bag = inventory.get();
        Inventory chest = FoodChestHelper.getFoodChestInventory(target, world);
        for (int i = 1; i < RATION; i++) {
            ItemStack food = FoodChestHelper.takeFood(target, world);
            if (food.isEmpty()) break;
            ItemStack rest = DepositCargoGoal.insert(bag, food);
            if (!rest.isEmpty()) {
                if (chest != null) DepositCargoGoal.insert(chest, rest); // túi đầy → trả lại rương
                break;
            }
        }
        if (chest != null) chest.markDirty();
    }

    private boolean hasEnoughFood() {
        SimpleInventory bag = inventory.get();
        int total = 0;
        for (int i = 0; i < bag.size(); i++) {
            ItemStack stack = bag.getStack(i);
            if (!stack.isEmpty() && stack.isFood()) total += stack.getCount();
        }
        return total >= FOOD_THRESHOLD;
    }
}
