package com.example.entity;

import com.example.ai.lumberjack.ChopTreeGoal;
import com.example.ai.lumberjack.DepositWoodToChestGoal;
import com.example.ai.lumberjack.LumberjackMemory;
import com.example.ai.lumberjack.PlantSaplingGoal;
import com.example.ai.lumberjack.WanderNearChestGoal;
import com.example.block.LumberjackChestBlockEntity;
import com.example.block.ProfessionChestBlockEntity;
import com.example.entity.base.WorkerNpcEntity;
import com.example.registry.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/** Tiều phu: chặt cây trong vùng chặt của Rương Tiều phu, trồng lại mầm, nộp gỗ về rương. */
public class LumberjackNpcEntity extends WorkerNpcEntity {
    public final static int FIND_CHEST_DISTANCE = 16;
    public final static int FIND_TREE_DISTANCE = 8;
    private static final int TREE_SEARCH_COOLDOWN = 20; // 1 giây

    public final LumberjackMemory memory = new LumberjackMemory();
    public final SimpleInventory plantingSaplings = new SimpleInventory(27);
    public BlockPos saplingChestPos;
    public boolean returningSaplings;
    public BlockPos activeTree;
    public boolean needsWoodDelivery;
    private int treeSearchCooldown = 0;

    public LumberjackNpcEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world, 9);
    }

    @Override
    protected void addWorkGoals() {
        this.goalSelector.add(5, new PlantSaplingGoal(this));
        this.goalSelector.add(6, new DepositWoodToChestGoal(this));
        this.goalSelector.add(7, new ChopTreeGoal(this));
        this.goalSelector.add(8, new WanderNearChestGoal(this));
    }

    @Override
    protected Class<? extends ProfessionChestBlockEntity> chestType() {
        return LumberjackChestBlockEntity.class;
    }

    @Override
    public Item tokenItem() {
        return ModItems.LUMBERJACK_TOKEN;
    }

    @Override
    public String professionName() {
        return "Tiều phu";
    }

    @Override
    protected ItemStack workTool() {
        return new ItemStack(Items.IRON_AXE);
    }

    @Override
    protected int chestSearchRadius() {
        return FIND_CHEST_DISTANCE;
    }

    @Override
    protected int chestSearchHeight() {
        return 2;
    }

    @Override
    protected String legacyCargoKey() {
        return "FoodInventory";
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (getNavigation().isIdle()) memory.tickIdle();
        else memory.resetIdle();
        if (treeSearchCooldown > 0) treeSearchCooldown--;
    }

    // ===== NBT =====

    @Override
    protected void writeWorkerNbt(NbtCompound nbt) {
        nbt.put("PlantingSaplings", plantingSaplings.toNbtList());
        if (saplingChestPos != null) nbt.putLong("SaplingChestPos", saplingChestPos.asLong());
        nbt.putBoolean("ReturningSaplings", returningSaplings);
        nbt.putBoolean("NeedsWoodDelivery", needsWoodDelivery);
        nbt.putBoolean("IsLumberjackNpc", true);
    }

    @Override
    protected void readWorkerNbt(NbtCompound nbt) {
        needsWoodDelivery = nbt.getBoolean("NeedsWoodDelivery");
        if (nbt.contains("PlantingSaplings")) plantingSaplings.readNbtList(nbt.getList("PlantingSaplings", 10));
        saplingChestPos = nbt.contains("SaplingChestPos") ? BlockPos.fromLong(nbt.getLong("SaplingChestPos")) : null;
        returningSaplings = nbt.getBoolean("ReturningSaplings");
    }

    @Override
    protected void dropWorkerItems() {
        dropInventory(plantingSaplings);
    }

    // ===== Đặt chỗ cây =====

    public boolean reserveTree(BlockPos pos) {
        return reservations().tryReserve(pos, getUuid(), "TREE");
    }

    public void releaseTree(BlockPos pos) {
        reservations().release(pos, getUuid(), "TREE");
    }

    // ===== Tìm cây =====

    public boolean isWorkedByAnother(BlockPos tree) {
        return !getWorld().getEntitiesByClass(LumberjackNpcEntity.class,
                new Box(tree).expand(8, 40, 8),
                other -> other != this && other.activeTree != null
                        && Math.abs(tree.getX() - other.activeTree.getX()) <= 2
                        && Math.abs(tree.getZ() - other.activeTree.getZ()) <= 2
                        && Math.abs(tree.getY() - other.activeTree.getY()) <= 40).isEmpty();
    }

    /** Tìm gốc cây gần nhất trong vùng chặt đã bật của rương. */
    public BlockPos findNearestTree() {
        if (treeSearchCooldown > 0) return null;
        BlockPos chestPos = findWorkChest();
        if (chestPos == null) return null;

        World world = getWorld();
        if (!(world.getBlockEntity(chestPos) instanceof LumberjackChestBlockEntity zone)
                || !zone.allowsCutting(chestPos)) {
            display.report(this, "Chưa bật vùng chặt trên rương tiều phu");
            treeSearchCooldown = TREE_SEARCH_COOLDOWN;
            return null;
        }

        int radius = LumberjackChestBlockEntity.CUT_RADIUS;
        BlockPos found = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -LumberjackChestBlockEntity.CUT_BELOW; dy <= LumberjackChestBlockEntity.CUT_ABOVE; dy++) {
                    BlockPos pos = chestPos.add(dx, dy, dz);
                    if (!zone.allowsCutting(pos) || !isTreeRoot(pos) || isWorkedByAnother(pos)) continue;
                    double dist = squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (dist < bestDist) {
                        bestDist = dist;
                        found = pos;
                    }
                }
            }
        }

        treeSearchCooldown = TREE_SEARCH_COOLDOWN;
        if (found == null) display.report(this, "Chưa có cây trong vùng chặt");
        return found;
    }

    public boolean isTreeRoot(BlockPos pos) {
        World world = getWorld();
        if (!world.isChunkLoaded(pos)) return false;
        BlockState log = world.getBlockState(pos);
        if (!log.isIn(BlockTags.LOGS)) return false;
        BlockState below = world.getBlockState(pos.down());
        return below.isIn(BlockTags.DIRT)
                || (log.isOf(Blocks.MANGROVE_LOG)
                    && (below.isOf(Blocks.MANGROVE_ROOTS) || below.isOf(Blocks.MUDDY_MANGROVE_ROOTS)
                        || below.isOf(Blocks.MUD)));
    }

    /** Kiểm tra block có phải thân gỗ hoặc lá. */
    public boolean isTreeBlock(BlockState state) {
        Block block = state.getBlock();
        return block instanceof PillarBlock && (
                block == Blocks.OAK_LOG ||
                        block == Blocks.SPRUCE_LOG ||
                        block == Blocks.BIRCH_LOG ||
                        block == Blocks.JUNGLE_LOG ||
                        block == Blocks.ACACIA_LOG ||
                        block == Blocks.DARK_OAK_LOG ||
                        block == Blocks.MANGROVE_LOG ||
                        block == Blocks.CHERRY_LOG
        ) || block instanceof LeavesBlock;
    }
}
