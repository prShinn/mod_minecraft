package com.example.ai.lumberjack;

import com.example.entity.LumberjackNpcEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;

public class DepositWoodToChestGoal extends Goal {
    private final LumberjackNpcEntity npc;
    private BlockPos chestPos;
    private int taskTicks = 0;
    private static final int MAX_TASK_TICKS = 60;
    private static final int SEARCH_RADIUS = 10;

    public DepositWoodToChestGoal(LumberjackNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Control.MOVE));
    }
    private boolean hasItems() {
        for (int i = 0; i < npc.inventory.size(); i++) {
            if (!npc.inventory.getStack(i).isEmpty()) return true;
        }
        return false;
    }

    @Override
    public boolean canStart() {
        if (!hasItems()) return false;

        chestPos = findNearestChest();
        return chestPos != null;
    }

    @Override
    public void start() {
        taskTicks = 0;
        npc.getNavigation().startMovingTo(
                chestPos.getX() + 3,
                chestPos.getY(),
                chestPos.getZ() + 3,
                1.3
        );
        npc.reserveChest(chestPos);
    }

    @Override
    public void tick() {
        taskTicks++;

        if (taskTicks > MAX_TASK_TICKS || !npc.getWorld().isChunkLoaded(chestPos)) {
            stop();
            return;
        }

        if (npc.squaredDistanceTo(Vec3d.ofCenter(chestPos)) < 4 * 4) {
            depositItems();
            stop();
        }
    }

    @Override
    public boolean shouldContinue() {
        return chestPos != null && taskTicks < MAX_TASK_TICKS;
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        if (chestPos != null) {
            npc.releaseChest(chestPos);
        }
        chestPos = null;
        taskTicks = 0;
    }
    private void depositItems() {
        BlockEntity be = npc.getWorld().getBlockEntity(chestPos);
        Inventory chestInv = null;

        if (be instanceof ChestBlockEntity) {
            BlockState state = npc.getWorld().getBlockState(chestPos);

            if (state.getBlock() instanceof ChestBlock chestBlock) {
                chestInv = ChestBlock.getInventory(
                        chestBlock,
                        state,
                        npc.getWorld(),
                        chestPos,
                        true
                );
            }
        }

        if (chestInv == null) {
            if (be instanceof Inventory) {
                chestInv = (Inventory) be;
            } else {
                return;
            }
        }

        // Transfer items từ NPC vào chest
        for (int i = 0; i < npc.inventory.size(); i++) {
            ItemStack npcStack = npc.inventory.getStack(i);
            if (npcStack.isEmpty() || !isTreeItem(npcStack)) continue;

            for (int j = 0; j < chestInv.size(); j++) {
                ItemStack chestStack = chestInv.getStack(j);

                if (chestStack.isEmpty()) {
                    chestInv.setStack(j, npcStack.copy());
                    npc.inventory.setStack(i, ItemStack.EMPTY);
                    break;
                } else if (ItemStack.canCombine(chestStack, npcStack) &&
                        chestStack.getCount() < chestStack.getMaxCount()) {
                    int canAdd = chestStack.getMaxCount() - chestStack.getCount();
                    int toAdd = Math.min(canAdd, npcStack.getCount());
                    chestStack.increment(toAdd);
                    npcStack.decrement(toAdd);

                    if (npcStack.isEmpty()) {
                        npc.inventory.setStack(i, ItemStack.EMPTY);
                        break;
                    }
                }
            }

            if (!npcStack.isEmpty()) {
                npc.inventory.setStack(i, npcStack);
            }
        }

        npc.memory.lastChestPos = chestPos;
        npc.memory.resetIdle();
    }

    private BlockPos findNearestChest() {
        BlockPos center = npc.getBlockPos();
        double closestDist = Double.MAX_VALUE;
        BlockPos closestChest = null;
        World world = npc.getWorld();

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (!world.isChunkLoaded(pos)) continue;

                    BlockEntity be = world.getBlockEntity(pos);

                    if (be instanceof ChestBlockEntity || be instanceof net.minecraft.block.entity.BarrelBlockEntity) {
                        if (npc.isChestReserved(pos)) continue;

                        double dist = npc.squaredDistanceTo(Vec3d.ofCenter(pos));
                        if (dist < closestDist) {
                            closestDist = dist;
                            closestChest = pos;
                        }
                    }
                }
            }
        }
        return closestChest;
    }
    private boolean isTreeItem(ItemStack stack) {
        Item item = stack.getItem();

        return item instanceof net.minecraft.item.BlockItem blockItem &&
                (blockItem.getBlock() instanceof net.minecraft.block.PillarBlock ||   // log, wood
                        blockItem.getBlock() instanceof net.minecraft.block.LeavesBlock ||   // leaves
                        blockItem.getBlock() instanceof net.minecraft.block.SaplingBlock)    // sapling
                || item == net.minecraft.item.Items.APPLE;
    }

}
