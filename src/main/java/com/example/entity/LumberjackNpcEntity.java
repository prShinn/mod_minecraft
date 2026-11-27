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
    public final static int FIND_CHEST_DISTANCE = 24;
    public final static int FIND_TREE_DISTANCE = 8;
    private static final int TREE_SEARCH_COOLDOWN = 60; // 3 giây
    private static final int TREE_SEARCH_IDLE_COOLDOWN = 100; // không thấy cây: 5 giây

    public final LumberjackMemory memory = new LumberjackMemory();
    public final SimpleInventory plantingSaplings = new SimpleInventory(27);
    public BlockPos saplingChestPos;
    public boolean returningSaplings;
    public BlockPos activeTree;
    public boolean needsWoodDelivery;
    private int treeSearchCooldown = 0;
    /** Gốc cây tìm được ở lần quét gần nhất (dùng lại trong lúc chờ quét). */
    @org.jetbrains.annotations.Nullable
    private BlockPos cachedTree;

    public LumberjackNpcEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world, 9);
        // Mặc định pathfinder coi lá là tường (penalty -1): cây mới mọc chắn lối là mất đường tới rương.
        // Tiều phu được tính đường xuyên lá (hơi đắt hơn đất trống) rồi chặt lá trên đường đi — xem clearLeavesOnPath().
        setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.LEAVES, LEAVES_PATH_PENALTY);
    }

    private static final float LEAVES_PATH_PENALTY = 4.0F;
    private static final double LEAVES_CLEAR_RANGE_SQ = 3.0 * 3.0;

    @Override
    protected void addWorkGoals() {
        this.goalSelector.add(5, new PlantSaplingGoal(this));
        this.goalSelector.add(6, new DepositWoodToChestGoal(this));
        this.goalSelector.add(7, new ChopTreeGoal(this));
        this.goalSelector.add(9, new WanderNearChestGoal(this));
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
        return 3;
    }

    /** Cây mọc trùm lên người → chặt ngay gỗ/lá đang đè, đồ cho vào túi. */
    @Override
    protected boolean canChopBlockingTrees() {
        return true;
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
        if (age % 5 == 0) clearLeavesOnPath();
    }

    /** Chặt lá nằm trên 2 nút đường kế tiếp (ngang chân và ngang đầu), lá cho vào túi. */
    private void clearLeavesOnPath() {
        var path = getNavigation().getCurrentPath();
        if (path == null || path.isFinished() || !(getWorld() instanceof net.minecraft.server.world.ServerWorld world)) return;
        int from = path.getCurrentNodeIndex();
        for (int i = from; i < Math.min(from + 2, path.getLength()); i++) {
            BlockPos node = path.getNodePos(i);
            for (BlockPos pos : new BlockPos[]{node, node.up()}) {
                if (getBlockPos().getSquaredDistance(pos) > LEAVES_CLEAR_RANGE_SQ) continue;
                BlockState state = world.getBlockState(pos);
                if (com.example.helper.NpcStuckHelper.isNaturalLeaves(state)) { // không phá lá người chơi đặt
                    swingHand(net.minecraft.util.Hand.MAIN_HAND);
                    com.example.helper.NpcStuckHelper.breakInto(world, this, pos, state, cargo);
                }
            }
        }
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

    private static boolean isNearTree(java.util.List<BlockPos> busy, BlockPos tree) {
        for (BlockPos b : busy) {
            if (Math.abs(tree.getX() - b.getX()) <= 2 && Math.abs(tree.getZ() - b.getZ()) <= 2) return true;
        }
        return false;
    }

    public boolean isWorkedByAnother(BlockPos tree) {
        return !getWorld().getEntitiesByClass(LumberjackNpcEntity.class,
                new Box(tree).expand(8, 40, 8),
                other -> other != this && other.activeTree != null
                        && Math.abs(tree.getX() - other.activeTree.getX()) <= 2
                        && Math.abs(tree.getZ() - other.activeTree.getZ()) <= 2
                        && Math.abs(tree.getY() - other.activeTree.getY()) <= 40).isEmpty();
    }

    /** Gốc cây không tới được → bỏ qua tới thời điểm (world time) này, tránh chọn lại mãi. */
    private final java.util.Map<BlockPos, Long> skippedTrees = new java.util.HashMap<>();

    public void skipTree(BlockPos root, int ticks) {
        skippedTrees.put(root.toImmutable(), getWorld().getTime() + ticks);
    }

    private boolean isTreeSkipped(BlockPos root) {
        Long until = skippedTrees.get(root);
        return until != null && until > getWorld().getTime();
    }

    /** Tìm gốc cây gần nhất trong vùng chặt đã bật của rương. */
    public BlockPos findNearestTree() {
        if (treeSearchCooldown > 0) {
            // Đang chờ quét lại: trả kết quả lần trước nếu cây đó vẫn còn (goal chặt / goal đi dạo hỏi xen kẽ
            // không bị "null" giả trong lúc chờ)
            // Chỉ kiểm tra rẻ (đọc block); việc "tiều phu khác đang chặt" do goal chặt kiểm tra một lần khi bắt đầu
            if (cachedTree != null && (!isTreeRoot(cachedTree) || isTreeSkipped(cachedTree))) {
                cachedTree = null;
            }
            return cachedTree;
        }
        BlockPos chestPos = findWorkChest();
        if (chestPos == null) return null;

        World world = getWorld();
        if (!(world.getBlockEntity(chestPos) instanceof LumberjackChestBlockEntity zone)
                || !zone.allowsCutting(chestPos)) {
            display.report(this, "Chưa bật vùng chặt trên rương tiều phu");
            treeSearchCooldown = TREE_SEARCH_COOLDOWN;
            return null;
        }

        long now = world.getTime();
        skippedTrees.values().removeIf(until -> until <= now);
        int radius = zone.getCutRadius(); // bán kính riêng của rương (3 / 5 / 8)
        // Cây các tiều phu khác đang chặt: 1 truy vấn entity cho cả lượt quét (thay vì mỗi gốc cây một lần)
        java.util.List<BlockPos> busyTrees = new java.util.ArrayList<>();
        for (LumberjackNpcEntity other : world.getEntitiesByClass(LumberjackNpcEntity.class,
                new Box(chestPos).expand(radius + 8, 72, radius + 8), o -> o != this && o.activeTree != null)) {
            busyTrees.add(other.activeTree);
        }
        BlockPos found = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = chestPos.getX() + dx, z = chestPos.getZ() + dz;
                if (!world.isChunkLoaded(new BlockPos(x, chestPos.getY(), z))) continue;
                // Gốc cây luôn nằm dưới đỉnh cột (heightmap bỏ lá): khỏi đọc khoảng trời trống phía trên (70 → ~10 ô/cột)
                int top = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                int maxDy = Math.min(LumberjackChestBlockEntity.CUT_ABOVE, top - chestPos.getY());
                for (int dy = -LumberjackChestBlockEntity.CUT_BELOW; dy <= maxDy; dy++) {
                    BlockPos pos = chestPos.add(dx, dy, dz);
                    if (!zone.allowsCutting(pos) || !isTreeRoot(pos) || isTreeSkipped(pos) || isNearTree(busyTrees, pos)) continue;
                    double dist = squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (dist < bestDist) {
                        bestDist = dist;
                        found = pos;
                    }
                }
            }
        }

        // Không có cây thì cây mới cần cả phút mới lớn: chờ 5 giây mới quét lại
        treeSearchCooldown = found == null ? TREE_SEARCH_IDLE_COOLDOWN : TREE_SEARCH_COOLDOWN;
        cachedTree = found;
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
