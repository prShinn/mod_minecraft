package com.example.ai.lumberjack;

import com.example.entity.LumberjackNpcEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.util.math.Direction;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChopTreeGoal extends Goal {
    private static final int MAX_LOGS = 2048;
    private static final int MAX_LEAVES = 1024;
    private static final int APPROACH_TIMEOUT = 240;
    private static final double BASE_REACH_SQUARED = 25;

    private final LumberjackNpcEntity npc;
    private final ArrayDeque<BlockPos> logs = new ArrayDeque<>();
    private final Set<BlockPos> canopy = new HashSet<>();
    private BlockPos base;
    private BlockPos zoneChest;
    private Block logType;
    private boolean atBase;
    private int approachTicks;
    private int swingDelay;
    private long retryAt;

    public ChopTreeGoal(LumberjackNpcEntity npc) {
        this.npc = npc;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (npc.needsWoodDelivery || npc.isInventoryFull() || npc.getWorld().getTime() < retryAt) return false;
        BlockPos root = npc.findNearestTree();
        if (root == null || npc.isWorkedByAnother(root)) return false;

        zoneChest = npc.getChestPos();
        logType = npc.getWorld().getBlockState(root).getBlock();
        List<BlockPos> tree = findConnectedLogs(root);
        if (tree.isEmpty()) return false;

        base = root;
        logs.clear();
        canopy.clear();
        collectCanopy(tree);
        canopy.stream()
                .sorted(Comparator.comparingInt((BlockPos pos) ->
                        npc.getWorld().getBlockState(pos).get(LeavesBlock.DISTANCE)).reversed())
                .forEach(logs::addLast);
        // Every branch was discovered after its connection to the root.
        // Cut in reverse discovery order so the remaining logs still connect to the root.
        Collections.reverse(tree);
        logs.addAll(tree);
        return true;
    }

    @Override
    public void start() {
        approachTicks = 0;
        swingDelay = 0;
        atBase = false;
        if (npc.squaredDistanceTo(Vec3d.ofCenter(base)) > BASE_REACH_SQUARED) {
            npc.getNavigation().startMovingTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 1.2);
        }
    }

    @Override
    public boolean shouldContinue() {
        return base != null && allowsCutting(base) && npc.isTreeRoot(base)
                && npc.getWorld().getBlockState(base).isOf(logType)
                && !logs.isEmpty() && !npc.isInventoryFull()
                && approachTicks < APPROACH_TIMEOUT && !npc.isWorkedByAnother(base);
    }

    @Override
    public void tick() {
        if (!(npc.getWorld() instanceof ServerWorld world)) return;
        if (npc.squaredDistanceTo(Vec3d.ofCenter(base)) > BASE_REACH_SQUARED) {
            if (atBase) {
                atBase = false;
                npc.activeTree = null;
                npc.getNavigation().startMovingTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 1.2);
            }
            approachTicks++;
            // Path tạm dừng (vướng vật cản/hết đoạn) → thử tìm đường lại mỗi giây; quá APPROACH_TIMEOUT mới bỏ cây
            if (npc.getNavigation().isIdle() && approachTicks % 20 == 0) {
                npc.getNavigation().startMovingTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 1.2);
            }
            return;
        }

        npc.getNavigation().stop();
        atBase = true;
        approachTicks = 0;
        npc.activeTree = base;
        if (swingDelay-- > 0) return;
        if (!allowsCutting(base) || !npc.isTreeRoot(base) || !world.getBlockState(base).isOf(logType)) return;
        BlockPos pos = logs.peekFirst();
        if (!world.isChunkLoaded(pos)) {
            logs.clear();
            return;
        }
        logs.removeFirst();
        BlockState state = world.getBlockState(pos);
        boolean leaf = canopy.contains(pos);
        if (leaf ? !isNaturalLeaf(state) : !state.isIn(BlockTags.LOGS)) return;

        List<ItemStack> drops = Block.getDroppedStacks(state, world, pos, null, npc, leaf ? ItemStack.EMPTY : npc.getMainHandStack());
        if (!world.breakBlock(pos, false, npc)) {
            logs.clear();
            return;
        }
        npc.swingHand(Hand.MAIN_HAND, true);
        npc.needsWoodDelivery = true;
        npc.memory.lastTreePos = base;
        swingDelay = 4;
        for (ItemStack stack : drops) {
            ItemStack remaining = npc.getCargo().addStack(stack);
            if (!remaining.isEmpty()) npc.dropStack(remaining);
        }
    }

    @Override
    public void stop() {
        // Hết thời gian mà chưa tới được gốc → bỏ qua cây này 1 phút, sang cây khác
        if (base != null && !atBase && approachTicks >= APPROACH_TIMEOUT) npc.skipTree(base, 1200);
        npc.getNavigation().stop();
        npc.activeTree = null;
        base = null;
        zoneChest = null;
        canopy.clear();
        logs.clear();
        retryAt = npc.getWorld().getTime() + 40;
        npc.memory.resetIdle();
    }

    // Follow the leaf distance gradient from this tree before removing its logs.
    private void collectCanopy(List<BlockPos> tree) {
        var world = npc.getWorld();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>(tree);
        while (!pending.isEmpty() && canopy.size() < MAX_LEAVES) {
            BlockPos current = pending.removeFirst();
            BlockState state = world.getBlockState(current);
            int distance = isNaturalLeaf(state) ? state.get(LeavesBlock.DISTANCE) : 0;
            if (distance >= 6) continue;
            for (Direction direction : Direction.values()) {
                BlockPos next = current.offset(direction);
                if (next.getY() < base.getY() || !world.isChunkLoaded(next)) continue;
                BlockState neighbor = world.getBlockState(next);
                if (isNaturalLeaf(neighbor) && neighbor.get(LeavesBlock.DISTANCE) == distance + 1
                        && canopy.add(next)) pending.addLast(next);
            }
        }
    }

    private boolean isNaturalLeaf(BlockState state) {
        return state.getBlock() instanceof LeavesBlock && !state.get(LeavesBlock.PERSISTENT);
    }

    private boolean allowsCutting(BlockPos target) {
        var world = npc.getWorld();
        return zoneChest != null && world.isChunkLoaded(zoneChest)
                && world.getBlockEntity(zoneChest) instanceof com.example.block.LumberjackChestBlockEntity chest
                && chest.allowsCutting(target);
    }

    private List<BlockPos> findConnectedLogs(BlockPos start) {
        var world = npc.getWorld();
        if (!allowsCutting(start) || !npc.isTreeRoot(start)) return List.of();

        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        List<BlockPos> tree = new ArrayList<>();
        visited.add(start);
        pending.add(start);
        while (!pending.isEmpty()) {
            if (tree.size() >= MAX_LOGS) {
                npc.display.report(npc, "Cụm gỗ quá lớn để chặt an toàn");
                return List.of();
            }
            BlockPos current = pending.removeFirst();
            tree.add(current);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos next = current.add(dx, dy, dz);
                        if (!visited.add(next)) continue;
                        if (!world.isChunkLoaded(next)) {
                            npc.display.report(npc, "Chưa tải đủ khu vực quanh cây");
                            return List.of();
                        }
                        if (world.getBlockState(next).isIn(BlockTags.LOGS)) pending.addLast(next);
                    }
                }
            }
        }
        return tree;
    }
}
