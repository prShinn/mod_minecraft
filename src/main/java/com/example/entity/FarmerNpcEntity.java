package com.example.entity;

import com.example.ai.FindAndSleepGoal;
import com.example.ai.GlobalReservationSystem;
import com.example.ai.WanderForBedGoal;
import com.example.ai.farmer.*;
import com.example.ai.foodChest.FindAndEatFoodGoal;
import com.example.entity.base.NpcDisplayComponent;
import com.example.entity.base.NpcEquipmentComponent;
import com.example.entity.base.NpcSleepingComponent;
import com.example.registry.ModItems;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public class FarmerNpcEntity extends PathAwareEntity {
    public final NpcDisplayComponent display = new NpcDisplayComponent();
    private final NpcEquipmentComponent equip = new NpcEquipmentComponent();
    public final com.example.entity.base.NpcSleepingComponent sleeping = new com.example.entity.base.NpcSleepingComponent();
    private final GlobalReservationSystem reservationSystem = GlobalReservationSystem.getInstance(getWorld());
    private BlockPos currentFarmPos;
    private UUID ownerUUID;

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }
    private UUID followPlayerUUID;
    public final SimpleInventory foodInventory = new SimpleInventory(9);
    private final SimpleInventory supplies = new SimpleInventory(9);
    public final FarmerMemory memory = new FarmerMemory();
    private static final int FIND_CHEST_DISTANCE = 16; // 1 second


    // Thay vì static Set

    // ===== COOLDOWN VARIABLES =====
    private int farmlandSearchCooldown = getRandom().nextInt(40);
    private static final int FARMLAND_SEARCH_COOLDOWN = 40; // Spread farmland scans over two seconds
    private int chestSearchCooldown = 60;
    private static final int CHEST_SEARCH_COOLDOWN = 100;

    public BlockPos cachedMatureCrop = null;
    public BlockPos cachedEmptyFarmland = null;

    // ===== HELPER METHODS FOR RESERVATION MAPS =====
    public boolean reserveCrop(BlockPos pos) {
        return reservationSystem.tryReserve(pos, this.getUuid(), "CROP");
    }

    public void releaseCrop(BlockPos pos) {
        reservationSystem.release(pos, this.getUuid(), "CROP");
    }

    public boolean reserveFarmland(BlockPos pos) {
        return reservationSystem.tryReserve(pos, this.getUuid(), "FARMLAND");
    }

    public void releaseFarmland(BlockPos pos) {
        reservationSystem.release(pos, this.getUuid(), "FARMLAND");
    }

    public boolean reserveChest(BlockPos pos) {
        return reservationSystem.tryReserve(pos, this.getUuid(), "WORK_CHEST");
    }

    public void releaseChest(BlockPos pos) {
        reservationSystem.release(pos, this.getUuid(), "WORK_CHEST");
    }

    public boolean isChestReserved(BlockPos pos) {
        return reservationSystem.isReservedByOthers(pos, this.getUuid(), "WORK_CHEST");
    }

    private void cleanupAllReservations() {
        reservationSystem.releaseAll(this.getUuid());
    }

    public FarmerNpcEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    // ===== AI GOALS =====
    @Override
    protected void initGoals() {
        super.initGoals();
        this.goalSelector.clear(goal -> true);
        this.targetSelector.clear(goal -> true);

        this.goalSelector.add(0, new SwimGoal(this)); // ko chet duoi
        this.goalSelector.add(1, new EscapeDangerGoal(this, 1.4)); // chay khi bi dame
        this.goalSelector.add(2, new com.example.ai.FindAndSleepGoal(this, () -> sleeping));
        this.goalSelector.add(3, new FindAndEatFoodGoal(this, () -> supplies, 24, () -> display.shouldEat(), amount -> display.feed(amount)));
//        this.goalSelector.add(3, new WanderForBedGoal(this, 1.0, sleeping));
        this.goalSelector.add(4, new HarvestCropGoal(this)); // thu hoach
        this.goalSelector.add(5, new DepositToChestGoal(this)); // cât đô
        this.goalSelector.add(6, new PlantSeedGoal(this)); // trồng cây
        this.goalSelector.add(7, new ReturnToFarmGoal(this)); // quay lại farm
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F)); // nhin player
        this.goalSelector.add(9, new WanderAroundFarGoal(this, 1.0D)); // đi lang thang xa
        this.goalSelector.add(9, new LookAroundGoal(this));//nhin xunh quanh
        ItemStack mainHand = this.getEquippedStack(EquipmentSlot.MAINHAND);
        if (mainHand.isEmpty()) {
            this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
            // Không drop khi chết
            this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
    }


    @Override
    public void tick() {
        super.tick();
        if (this.getWorld() == null || this.getWorld().isClient) return;
        sleeping.setSearchCenter(memory.lastChestPos);
        sleeping.tick(this);
        display.tick(this, supplies, goalSelector);
        if (farmlandSearchCooldown > 0) {
            farmlandSearchCooldown--;
        } else {
            scanFarmland();
        }
        if (chestSearchCooldown > 0) {
            chestSearchCooldown--;
        }

        // Update idle ticks
        if (this.getNavigation().isIdle()) {
            memory.tickIdle();
        } else {
            memory.resetIdle();
        }

        // Trong tick() - cleanup sớm nếu HP thấp
        if (this.getHealth() <= 2.0F) {
            cleanupAllReservations();
        }
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        // Nếu player đang cố gắng lấy cuốc từ NPC
        ItemStack held = player.getStackInHand(hand);
        // Nếu đang cầm armor hoặc food → ignore
        if (player.isSneaking() || held.getItem() instanceof ArmorItem || held.isFood()) {

        } else {
            return ActionResult.FAIL;
        }
        ActionResult handled = equip.interactMob(this, player, ownerUUID, hand, supplies, ModItems.FARMER_TOKEN);
        if (!handled.equals(ActionResult.FAIL)) {
            return handled;
        }
        return super.interactMob(player, hand);
    }

    // ===== NBT SAVE/LOAD =====
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        display.writeNbt(nbt);
        nbt.put("FoodInventory", foodInventory.toNbtList());
        nbt.put("Supplies", supplies.toNbtList());
        net.minecraft.nbt.NbtList seeds = new net.minecraft.nbt.NbtList();
        for (ItemStack stack : plantingSeeds) seeds.add(stack.writeNbt(new NbtCompound()));
        nbt.put("PlantingSeeds", seeds);
        if (seedChestPos != null) nbt.putLong("SeedChestPos", seedChestPos.asLong());
        nbt.putBoolean("ReturningSeeds", returningSeeds);
        if (ownerUUID != null) {
            nbt.putUuid("OwnerUUID", ownerUUID);
        }
        if (followPlayerUUID != null) {
            nbt.putUuid("FollowPlayer", followPlayerUUID);
        }
        if (currentFarmPos != null) {
            nbt.putLong("CurrentFarmPos", currentFarmPos.asLong());
        }
        nbt.putBoolean("IsFarmerNpc", true);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        display.readNbt(nbt);
        if (nbt.contains("Supplies")) supplies.readNbtList(nbt.getList("Supplies", 10));
        plantingSeeds.clear();
        for (var entry : nbt.getList("PlantingSeeds", 10)) {
            ItemStack stack = ItemStack.fromNbt((NbtCompound) entry);
            if (!stack.isEmpty() && isSeedItem(stack.getItem())) plantingSeeds.add(stack);
        }
        if (nbt.contains("PlantingSeed")) {
            ItemStack oldSeed = ItemStack.fromNbt(nbt.getCompound("PlantingSeed"));
            if (!oldSeed.isEmpty() && isSeedItem(oldSeed.getItem())) plantingSeeds.add(oldSeed);
        }
        seedChestPos = nbt.contains("SeedChestPos") ? BlockPos.fromLong(nbt.getLong("SeedChestPos")) : null;
        returningSeeds = nbt.getBoolean("ReturningSeeds");
        if (nbt.contains("FoodInventory")) foodInventory.readNbtList(nbt.getList("FoodInventory", 10));
        if (nbt.containsUuid("OwnerUUID")) {
            ownerUUID = nbt.getUuid("OwnerUUID");
        }
        if (nbt.containsUuid("FollowPlayer")) {
            followPlayerUUID = nbt.getUuid("FollowPlayer");
        }
        if (nbt.contains("CurrentFarmPos")) {
            currentFarmPos = BlockPos.fromLong(nbt.getLong("CurrentFarmPos"));
        }
        ItemStack mainHand = this.getEquippedStack(EquipmentSlot.MAINHAND);
        if (mainHand.isEmpty()) {
            this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));

            // Không drop khi chết
            this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }

    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean result = super.damage(source, amount);
        if (!this.isAlive()) {
            cleanupAllReservations();
        }
        return result;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!getWorld().isClient) {
            sleeping.cancelMovingToBed(this);
            sleeping.wakeUp(this);
        }
        cleanupAllReservations();
        super.remove(reason);
    }


    /**
     * Tìm farm (farmland) gần nhất trong bán kính 24 block
     * Ưu tiên: cây chín > đất trống > farmland bất kỳ
     */
