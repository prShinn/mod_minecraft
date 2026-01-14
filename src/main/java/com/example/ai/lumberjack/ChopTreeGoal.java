package com.example.ai.lumberjack;

import com.example.entity.LumberjackNpcEntity;
import net.minecraft.block.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.*;

public class ChopTreeGoal extends Goal {
    private final LumberjackNpcEntity npc;
    private BlockPos targetTree;
    private int taskTicks = 0;
    private int pickupDelay = 0;
    private BlockPos lastChopPos;
    private Set<BlockPos> treeBlocks = new HashSet<>();
    private int maxTaskTicks = 0;
    private static final double CHOP_DISTANCE = 27.0;
    private BlockPos basePos;
    private int treeHeight = 0;

    public ChopTreeGoal(LumberjackNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        BlockPos tree = npc.findNearestTree();
        if (tree == null) return false;

        if (!npc.reserveTree(tree)) return false;

        targetTree = tree;
        scanTree(tree);
        if (treeBlocks.isEmpty()) {
            npc.releaseTree(tree);
            return false;
        }

        // Tính chiều cao và thời gian chặt
        calculateTreeHeight();
        calculateChoppingTime();

        return true;

    }

    @Override
    public void start() {
        taskTicks = 0;

        // Di chuyển đến gốc cây (tìm log thấp nhất)
        basePos = findLowestLog();
        if (basePos != null) {
            npc.getNavigation().startMovingTo(
                    basePos.getX() + 0.5,
                    basePos.getY(),
                    basePos.getZ() + 0.5,
                    1.2f
            );
        }

    }

    @Override
    public void tick() {
        taskTicks++;

        if (pickupDelay > 0) {
            pickupDelay--;
            if (pickupDelay == 0 && lastChopPos != null) {
                pickupNearbyDrops();
            }
            return;
        }
        // Nếu chưa đến gốc cây thì chờ
        if (basePos != null && npc.squaredDistanceTo(
                basePos.getX() + 0.5,
                basePos.getY() + 0.5,
                basePos.getZ() + 0.5
        ) > 2 * 2) {
            return;
        }
        // Tìm log trong tầm chặt
        BlockPos nextLog = findLowestLogInTree();

        if (nextLog != null) {
            npc.getLookControl().lookAt(
                    nextLog.getX() + 0.5,
                    nextLog.getY() + 0.5,
                    nextLog.getZ() + 0.5
            );

            chopBlock(nextLog);
            treeBlocks.remove(nextLog);
        } else {
            // Không còn log -> chặt leaves
            BlockPos nextLeaf = findAnyLeafInTree();
            if (nextLeaf != null) {
                chopBlock(nextLeaf);
                treeBlocks.remove(nextLeaf);
            }
        }
    }

