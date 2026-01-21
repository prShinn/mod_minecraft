package com.example.ai.lumberjack;

import com.example.entity.LumberjackNpcEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;

public class PlantSaplingGoal extends Goal {
    private final LumberjackNpcEntity npc;
    private BlockPos plantPos;
    private int taskTicks = 0;
    private static final int MAX_TASK_TICKS = 160;
    private static final double PLANT_DIST_SQ = 2.25;
    private static final int PLANT_DISTANCE = 2;
    private int searchCooldown = 0;
    public PlantSaplingGoal(LumberjackNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }
    @Override
    public boolean canStart() {
        if (searchCooldown-- > 0) return false;
        searchCooldown = 20;

        // Kiểm tra có mầm cây không
        if (!hasSapling()) return false;

        // Tìm vị trí trống để trồng
        BlockPos found = findPlantingSpot();
        if (found == null) return false;

        plantPos = found;
        return true;
    }
    @Override
    public void start() {
        taskTicks = 0;
        npc.getNavigation().startMovingTo(
                plantPos.getX() + 1.5,
                plantPos.getY(),
                plantPos.getZ() + 1.5,
                1.2
        );
    }
    @Override
    public void tick() {
        taskTicks++;

        if (plantPos == null || !npc.getWorld().isChunkLoaded(plantPos)) {
            stop();
            return;
        }

        // Đến gần vị trí trồng
        if (npc.squaredDistanceTo(Vec3d.ofCenter(plantPos)) < PLANT_DIST_SQ) {
            plantSapling();
            plantPos = findPlantingSpot();
            if (plantPos == null || !hasSapling()) {
                stop();
                return;
            }

            npc.getNavigation().startMovingTo(
                    plantPos.getX() + 0.5,
                    plantPos.getY(),
                    plantPos.getZ() + 0.5,
                    1.2
            );

        }
    }
    @Override
    public boolean shouldContinue() {
        if (taskTicks > MAX_TASK_TICKS) return false;
        if (plantPos == null) return false;

        World world = npc.getWorld();
        BlockState ground = world.getBlockState(plantPos);
        BlockState above = world.getBlockState(plantPos.up());

        // Vẫn là đất và không có gì ở trên
        return isValidGround(ground.getBlock()) && above.isAir();
    }
    @Override
    public void stop() {
        npc.getNavigation().stop();
        plantPos = null;
        taskTicks = 0;
    }
    /**
     * Kiểm tra có mầm cây trong inventory hoặc chest không
     */
    private boolean hasSapling() {
        // Kiểm tra inventory của NPC
        for (int i = 0; i < npc.inventory.size(); i++) {
            ItemStack stack = npc.inventory.getStack(i);
            if (!stack.isEmpty() && isSaplingItem(stack.getItem())) {
                return true;
            }
        }

        // Kiểm tra chest
        Inventory chest = npc.findNearestChest();
        if (chest != null) {
            for (int i = 0; i < chest.size(); i++) {
                ItemStack stack = chest.getStack(i);
                if (!stack.isEmpty() && isSaplingItem(stack.getItem())) {
                    return true;
                }
            }
        }

        return false;
    }
    /**
     * Lấy mầm cây từ inventory hoặc chest
     */
    private ItemStack takeSapling() {
        // Thử lấy từ inventory NPC trước
        for (int i = 0; i < npc.inventory.size(); i++) {
            ItemStack stack = npc.inventory.getStack(i);
            if (!stack.isEmpty() && isSaplingItem(stack.getItem())) {
                return npc.inventory.removeStack(i, 1);
            }
        }

        // Không có -> lấy từ chest
        Inventory chest = npc.findNearestChest();
        if (chest != null) {
            for (int i = 0; i < chest.size(); i++) {
                ItemStack stack = chest.getStack(i);
                if (!stack.isEmpty() && isSaplingItem(stack.getItem())) {
                    return chest.removeStack(i, 1);
                }
            }
        }

        return ItemStack.EMPTY;
    }
    /**
     * Kiểm tra item có phải sapling không
     */
    private boolean isSaplingItem(Item item) {
        return item == Items.OAK_SAPLING ||
                item == Items.SPRUCE_SAPLING ||
                item == Items.BIRCH_SAPLING ||
                item == Items.JUNGLE_SAPLING ||
                item == Items.ACACIA_SAPLING ||
                item == Items.DARK_OAK_SAPLING ||
                item == Items.MANGROVE_PROPAGULE ||
                item == Items.CHERRY_SAPLING;
    }
    /**
     * Kiểm tra block có phải đất hợp lệ không
     */
    private boolean isValidGround(Block block) {
        return block == Blocks.DIRT ||
                block == Blocks.GRASS_BLOCK ||
                block == Blocks.PODZOL ||
                block == Blocks.COARSE_DIRT ||
                block == Blocks.MYCELIUM;
    }
    /**
     * Tìm vị trí trống để trồng cây
     * Chỉ tìm trong vùng 7 blocks quanh chest và phải là dirt/grass
     */
    private BlockPos findPlantingSpot() {
        if (npc.memory.lastChestPos == null) {
            npc.findNearestChest();
        }

        if (npc.memory.lastChestPos == null) return null;

        BlockPos chestPos = npc.memory.lastChestPos;
        World world = npc.getWorld();
        double closestDist = Double.MAX_VALUE;
        BlockPos closestSpot = null;

        // Tìm trong bán kính 7 blocks quanh chest
        for (int dx = -PLANT_DISTANCE; dx <= PLANT_DISTANCE; dx++) {
            for (int dz = -PLANT_DISTANCE; dz <= PLANT_DISTANCE; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos pos = chestPos.add(dx, dy, dz);
                    if (!world.isChunkLoaded(pos)) continue;

                    BlockState ground = world.getBlockState(pos);
                    BlockState above = world.getBlockState(pos.up());

                    // Phải là đất và không có gì ở trên
                    if (isValidGround(ground.getBlock()) && above.isAir()) {
                        // Kiểm tra có đủ ánh sáng không (mầm cây cần ánh sáng)
                        if (world.getLightLevel(pos.up()) >= 0) {
                            double dist = npc.squaredDistanceTo(
                                    pos.getX() + 0.5,
                                    pos.getY() + 0.5,
                                    pos.getZ() + 0.5
                            );

                            if (dist < closestDist) {
                                closestDist = dist;
                                closestSpot = pos;
                            }
                        }
                    }
                }
            }
        }