//    public BlockPos findNearestFarmland() {
//        BlockPos center = getBlockPos();
//        World world = getWorld();
//        BlockPos matureCrop = null;
//        BlockPos emptyFarmland = null;
//
//        for (BlockPos pos : BlockPos.iterate(center.add(-24, -2, -24), center.add(24, 2, 24))) {
//            BlockPos farmlandPos = pos;
//            BlockPos cropPos = pos.up();
//
//            // Check xem vị trí này có farmland không
//            if (!(world.getBlockState(farmlandPos).getBlock() instanceof FarmlandBlock)) {
//                continue;
//            }
//
//            // Ưu tiên cây chín
//            if (world.getBlockState(cropPos).getBlock() instanceof CropBlock crop) {
//                if (crop.isMature(world.getBlockState(cropPos))) {
//                    if (matureCrop == null ||
//                            squaredDistanceTo(Vec3d.ofCenter(cropPos)) < squaredDistanceTo(Vec3d.ofCenter(matureCrop))) {
//                        matureCrop = cropPos;
//                    }
//                }
//            }
//            // Thứ 2 là đất trống
//            else if (world.getBlockState(cropPos).isAir() && emptyFarmland == null) {
//                emptyFarmland = farmlandPos;
//            }
//        }
//
//        if (matureCrop != null) return matureCrop;
//        if (emptyFarmland != null) return emptyFarmland;
//        return null;
//    }
    public BlockPos activeFarmPlot;
    public final java.util.List<ItemStack> plantingSeeds = new java.util.ArrayList<>();
    public BlockPos seedChestPos;
    public boolean returningSeeds;

    public boolean isNearFarmChest(BlockPos pos) {
        BlockPos chest = memory.lastChestPos;
        return chest != null && Math.abs(pos.getX() - chest.getX()) <= 12
                && Math.abs(pos.getZ() - chest.getZ()) <= 12
                && Math.abs(pos.getY() - chest.getY()) <= 2;
    }

    public boolean isWorkedByAnother(BlockPos plot) {
        return !getWorld().getEntitiesByClass(FarmerNpcEntity.class,
                new net.minecraft.util.math.Box(plot).expand(3),
                other -> other != this && plot.equals(other.activeFarmPlot)).isEmpty();
    }

    public void scanFarmland() {
        World world = getWorld();
        if (world == null) return;

        farmlandSearchCooldown = FARMLAND_SEARCH_COOLDOWN;
        if (findNearestChest() == null || memory.lastChestPos == null) {
            cachedMatureCrop = null;
            cachedEmptyFarmland = null;
            return;
        }
        BlockPos center = memory.lastChestPos;
        BlockPos nearestMatureCrop = null;
        double nearestCropDist = Double.MAX_VALUE;
        BlockPos nearestEmptyFarmland = null;
        double nearestFarmlandDist = Double.MAX_VALUE;

        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    mutable.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!world.isChunkLoaded(mutable)) continue;

                    BlockState state = world.getBlockState(mutable);
                    if (!(state.getBlock() instanceof FarmlandBlock)) continue;

                    BlockPos farmlandPos = mutable.toImmutable();
                    BlockPos cropPos = farmlandPos.up();
                    BlockState cropState = world.getBlockState(cropPos);

                    double dist = this.squaredDistanceTo(cropPos.getX() + 0.5, cropPos.getY() + 0.5, cropPos.getZ() + 0.5);
                    // 🌾 Ưu tiên cây chín
                    if (cropState.getBlock() instanceof CropBlock crop && crop.isMature(cropState)) {
                        if (isWorkedByAnother(cropPos.down())) continue;
                        if (dist < nearestCropDist) {
                            nearestCropDist = dist;
                            nearestMatureCrop = cropPos.toImmutable();
                        }
                    }
                    // 🌱 Farmland trống
                    else if (cropState.isAir()) {
                        if (isWorkedByAnother(farmlandPos)) continue;
                        if (dist < nearestFarmlandDist) {
                            nearestFarmlandDist = dist;
                            nearestEmptyFarmland = farmlandPos.toImmutable();
                        }
                    }
                }
            }
        }
        farmlandSearchCooldown = FARMLAND_SEARCH_COOLDOWN;
        cachedMatureCrop = nearestMatureCrop;
        cachedEmptyFarmland = nearestEmptyFarmland;
    }

    /**
     * Check xem có hạt giống trong inventory không
     */
    public boolean hasSeeds() {
        Inventory chest = findNearestChest();
        if (chest == null) return false;

        for (int i = 0; i < chest.size(); i++) {
            ItemStack stack = chest.getStack(i);
            if (isSeedItem(stack.getItem())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Lấy hạt giống từ inventory
     */
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
        var nearby = new net.minecraft.util.math.Box(chestPos).expand(FIND_CHEST_DISTANCE, 4, FIND_CHEST_DISTANCE);
        for (FarmerNpcEntity other : getWorld().getEntitiesByClass(FarmerNpcEntity.class, nearby,
                farmer -> farmer != this && farmer.isAlive())) {
            BlockPos otherChest = other.memory.lastChestPos;
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

    public com.example.block.FarmerChestBlockEntity getSeedChest(BlockPos pos) {
        if (pos == null || !getWorld().isChunkLoaded(pos)) return null;
        return getWorld().getBlockEntity(pos) instanceof com.example.block.FarmerChestBlockEntity chest ? chest : null;
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

    /**
     * Check xem item có phải hạt giống không
     */
    private boolean isSeedItem(Item item) {
        return item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS || item == Items.CARROT || item == Items.POTATO || item == Items.PUMPKIN_SEEDS || item == Items.MELON_SEEDS;
    }

    /**
     * Trồng hạt giống tại vị trí farmland
     */
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

    /**
     * Kiểm tra xem NPC có tài nguyên để làm việc (hạt giống hoặc rương)
     */
    public boolean hasFarmItems() {
        // Check inventory
        if (hasSeeds()) return true;

        // Check rương gần
        Inventory chest = findNearestChest();
        return chest != null && hasSpaceInInventory(chest);
    }

    /**
     * Tìm Rương Nông dân (FarmerChestBlockEntity) gần nhất trong bán kính FIND_CHEST_DISTANCE.
     * Chỉ nhận ra rương đúng loại (craft bằng Chest + Cuốc full độ bền).
     * Không cần khóa (reservation) vì mỗi loại rương chỉ dành cho 1 loại NPC.
     */
    public Inventory findNearestChest() {
        World world = getWorld();
        if (world == null) return null;

        // Dùng cache nếu còn hiệu lực
        if (memory.lastChestPos != null && chestSearchCooldown > 0) {
            BlockPos cached = memory.lastChestPos;
            if (world.isChunkLoaded(cached)) {
                BlockEntity be = world.getBlockEntity(cached);
                if (be instanceof com.example.block.FarmerChestBlockEntity inv) {
                    return inv;
                }
            }
            memory.lastChestPos = null;
        }

        if (chestSearchCooldown > 0) return null;

        BlockPos center = getBlockPos();
        double closestDist = Double.MAX_VALUE;
        Inventory closestChest = null;
        BlockPos newChestPos = null;

        for (int dx = -FIND_CHEST_DISTANCE; dx <= FIND_CHEST_DISTANCE; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -FIND_CHEST_DISTANCE; dz <= FIND_CHEST_DISTANCE; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (!world.isChunkLoaded(pos)) continue;

                    BlockEntity be = world.getBlockEntity(pos);
                    if (be instanceof com.example.block.FarmerChestBlockEntity farmerChest) {
                        double dist = this.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (dist < closestDist) {
                            closestDist = dist;
                            closestChest = farmerChest;
                            newChestPos = pos;
                        }
                    }
                }
            }
        }
        chestSearchCooldown = CHEST_SEARCH_COOLDOWN;

        if (newChestPos != null) {
            memory.lastChestPos = newChestPos;
        }

        return closestChest;
    }


    private boolean hasSpaceInInventory(Inventory inv) {
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isEmpty()) return true;
        }
        return false;
    }

    public SimpleInventory getInventory() {
        return foodInventory;
    }

    public boolean isInventoryFull() {
        for (int i = 0; i < foodInventory.size(); i++) {
            if (foodInventory.getStack(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        if (!getWorld().isClient) {
            for (ItemStack seed : plantingSeeds) dropStack(seed);
            plantingSeeds.clear();
        }
        cleanupAllReservations();
        super.onDeath(damageSource);
    }
}
