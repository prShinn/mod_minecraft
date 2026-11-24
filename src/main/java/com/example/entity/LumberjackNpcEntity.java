package com.example.entity;

import com.example.ai.FindAndSleepGoal;
import com.example.ai.GlobalReservationSystem;
import com.example.ai.WanderForBedGoal;
import com.example.ai.foodChest.FindAndEatFoodGoal;
import com.example.ai.lumberjack.*;
import com.example.entity.base.NpcDisplayComponent;
import com.example.entity.base.NpcEquipmentComponent;
import com.example.entity.base.NpcSleepingComponent;
import com.example.registry.ModItems;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class LumberjackNpcEntity extends PathAwareEntity {
    public final NpcDisplayComponent display = new NpcDisplayComponent();
    private final NpcEquipmentComponent equip = new NpcEquipmentComponent();
    public final com.example.entity.base.NpcSleepingComponent sleeping = new com.example.entity.base.NpcSleepingComponent();
    private UUID ownerUUID;

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }
    public final SimpleInventory inventory = new SimpleInventory(9);
    public final SimpleInventory plantingSaplings = new SimpleInventory(27);
    public BlockPos saplingChestPos;
    public boolean returningSaplings;
    private final SimpleInventory supplies = new SimpleInventory(9);
    public final LumberjackMemory memory = new LumberjackMemory();
    public final static int FIND_CHEST_DISTANCE = 16;
    public final static int FIND_TREE_DISTANCE = 8;
    private final GlobalReservationSystem reservationSystem = GlobalReservationSystem.getInstance(getWorld());
    private int treeSearchCooldown = 0;
    private static final int TREE_SEARCH_COOLDOWN = 20; // 1 second (20 ticks)

    private int chestSearchCooldown = 60; // Thêm cooldown giống Miner
    private static final int CHEST_SEARCH_COOLDOWN = 100;
    private static final Set<BlockPos> RESERVED_CHESTS = new HashSet<>();

    public boolean reserveTree(BlockPos pos) {
        return reservationSystem.tryReserve(pos, this.getUuid(), "TREE");
    }

    public void releaseTree(BlockPos pos) {
        reservationSystem.release(pos, this.getUuid(), "TREE");
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

    public LumberjackNpcEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        this.goalSelector.clear(goal -> true);
        this.targetSelector.clear(goal -> true);

        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new EscapeDangerGoal(this, 1.4));
        this.goalSelector.add(2, new com.example.ai.FindAndSleepGoal(this, () -> sleeping));
        this.goalSelector.add(3, new FindAndEatFoodGoal(this, () -> supplies, 24, () -> display.shouldEat(), amount -> display.feed(amount)));
//        this.goalSelector.add(3, new WanderForBedGoal(this, 1.0));
        this.goalSelector.add(6, new ChopTreeGoal(this)); // Chặt cây
        this.goalSelector.add(5, new DepositWoodToChestGoal(this)); // Cất vao chest
        this.goalSelector.add(4, new PlantSaplingGoal(this)); // Trồng mầm cây
        this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.add(8, new WanderNearChestGoal(this)); // Lang thang và tìm chest
        this.goalSelector.add(8, new LookAroundGoal(this));
//        this.goalSelector.add(9, new WanderAroundFarGoal(this, 1.0D)); // ✅ GIỮ - Wander NGÀY + ĐÊM (khi idle)
        // Trang bị rìu nếu chưa có
        ItemStack mainHand = this.getEquippedStack(EquipmentSlot.MAINHAND);
        if (mainHand.isEmpty()) {
            this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
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
        if (this.getNavigation().isIdle()) {
            memory.tickIdle();
        } else {
            memory.resetIdle();
        }
        if (treeSearchCooldown > 0) {
            treeSearchCooldown--;
        }
        if (chestSearchCooldown > 0) {
            chestSearchCooldown--;
        }
        // Trong tick() - cleanup sớm nếu HP thấp
        if (this.getHealth() <= 2.0F) {
            cleanupAllReservations();
        }
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);

        if (player.isSneaking() || held.getItem() instanceof ArmorItem || held.isFood()) {
            // Được phép tương tác
        } else {
            return ActionResult.FAIL;
        }

        ActionResult handled = equip.interactMob(this, player, ownerUUID, hand, supplies, ModItems.LUMBERJACK_TOKEN);
        if (!handled.equals(ActionResult.FAIL)) {
            return handled;
        }
        return super.interactMob(player, hand);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        display.writeNbt(nbt);
        nbt.put("FoodInventory", inventory.toNbtList());
        nbt.put("PlantingSaplings", plantingSaplings.toNbtList());
        if (saplingChestPos != null) nbt.putLong("SaplingChestPos", saplingChestPos.asLong());
        nbt.putBoolean("ReturningSaplings", returningSaplings);
        nbt.put("Supplies", supplies.toNbtList());
        nbt.putBoolean("NeedsWoodDelivery", needsWoodDelivery);
        if (ownerUUID != null) {
            nbt.putUuid("OwnerUUID", ownerUUID);
        }
        nbt.putBoolean("IsLumberjackNpc", true);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        display.readNbt(nbt);
        needsWoodDelivery = nbt.getBoolean("NeedsWoodDelivery");
        if (nbt.contains("Supplies")) supplies.readNbtList(nbt.getList("Supplies", 10));
        if (nbt.contains("FoodInventory")) inventory.readNbtList(nbt.getList("FoodInventory", 10));
        if (nbt.contains("PlantingSaplings")) plantingSaplings.readNbtList(nbt.getList("PlantingSaplings", 10));
        saplingChestPos = nbt.contains("SaplingChestPos") ? BlockPos.fromLong(nbt.getLong("SaplingChestPos")) : null;
        returningSaplings = nbt.getBoolean("ReturningSaplings");
        if (nbt.containsUuid("OwnerUUID")) {
            ownerUUID = nbt.getUuid("OwnerUUID");
        }

        ItemStack mainHand = this.getEquippedStack(EquipmentSlot.MAINHAND);
        if (mainHand.isEmpty()) {
            this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
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
        // ✅ Fixed: Added proper cleanup
        cleanupAllReservations(); // 🔥 CLEANUP ALL (bed + trees + chests)
        super.remove(reason);
    }

