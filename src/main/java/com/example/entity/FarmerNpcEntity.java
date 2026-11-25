package com.example.entity;

import com.example.ai.farmer.DepositToChestGoal;
import com.example.ai.farmer.FarmerMemory;
import com.example.ai.farmer.HarvestCropGoal;
import com.example.ai.farmer.PlantSeedGoal;
import com.example.ai.farmer.ReturnToFarmGoal;
import com.example.block.FarmerChestBlockEntity;
import com.example.block.ProfessionChestBlockEntity;
import com.example.entity.base.WorkerNpcEntity;
import com.example.registry.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Nông dân: thu hoạch và gieo lại ruộng quanh Rương Nông dân, nộp nông sản về rương. */
public class FarmerNpcEntity extends WorkerNpcEntity {
    private static final int FIND_CHEST_DISTANCE = 16;
    private static final int FARM_RADIUS = 12;
    private static final int FARMLAND_SEARCH_COOLDOWN = 40; // rải việc quét ruộng trong 2 giây

    public final FarmerMemory memory = new FarmerMemory();

    public BlockPos cachedMatureCrop = null;
    public BlockPos cachedEmptyFarmland = null;
    public BlockPos activeFarmPlot;
    public final List<ItemStack> plantingSeeds = new ArrayList<>();
    public BlockPos seedChestPos;
    public boolean returningSeeds;

    private BlockPos currentFarmPos;
    private UUID followPlayerUUID;
    private int farmlandSearchCooldown = getRandom().nextInt(40);

