package com.example.ai.lumberjack;

import com.example.ai.DeliveryTarget;
import com.example.block.LumberjackChestBlockEntity;
import com.example.entity.LumberjackNpcEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlantSaplingGoal extends Goal {
    private static final int PLANT_RADIUS = 8;
    private static final int ACCESS_LENGTH = 8;
    private static final int TRAVEL_TIMEOUT = 240;
    private static final double PLANT_REACH_SQUARED = 2.25;

    private final LumberjackNpcEntity npc;
    private final List<BlockPos> plots = new ArrayList<>();
    private final Map<BlockPos, Long> blockedPlots = new HashMap<>();
    private BlockPos chest;
    private BlockPos plot;
    private int travelTicks;
    private long retryAt;
    private boolean finished;

    public PlantSaplingGoal(LumberjackNpcEntity npc) {
        this.npc = npc;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        long now = npc.getWorld().getTime();
        if (npc.sleeping.isSleeping() || npc.activeTree != null || npc.needsWoodDelivery || now < retryAt) return false;
        retryAt = now + 40;
        blockedPlots.values().removeIf(until -> until <= now);

        chest = npc.saplingChestPos;
        if (chest != null && !npc.getWorld().isChunkLoaded(chest)) return false;
        Inventory source = getChest();
        if (source == null) {
            source = npc.findNearestChest();
            chest = npc.getChestPos();
        }
        if (source == null || chest == null) return false;
        if (npc.returningSaplings && !npc.plantingSaplings.isEmpty()) return true;

        plots.clear();
        for (BlockPos pos : BlockPos.iterate(chest.add(-PLANT_RADIUS, -2, -PLANT_RADIUS),
                chest.add(PLANT_RADIUS, 2, PLANT_RADIUS))) {
            if (isPlantable(pos) && !blockedPlots.containsKey(pos)) plots.add(pos.toImmutable());
        }
        if (!npc.plantingSaplings.isEmpty()) return true;
        return !plots.isEmpty() && (countSaplings(source) > 0 || countSaplings(npc.getCargo()) > 0);
    }

    @Override
    public void start() {
        finished = false;
        plot = null;
        if (npc.returningSaplings || npc.plantingSaplings.isEmpty()) {
            moveToChest();
        } else {
            nextPlot();
        }
    }

    @Override
    public boolean shouldContinue() {
        return !finished && !npc.sleeping.isSleeping();
    }

    @Override
    public void tick() {
        if (finished) return;
        travelTicks++;
        Inventory source = getChest();
        if (source == null) {
            finished = true;
            return;
        }
        if (plot == null) {
            if (!npc.returningSaplings && !npc.plantingSaplings.isEmpty()) {
                nextPlot();
            } else if (DeliveryTarget.canReach(npc, chest)) {
                if (npc.returningSaplings) {
                    returnBatch(source);
                    finished = true;
                } else {
                    takeBatch(source);
                    nextPlot();
                }
            } else if (travelFailed()) {
                npc.display.report(npc, "Không có đường tới rương tiều phu");
                finished = true;
            }
            return;
        }

        if (!isPlantable(plot)) {
            nextPlot();
        } else if (canReachPlot(plot)) {
            plantSapling();
            nextPlot();
        } else if (travelFailed()) {
            markBlocked(plot);
            nextPlot();
        }
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        plots.clear();
        plot = null;
        chest = null;
        retryAt = npc.getWorld().getTime() + 40;
    }

    private void nextPlot() {
        plot = null;
        if (npc.plantingSaplings.isEmpty()) {
            clearBatch();
            finished = true;
            return;
        }

        plots.sort(Comparator.comparingDouble(npc.getBlockPos()::getSquaredDistance));
        var navigation = npc.getNavigation();
        navigation.stop();
        // Limit path searches per tick. Recheck the remaining plots on the next tick.
        int attemptedPaths = 0;
        while (!plots.isEmpty() && attemptedPaths < 8) {
            BlockPos candidate = plots.remove(0);
            if (!isPlantable(candidate)) continue;
            if (canReachPlot(candidate)) {
                plot = candidate;
                return;
            }
            attemptedPaths++;
            BlockPos target = candidate.up();
            var path = navigation.findPathTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0);
            if (path != null && path.reachesTarget() && navigation.startMovingAlong(path, 1.2)) {
                plot = candidate;
                travelTicks = 0;
                return;
            }
            markBlocked(candidate);
        }
        if (plots.isEmpty()) {
            npc.returningSaplings = true;
            moveToChest();
        }
    }

    private void moveToChest() {
        travelTicks = 0;
        var navigation = npc.getNavigation();
        navigation.stop();
        if (!DeliveryTarget.canReach(npc, chest)) {
            var path = navigation.findPathTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5, 1);
            if (path != null && path.reachesTarget()) navigation.startMovingAlong(path, 1.2);
        }
    }

    private boolean travelFailed() {
        return travelTicks >= TRAVEL_TIMEOUT || npc.getNavigation().isIdle();
    }

    private boolean canReachPlot(BlockPos ground) {
        Vec3d target = Vec3d.ofCenter(ground.up());
        if (npc.squaredDistanceTo(target) > PLANT_REACH_SQUARED) return false;
        var hit = npc.getWorld().raycast(new net.minecraft.world.RaycastContext(
                npc.getEyePos(), target, net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, npc));
        return hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS;
    }

    private void markBlocked(BlockPos pos) {
        blockedPlots.put(pos, npc.getWorld().getTime() + 200);
    }

    private Inventory getChest() {
        var world = npc.getWorld();
        return chest != null && world.isChunkLoaded(chest)
                && world.getBlockEntity(chest) instanceof LumberjackChestBlockEntity source ? source : null;
    }

    private boolean isPlantable(BlockPos ground) {
        var world = npc.getWorld();
        if (!world.isChunkLoaded(ground)) return false;
        int dx = ground.getX() - chest.getX();
        int dz = ground.getZ() - chest.getZ();
        if (dx * dx + dz * dz > PLANT_RADIUS * PLANT_RADIUS) return false;
        // Reserve one straight lane on each horizontal axis through the chest.
        if ((dx == 0 || dz == 0) && Math.abs(dx) + Math.abs(dz) <= ACCESS_LENGTH) return false;

        Block block = world.getBlockState(ground).getBlock();
        return (block == Blocks.DIRT || block == Blocks.GRASS_BLOCK || block == Blocks.PODZOL
                || block == Blocks.COARSE_DIRT || block == Blocks.MYCELIUM)
                && world.getBlockState(ground.up()).isAir() && world.getLightLevel(ground.up()) >= 8;
    }

    private void takeBatch(Inventory source) {
        // Saplings carried by NPCs from an older save can still be planted.
        Inventory stock = countSaplings(npc.getCargo()) > 0 ? npc.getCargo() : source;
        int remaining = Math.min(plots.size(), stock == source ? shareOf(source) : countSaplings(stock));
        for (int slot = 0, batchSlot = 0; slot < stock.size() && remaining > 0; slot++) {
            ItemStack stack = stock.getStack(slot);
            if (!isSapling(stack)) continue;
            ItemStack taken = stock.removeStack(slot, Math.min(remaining, stack.getCount()));
            npc.plantingSaplings.setStack(batchSlot++, taken);
            remaining -= taken.getCount();
        }
        if (!npc.plantingSaplings.isEmpty()) npc.saplingChestPos = chest;
    }

    private int shareOf(Inventory source) {
        int total = countSaplings(source);
        if (total == 0) return 0;
        int workers = 1;
        boolean unknownAssignment = false;
        int radius = LumberjackNpcEntity.FIND_CHEST_DISTANCE;
        for (LumberjackNpcEntity other : npc.getWorld().getEntitiesByClass(LumberjackNpcEntity.class,
                new Box(chest).expand(radius, 4, radius), worker -> worker != npc && worker.isAlive())) {
            BlockPos otherChest = other.getChestPos();
            if (chest.equals(otherChest)) workers++;
            else if (otherChest == null) unknownAssignment = true;
        }
        return unknownAssignment ? (total > 8 ? total / 2 : total) : Math.max(1, total / workers);
    }

    private int countSaplings(Inventory inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (isSapling(stack)) count += stack.getCount();
        }
        return count;
    }

    private boolean isSapling(ItemStack stack) {
        var item = stack.getItem();
        return item == Items.OAK_SAPLING || item == Items.SPRUCE_SAPLING || item == Items.BIRCH_SAPLING
                || item == Items.JUNGLE_SAPLING || item == Items.ACACIA_SAPLING || item == Items.DARK_OAK_SAPLING
                || item == Items.MANGROVE_PROPAGULE || item == Items.CHERRY_SAPLING;
    }

    private void plantSapling() {
        var world = npc.getWorld();
        BlockPos target = plot.up();
        for (int slot = 0; slot < npc.plantingSaplings.size(); slot++) {
            ItemStack stack = npc.plantingSaplings.getStack(slot);
            if (!(stack.getItem() instanceof BlockItem sapling)) continue;
            BlockState state = sapling.getBlock().getDefaultState();
            if (state.canPlaceAt(world, target) && world.setBlockState(target, state)) {
                npc.plantingSaplings.removeStack(slot, 1);
                npc.swingHand(Hand.MAIN_HAND, true);
                npc.memory.lastTreePos = target;
                npc.memory.resetIdle();
                return;
            }
        }
    }

    private void returnBatch(Inventory source) {
        for (int i = 0; i < npc.plantingSaplings.size(); i++) {
            ItemStack stack = npc.plantingSaplings.getStack(i);
            for (int slot = 0; slot < source.size() && !stack.isEmpty(); slot++) {
                ItemStack current = source.getStack(slot);
                if (current.isEmpty()) {
                    source.setStack(slot, stack.copy());
                    npc.plantingSaplings.setStack(i, ItemStack.EMPTY);
                    break;
                }
                if (ItemStack.canCombine(current, stack)) {
                    int capacity = Math.min(source.getMaxCountPerStack(), current.getMaxCount()) - current.getCount();
                    if (capacity <= 0) continue;
                    int count = Math.min(stack.getCount(), capacity);
                    current.increment(count);
                    stack.decrement(count);
                    source.markDirty();
                }
            }
        }
        if (npc.plantingSaplings.isEmpty()) clearBatch();
        else npc.display.report(npc, "Rương đầy, tiều phu vẫn giữ mầm dư");
    }

    private void clearBatch() {
        npc.saplingChestPos = null;
        npc.returningSaplings = false;
    }
}
