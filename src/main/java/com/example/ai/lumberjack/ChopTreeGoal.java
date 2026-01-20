package com.example.ai.lumberjack;

import com.example.entity.LumberjackNpcEntity;
import net.minecraft.block.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public class ChopTreeGoal extends Goal {
    private final LumberjackNpcEntity npc;
    private BlockPos targetTree;
    private int taskTicks = 0;
    private int pickupDelay = 0;
    private BlockPos lastChopPos;
    private Set<BlockPos> treeBlocks = new HashSet<>();
    private int maxTaskTicks = 0;

    // Tầm chặt
    private static final double CHOP_RANGE_HORIZONTAL = 5.0; // Tầm ngang (horiz)
    private static final double CHOP_RANGE_VERTICAL = 6.0; // Tầm dọc (vertical)
    private static final double CHOP_RANGE_MAX = 8.0; // Tầm chặt tối đa khi nhìn thẳng

    private BlockPos basePos;
    private int treeHeight = 0;
    private BlockPos currentTargetBlock;

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

        calculateTreeHeight();
        calculateChoppingTime();

        return true;
    }

    @Override
    public void start() {
        taskTicks = 0;
        basePos = findLowestLog();
        currentTargetBlock = null;
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
        if (currentTargetBlock == null || npc.getWorld().getBlockState(currentTargetBlock).isAir()) {

            currentTargetBlock = findBestBlockToChop();
        }
        if (currentTargetBlock == null) return;

        Vec3d npcPos = npc.getPos();

        double dist3D = npcPos.distanceTo(currentTargetBlock.toCenterPos());
        double horizontalDist = Math.sqrt(Math.pow(currentTargetBlock.getX() - npcPos.x, 2) + Math.pow(currentTargetBlock.getZ() - npcPos.z, 2));
        double verticalDist = currentTargetBlock.getY() - npcPos.y;

        if (isInChopRange(horizontalDist, verticalDist, dist3D)) {
            chopBlockAtPos(currentTargetBlock);
            treeBlocks.remove(currentTargetBlock);
            currentTargetBlock = null;
        } else {
            moveCloserToBlock(currentTargetBlock);
        }
    }

    @Override
    public boolean shouldContinue() {
        if (taskTicks > maxTaskTicks) return false;
        if (pickupDelay > 0) return true;
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
        currentTargetBlock = null;
        pickupDelay = 0;
        taskTicks = 0;
        treeHeight = 0;
        basePos = null;
        maxTaskTicks = 0;
        npc.memory.resetIdle();
    }

    /**
     * Quét tất cả blocks của cây (log + leaves) - quét rộng hơn
     */
    private void scanTree(BlockPos start) {
        treeBlocks.clear();
        if (start == null) return;
        BlockPos chestPos = npc.memory.lastChestPos;
        if (chestPos == null) return;

        World world = npc.getWorld();

        int radius = 7;
        int maxHeight = targetTree.getY() + 30;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = targetTree.getY() - 3; dy <= maxHeight; dy++) {
                    BlockPos pos = new BlockPos(
                            start.getX() + dx,
                            dy,
                            start.getZ() + dz
                    );

                    if (!world.isChunkLoaded(pos)) continue;
                    BlockState state = world.getBlockState(pos);
                    if (npc.isTreeBlock(state)) {  // ✅ Tái sử dụng hàm từ Entity
                        treeBlocks.add(pos);
                    }
                }
            }
        }

    }

    /**
     * Kiểm tra block có nằm trong tầm chặt không
     * - Ngang: 5 block
     * - Dọc: 6 block (lên hoặc xuống)
     * - Tối đa: 8 block (đường thẳng)
     */
    private boolean isInChopRange(double horizontalDist, double verticalDist, double dist3D) {
        // Nếu trong tầm ngang + dọc
        if (horizontalDist <= CHOP_RANGE_HORIZONTAL && Math.abs(verticalDist) <= CHOP_RANGE_VERTICAL) {
            return true;
        }

        // Hoặc nếu trong tầm tối đa (đường thẳng)
        if (dist3D <= CHOP_RANGE_MAX) {
            return true;
        }

        return false;
    }

    /**
     * Tìm block tốt nhất để chặt
     * Ưu tiên: log từ thấp lên cao -> lá
     */
    private BlockPos findBestBlockToChop() {
        Vec3d npcPos = npc.getPos();

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (BlockPos pos : treeBlocks) {
            // ✅ Check block còn tồn tại không
            BlockState state = npc.getWorld().getBlockState(pos);
            if (state.isAir()) continue;

            Block block = state.getBlock();

            double score;

            if (block instanceof PillarBlock) {
                // ✅ Log: Ưu tiên từ dưới lên (Y thấp trước)
                // Y * 500 = log dưới được ưu tiên
                score = pos.getY() * 500 + npcPos.squaredDistanceTo(pos.toCenterPos());
            } else if (block instanceof LeavesBlock) {
                // ✅ Leaves: Ưu tiên sau cùng
                score = 1_000_000 + npcPos.squaredDistanceTo(pos.toCenterPos());
            } else {
                continue;
            }

            if (score < bestScore) {
                bestScore = score;
                best = pos;
            }
        }
        return best;
    }

    /**
     * Chặt block tại vị trí cụ thể
     */
    private void chopBlockAtPos(BlockPos pos) {
        npc.getLookControl().lookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        World world = npc.getWorld();

        // ✅ Phá block và drop items
        world.breakBlock(pos, true, npc);

        lastChopPos = pos.toImmutable();
        pickupDelay = 5; // ✅ Delay 5 ticks rồi nhặt items
        npc.memory.lastTreePos = pos;
        npc.memory.resetIdle();

    }

    /**
     * Di chuyển NPC gần block
     */
    private void moveCloserToBlock(BlockPos block) {
        // Fallback: di chuyển tới chính block
        npc.getNavigation().startMovingTo(block.getX() + 2.5, block.getY(), block.getZ() + 2.5, 1.2f);
    }

    /**
     * Nhặt items rơi gần đó
     */
    private void pickupNearbyDrops() {
        List<ItemEntity> drops = npc.getWorld().getEntitiesByClass(ItemEntity.class, new Box(lastChopPos).expand(4.0), item -> item.isAlive());

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

    /**
     * Tính chiều cao cây
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

        treeHeight = (minY == Integer.MAX_VALUE) ? 1 : (maxY - minY + 1);
    }

    /**
     * Tính thời gian chặt
     */
    private void calculateChoppingTime() {
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

        maxTaskTicks = (logCount * 15) + (leafCount * 2) + 60;
        maxTaskTicks = Math.max(80, Math.min(1200, maxTaskTicks));
    }

    /**
     * Tìm log thấp nhất
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
}