    public FarmerNpcEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world, 9);
    }

    @Override
    protected void addWorkGoals() {
        this.goalSelector.add(5, new HarvestCropGoal(this));
        this.goalSelector.add(6, new DepositToChestGoal(this));
        this.goalSelector.add(7, new PlantSeedGoal(this));
        this.goalSelector.add(8, new ReturnToFarmGoal(this));
        this.goalSelector.add(9, new WanderAroundFarGoal(this, 1.0D));
    }

    @Override
    protected Class<? extends ProfessionChestBlockEntity> chestType() {
        return FarmerChestBlockEntity.class;
    }

    @Override
    public Item tokenItem() {
        return ModItems.FARMER_TOKEN;
    }

    @Override
    public String professionName() {
        return "Nông dân";
    }

    @Override
    protected ItemStack workTool() {
        return new ItemStack(Items.IRON_HOE);
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
        if (farmlandSearchCooldown > 0) farmlandSearchCooldown--;
        else scanFarmland();
        if (getNavigation().isIdle()) memory.tickIdle();
        else memory.resetIdle();
    }

    // ===== NBT =====

    @Override
    protected void writeWorkerNbt(NbtCompound nbt) {
        NbtList seeds = new NbtList();
        for (ItemStack stack : plantingSeeds) seeds.add(stack.writeNbt(new NbtCompound()));
        nbt.put("PlantingSeeds", seeds);
        if (seedChestPos != null) nbt.putLong("SeedChestPos", seedChestPos.asLong());
        nbt.putBoolean("ReturningSeeds", returningSeeds);
        if (followPlayerUUID != null) nbt.putUuid("FollowPlayer", followPlayerUUID);
        if (currentFarmPos != null) nbt.putLong("CurrentFarmPos", currentFarmPos.asLong());
        nbt.putBoolean("IsFarmerNpc", true);
    }

    @Override
    protected void readWorkerNbt(NbtCompound nbt) {
        plantingSeeds.clear();
        for (var entry : nbt.getList("PlantingSeeds", 10)) {
            ItemStack stack = ItemStack.fromNbt((NbtCompound) entry);
            if (!stack.isEmpty() && isSeedItem(stack.getItem())) plantingSeeds.add(stack);
        }
        // Dữ liệu kiểu cũ: chỉ một túi hạt
        if (nbt.contains("PlantingSeed")) {
            ItemStack oldSeed = ItemStack.fromNbt(nbt.getCompound("PlantingSeed"));
            if (!oldSeed.isEmpty() && isSeedItem(oldSeed.getItem())) plantingSeeds.add(oldSeed);
        }
        seedChestPos = nbt.contains("SeedChestPos") ? BlockPos.fromLong(nbt.getLong("SeedChestPos")) : null;
        returningSeeds = nbt.getBoolean("ReturningSeeds");
        if (nbt.containsUuid("FollowPlayer")) followPlayerUUID = nbt.getUuid("FollowPlayer");
        if (nbt.contains("CurrentFarmPos")) currentFarmPos = BlockPos.fromLong(nbt.getLong("CurrentFarmPos"));
    }

    @Override
    protected void dropWorkerItems() {
        for (ItemStack seed : plantingSeeds) dropStack(seed);
        plantingSeeds.clear();
    }

    // ===== Đặt chỗ ruộng =====

    public boolean reserveCrop(BlockPos pos) {
        return reservations().tryReserve(pos, getUuid(), "CROP");
    }

    public void releaseCrop(BlockPos pos) {
        reservations().release(pos, getUuid(), "CROP");
    }

    public boolean reserveFarmland(BlockPos pos) {
        return reservations().tryReserve(pos, getUuid(), "FARMLAND");
    }

    public void releaseFarmland(BlockPos pos) {
        reservations().release(pos, getUuid(), "FARMLAND");
    }

    // ===== Ruộng =====

    public boolean isNearFarmChest(BlockPos pos) {
        BlockPos chest = getChestPos();
        return chest != null && Math.abs(pos.getX() - chest.getX()) <= FARM_RADIUS
                && Math.abs(pos.getZ() - chest.getZ()) <= FARM_RADIUS
                && Math.abs(pos.getY() - chest.getY()) <= 2;
    }

    public boolean isWorkedByAnother(BlockPos plot) {
        return !getWorld().getEntitiesByClass(FarmerNpcEntity.class, new Box(plot).expand(3),
                other -> other != this && plot.equals(other.activeFarmPlot)).isEmpty();
    }

    /** Quét ruộng quanh rương: ưu tiên cây chín gần nhất, sau đó đất trống gần nhất. */
    public void scanFarmland() {
        World world = getWorld();
        farmlandSearchCooldown = FARMLAND_SEARCH_COOLDOWN;
        BlockPos center = findWorkChest();
        if (center == null) {
            cachedMatureCrop = null;
            cachedEmptyFarmland = null;
            return;
        }
        BlockPos nearestMatureCrop = null;
        double nearestCropDist = Double.MAX_VALUE;
        BlockPos nearestEmptyFarmland = null;
        double nearestFarmlandDist = Double.MAX_VALUE;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        boolean skippedAny = false;
        long now = world.getTime();
        skippedPlots.values().removeIf(until -> until <= now);

        for (int dx = -FARM_RADIUS; dx <= FARM_RADIUS; dx++) {
            for (int dz = -FARM_RADIUS; dz <= FARM_RADIUS; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    mutable.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!world.isChunkLoaded(mutable)) continue;
                    if (!(world.getBlockState(mutable).getBlock() instanceof FarmlandBlock)) continue;

                    BlockPos farmlandPos = mutable.toImmutable();
                    BlockPos cropPos = farmlandPos.up();
                    BlockState cropState = world.getBlockState(cropPos);
                    double dist = squaredDistanceTo(cropPos.getX() + 0.5, cropPos.getY() + 0.5, cropPos.getZ() + 0.5);
                    if (isPlotSkipped(farmlandPos)) {
                        skippedAny = true;
                        continue; // vừa thử mà không có đường tới → để ô khác lên trước
                    }
                    if (cropState.getBlock() instanceof CropBlock crop && crop.isMature(cropState)) {
                        if (isWorkedByAnother(farmlandPos)) continue;
                        if (dist < nearestCropDist) {
                            nearestCropDist = dist;
                            nearestMatureCrop = cropPos;
                        }
                    } else if (cropState.isAir()) {
                        if (isWorkedByAnother(farmlandPos)) continue;
                        if (dist < nearestFarmlandDist) {
                            nearestFarmlandDist = dist;
                            nearestEmptyFarmland = farmlandPos;
                        }
                    }
                }
            }
        }
        cachedMatureCrop = nearestMatureCrop;
        cachedEmptyFarmland = nearestEmptyFarmland;
        if (skippedAny && nearestMatureCrop == null && nearestEmptyFarmland == null) {
            display.report(this, "Không tìm được đường vào ruộng (rào kín?)");
        }
    }

    // ===== Ô ruộng không tới được =====

    /** Ô đất cày vừa thử mà không có đường tới → bỏ qua một lúc, tránh đứng loanh quanh sát rào. */
    private final java.util.Map<BlockPos, Long> skippedPlots = new java.util.HashMap<>();
    private static final int PLOT_SKIP_TICKS = 600; // 30 giây

    public void skipPlot(BlockPos farmland) {
        skippedPlots.put(farmland.toImmutable(), getWorld().getTime() + PLOT_SKIP_TICKS);
        if (farmland.up().equals(cachedMatureCrop)) cachedMatureCrop = null;
        if (farmland.equals(cachedEmptyFarmland)) cachedEmptyFarmland = null;
    }

    public boolean isPlotSkipped(BlockPos farmland) {
        Long until = skippedPlots.get(farmland);
        return until != null && until > getWorld().getTime();
    }

    // ===== Hạt giống =====

    /** Rương nông dân còn hạt giống không. */
    public boolean hasSeeds() {
        Inventory chest = findNearestChest();
        if (chest == null) return false;
        for (int i = 0; i < chest.size(); i++) {
            if (isSeedItem(chest.getStack(i).getItem())) return true;
        }
        return false;
    }

    /** Lấy một phần hạt giống từ rương, chia đều cho các nông dân dùng chung rương. */
    public boolean takeSeedBatch(BlockPos chestPos) {
        if (!plantingSeeds.isEmpty() || squaredDistanceTo(Vec3d.ofCenter(chestPos)) > 6.25) return false;
        var chest = getSeedChest(chestPos);
        if (chest == null) return false;
        int totalSeeds = 0;
        for (int i = 0; i < chest.size(); i++) {
            ItemStack stack = chest.getStack(i);
            if (!stack.isEmpty() && isSeedItem(stack.getItem())) totalSeeds += stack.getCount();
        }
        if (totalSeeds == 0) return false;

        int farmers = 1;
        boolean unknownAssignment = false;
        var nearby = new Box(chestPos).expand(FIND_CHEST_DISTANCE, 4, FIND_CHEST_DISTANCE);
        for (FarmerNpcEntity other : getWorld().getEntitiesByClass(FarmerNpcEntity.class, nearby,
                farmer -> farmer != this && farmer.isAlive())) {
            BlockPos otherChest = other.getChestPos();
            if (chestPos.equals(otherChest)) farmers++;
            else if (otherChest == null) unknownAssignment = true;
        }

        int remaining = unknownAssignment
                ? (totalSeeds > 8 ? totalSeeds / 2 : totalSeeds)
                : Math.max(1, totalSeeds / farmers);
        for (int i = 0; i < chest.size() && remaining > 0; i++) {
            ItemStack stack = chest.getStack(i);
            if (!stack.isEmpty() && isSeedItem(stack.getItem())) {
                ItemStack taken = chest.removeStack(i, Math.min(remaining, stack.getCount()));
                plantingSeeds.add(taken);
                remaining -= taken.getCount();
            }
        }
        if (plantingSeeds.isEmpty()) return false;
        seedChestPos = chestPos;
        returningSeeds = false;
        return true;
    }

    public FarmerChestBlockEntity getSeedChest(BlockPos pos) {
        if (pos == null || !getWorld().isChunkLoaded(pos)) return null;
        return getWorld().getBlockEntity(pos) instanceof FarmerChestBlockEntity chest ? chest : null;
    }

    public void returnSeedBatch(BlockPos pos) {
        var chest = getSeedChest(pos);
        if (chest == null || squaredDistanceTo(Vec3d.ofCenter(pos)) > 6.25) return;
        for (ItemStack seed : plantingSeeds) {
            for (int slot = 0; slot < chest.size() && !seed.isEmpty(); slot++) {
                ItemStack current = chest.getStack(slot);
                if (current.isEmpty()) {
                    chest.setStack(slot, seed.copy());
                    seed.setCount(0);
                } else if (ItemStack.canCombine(current, seed)) {
                    int moved = Math.min(seed.getCount(), current.getMaxCount() - current.getCount());
                    if (moved <= 0) continue;
                    current.increment(moved);
                    seed.decrement(moved);
                    chest.markDirty();
                }
            }
        }
        plantingSeeds.removeIf(ItemStack::isEmpty);
        if (plantingSeeds.isEmpty()) {
            seedChestPos = null;
            returningSeeds = false;
        }
    }

    private boolean isSeedItem(Item item) {
        return item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS || item == Items.CARROT
                || item == Items.POTATO || item == Items.PUMPKIN_SEEDS || item == Items.MELON_SEEDS;
    }

    /** Trồng hạt giống lên ô đất cày. */
    public boolean plantSeedAt(BlockPos farmlandPos, ItemStack seed) {
        World world = getWorld();
        BlockPos cropPos = farmlandPos.up();
        if (!world.getBlockState(farmlandPos).isOf(Blocks.FARMLAND)
                || !world.getBlockState(cropPos).isAir()) return false;

        Item item = seed.getItem();
        Block crop;
        if (item == Items.WHEAT_SEEDS) crop = Blocks.WHEAT;
        else if (item == Items.BEETROOT_SEEDS) crop = Blocks.BEETROOTS;
        else if (item == Items.PUMPKIN_SEEDS) crop = Blocks.PUMPKIN_STEM;
        else if (item == Items.MELON_SEEDS) crop = Blocks.MELON_STEM;
        else if (item == Items.CARROT) crop = Blocks.CARROTS;
        else if (item == Items.POTATO) crop = Blocks.POTATOES;
        else return false;
        return world.setBlockState(cropPos, crop.getDefaultState());
    }

    /** Có việc để làm không: còn hạt giống, hoặc rương còn chỗ chứa nông sản. */
    public boolean hasFarmItems() {
        if (hasSeeds()) return true;
        Inventory chest = findNearestChest();
        if (chest == null) return false;
        for (int i = 0; i < chest.size(); i++) {
            if (chest.getStack(i).isEmpty()) return true;
        }
        return false;
    }
}