        return closestSpot;
    }
    /**
     * Trồng mầm cây tại vị trí
     */
    private void plantSapling() {
        ItemStack sapling = takeSapling();
        if (sapling.isEmpty()) return;

        World world = npc.getWorld();
        BlockPos abovePos = plantPos.up();

        // Kiểm tra lại điều kiện
        if (!world.getBlockState(abovePos).isAir()) {
            // Vị trí không còn trống -> trả lại sapling
            npc.inventory.addStack(sapling);
            return;
        }

        // Trồng sapling tương ứng
        Block saplingBlock = getSaplingBlock(sapling.getItem());
        if (saplingBlock != null) {
            world.setBlockState(abovePos, saplingBlock.getDefaultState());
            npc.memory.lastTreePos = abovePos;
            npc.memory.resetIdle();
        } else {
            // Không phải sapling hợp lệ -> trả lại
            npc.inventory.addStack(sapling);
        }
    }
    /**
     * Lấy block sapling từ item
     */
    private Block getSaplingBlock(Item item) {
        if (item == Items.OAK_SAPLING) return Blocks.OAK_SAPLING;
        if (item == Items.SPRUCE_SAPLING) return Blocks.SPRUCE_SAPLING;
        if (item == Items.BIRCH_SAPLING) return Blocks.BIRCH_SAPLING;
        if (item == Items.JUNGLE_SAPLING) return Blocks.JUNGLE_SAPLING;
        if (item == Items.ACACIA_SAPLING) return Blocks.ACACIA_SAPLING;
        if (item == Items.DARK_OAK_SAPLING) return Blocks.DARK_OAK_SAPLING;
        if (item == Items.MANGROVE_PROPAGULE) return Blocks.MANGROVE_PROPAGULE;
        if (item == Items.CHERRY_SAPLING) return Blocks.CHERRY_SAPLING;
        return null;
    }
}