//    public Inventory findNearestChest() {
//        BlockPos center = getBlockPos();
//        World world = getWorld();
//        double closestDist = Double.MAX_VALUE;
//        Inventory closestChest = null;
//
//        for (int dx = -FIND_CHEST_DISTANCE; dx <= FIND_CHEST_DISTANCE; dx++) {
//            for (int dy = -2; dy <= 2; dy++) {
//                for (int dz = -FIND_CHEST_DISTANCE; dz <= FIND_CHEST_DISTANCE; dz++) {
//                    BlockPos pos = center.add(dx, dy, dz);
//                    if (!world.isChunkLoaded(pos)) continue;
//
//                    BlockEntity be = world.getBlockEntity(pos);
//                    if (be instanceof Inventory inv) {
//                        if (reservationSystem.isReservedByOthers(pos, this.getUuid(), "WORK_CHEST")) {
//                            continue;
//                        }
//                        double dist = this.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
//                        if (dist < closestDist) {
//                            closestDist = dist;
//                            closestChest = inv;
//                            memory.lastChestPos = pos;
//                        }
//                    }
//                }
//            }
//        }
//        return closestChest;
//    }
    public Inventory findNearestChest() {
        World world = getWorld();
        if (world == null) return null;

        // Dùng cache nếu còn hiệu lực
        if (memory.lastChestPos != null && chestSearchCooldown > 0) {
            BlockPos cached = memory.lastChestPos;
            if (world.isChunkLoaded(cached)) {
                BlockEntity be = world.getBlockEntity(cached);
                if (be instanceof com.example.block.LumberjackChestBlockEntity inv) {
                    return inv;
                }
            }
            memory.lastChestPos = null;
        }

        // Kiểm tra cooldown
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
                    if (be instanceof com.example.block.LumberjackChestBlockEntity lumberjackChest) {
                        double dist = this.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (dist < closestDist) {
                            closestDist = dist;
                            closestChest = lumberjackChest;
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

    /**
     * Tìm cây gần nhất trong bán kính 7 blocks từ chest
     * Chỉ tìm log có độ cao >= chest
     */
    public BlockPos activeTree;
    public boolean needsWoodDelivery;

    public boolean isWorkedByAnother(BlockPos tree) {
        return !getWorld().getEntitiesByClass(LumberjackNpcEntity.class,
                new net.minecraft.util.math.Box(tree).expand(8, 40, 8),
                other -> other != this && other.activeTree != null
                        && Math.abs(tree.getX() - other.activeTree.getX()) <= 2
                        && Math.abs(tree.getZ() - other.activeTree.getZ()) <= 2
                        && Math.abs(tree.getY() - other.activeTree.getY()) <= 40).isEmpty();
    }

    public BlockPos findNearestTree() {
        if (treeSearchCooldown > 0) {
            return null;
        }
        if (findNearestChest() == null || memory.lastChestPos == null) {
            display.report(this, "Không tìm thấy rương tiều phu");
            return null;
        }

        BlockPos chestPos = memory.lastChestPos;
        World world = getWorld();

        var workChest = world.getBlockEntity(chestPos);
        if (!(workChest instanceof com.example.block.LumberjackChestBlockEntity zone)
                || !zone.allowsCutting(chestPos)) {
            display.report(this, "Chưa bật vùng chặt trên rương tiều phu");
            treeSearchCooldown = TREE_SEARCH_COOLDOWN;
            return null;
        }

        int radius = com.example.block.LumberjackChestBlockEntity.CUT_RADIUS;
        BlockPos found = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -com.example.block.LumberjackChestBlockEntity.CUT_BELOW;
                     dy <= com.example.block.LumberjackChestBlockEntity.CUT_ABOVE; dy++) {
                    BlockPos pos = chestPos.add(dx, dy, dz);
                    if (!zone.allowsCutting(pos) || !isTreeRoot(pos)) continue;

                    if (isWorkedByAnother(pos)) {
                        continue;
                    }

                    double dist = this.squaredDistanceTo(
                            pos.getX() + 0.5,
                            pos.getY() + 0.5,
                            pos.getZ() + 0.5
                    );

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
        if (!log.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) return false;
        BlockState below = world.getBlockState(pos.down());
        return below.isIn(net.minecraft.registry.tag.BlockTags.DIRT)
                || (log.isOf(Blocks.MANGROVE_LOG)
                    && (below.isOf(Blocks.MANGROVE_ROOTS) || below.isOf(Blocks.MUDDY_MANGROVE_ROOTS)
                        || below.isOf(Blocks.MUD)));
    }

    /**
     * Kiểm tra xem block có phải là log hoặc leaves
     */
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

    public SimpleInventory getInventory() {
        return inventory;
    }

    public boolean isInventoryFull() {
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        cleanupAllReservations();
        if (!getWorld().isClient) {
            for (int slot = 0; slot < plantingSaplings.size(); slot++) {
                ItemStack stack = plantingSaplings.removeStack(slot);
                if (!stack.isEmpty()) dropStack(stack);
            }
        }
        super.onDeath(damageSource);
    }
}