    @Override
    public boolean shouldContinue() {
        if (taskTicks > maxTaskTicks) return false;
        if (pickupDelay > 0) return true;

        // Tiếp tục nếu còn block cây trong tầm
        return !treeBlocks.isEmpty();
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        if (targetTree != null) {
            npc.releaseTree(targetTree);
        }
        targetTree = null;
        treeBlocks.clear();
        lastChopPos = null;
        pickupDelay = 0;
        taskTicks = 0;
        treeHeight = 0;
        basePos = null;
        maxTaskTicks = 0;
        npc.memory.resetIdle();
    }
    /**
     * Tính chiều cao cây (từ log thấp nhất đến cao nhất)
     */
    private void calculateTreeHeight() {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (BlockPos pos : treeBlocks) {
            BlockState state = npc.getWorld().getBlockState(pos);
            if (state.getBlock() instanceof PillarBlock) {
                minY = Math.min(minY, pos.getY());
                maxY = Math.max(maxY, pos.getY());
            }
        }

        treeHeight = maxY - minY + 1;
    }
    /**
     * Tính thời gian chặt dựa trên chiều cao cây
     * Công thức: chiều_cao × 25 ticks (1.25 giây/block cao)
     * Cây càng cao càng lâu chặt
     */
    private void calculateChoppingTime() {
        // Mỗi block chiều cao = 25 ticks (1.25 giây)
        // Cộng thêm 60 ticks buffer và thời gian cho leaves
        int logCount = 0;
        int leafCount = 0;

        for (BlockPos pos : treeBlocks) {
            BlockState state = npc.getWorld().getBlockState(pos);
            if (state.getBlock() instanceof PillarBlock) {
                logCount++;
            } else if (state.getBlock() instanceof LeavesBlock) {
                leafCount++;
            }
        }

        // Công thức: (chiều_cao × 20) + (số_lá × 3) + 60 buffer
        maxTaskTicks = (treeHeight * 20) + (leafCount * 2) + 60;

        // Giới hạn: tối thiểu 80 ticks (4s), tối đa 1500 ticks (300s)
        maxTaskTicks = Math.max(80, Math.min(600, maxTaskTicks));
    }
    /**
     * Quét tất cả blocks của cây (log + leaves)
     */
    private void scanTree(BlockPos start) {
        treeBlocks.clear();
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && treeBlocks.size() < 1500) { // Giới hạn 200 blocks
            BlockPos pos = queue.poll();
            BlockState state = npc.getWorld().getBlockState(pos);
            Block block = state.getBlock();

            // Kiểm tra log hoặc leaves
            if (block instanceof PillarBlock || block instanceof LeavesBlock) {
                treeBlocks.add(pos);

                // Tìm các block liền kề
                for (BlockPos neighbor : getNeighbors(pos, block instanceof PillarBlock)) {
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
        }
    }

    private List<BlockPos> getNeighbors(BlockPos pos, boolean isLog) {
        List<BlockPos> neighbors = new ArrayList<>();
        int range = isLog ? 2 : 1;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    neighbors.add(pos.add(dx, dy, dz));
                }
            }
        }
        return neighbors;
    }

    /**
     * Tìm log thấp nhất (gốc cây)
     */
    private BlockPos findLowestLog() {
        BlockPos lowest = null;
        int lowestY = Integer.MAX_VALUE;

        for (BlockPos pos : treeBlocks) {
            BlockState state = npc.getWorld().getBlockState(pos);
            if (state.getBlock() instanceof PillarBlock) {
                if (pos.getY() < lowestY) {
                    lowestY = pos.getY();
                    lowest = pos;
                }
            }
        }
        return lowest;
    }
    /**
     * Tìm log thấp nhất còn lại (để chặt từ dưới lên)
     */
    private BlockPos findLowestLogInTree() {
        BlockPos lowest = null;
        int lowestY = Integer.MAX_VALUE;

        for (BlockPos pos : treeBlocks) {
            BlockState state = npc.getWorld().getBlockState(pos);
            if (!(state.getBlock() instanceof PillarBlock)) continue;

            if (pos.getY() < lowestY) {
                lowestY = pos.getY();
                lowest = pos;
            }
        }
        return lowest;
    }
    /**
     * Tìm lá bất kỳ còn lại
     */
    private BlockPos findAnyLeafInTree() {
        for (BlockPos pos : treeBlocks) {
            BlockState state = npc.getWorld().getBlockState(pos);
            if (state.getBlock() instanceof LeavesBlock) {
                return pos;
            }
        }
        return null;
    }

    /**
     * Chặt block
     */
    private void chopBlock(BlockPos pos) {
        var world = npc.getWorld();
        var state = world.getBlockState(pos);

        // Drop items
//        Block.dropStacks(state, world, pos, null, npc, ItemStack.EMPTY);
        // Xóa block
//        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        world.breakBlock(pos, true, npc);
        lastChopPos = pos.toImmutable();
        pickupDelay = 5;
        npc.memory.lastTreePos = pos;
        npc.memory.resetIdle();
    }

    /**
     * Nhặt items rơi gần đó
     */
    private void pickupNearbyDrops() {
        List<ItemEntity> drops = npc.getWorld().getEntitiesByClass(
                ItemEntity.class,
                new Box(lastChopPos).expand(4.0),
                item -> item.isAlive()
        );

        for (ItemEntity item : drops) {
            ItemStack stack = item.getStack();
            boolean addedAll = addToInventory(npc.getInventory(), stack);

            if (addedAll) {
                item.discard();
            }
        }
    }

    private boolean addToInventory(SimpleInventory inv, ItemStack stack) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack slot = inv.getStack(i);

            if (slot.isEmpty()) {
                inv.setStack(i, stack.copy());
                stack.setCount(0);
                return true;
            }

            if (ItemStack.canCombine(slot, stack) && slot.getCount() < slot.getMaxCount()) {
                int move = Math.min(stack.getCount(), slot.getMaxCount() - slot.getCount());
                slot.increment(move);
                stack.decrement(move);

                if (stack.isEmpty()) return true;
            }
        }
        return stack.isEmpty();
    }
}
