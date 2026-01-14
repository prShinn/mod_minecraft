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
import net.minecraft.registry.tag.BlockTags;
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
    public final NpcSleepingComponent sleeping = new NpcSleepingComponent()
            .withSearchRadius(16)
            .withCooldown(120)
            .withWanderDuration(100);
    private UUID ownerUUID;
    public final SimpleInventory inventory = new SimpleInventory(9);
    public final LumberjackMemory memory = new LumberjackMemory();
    public final static int FIND_CHEST_DISTANCE = 20;
    public final static int FIND_TREE_DISTANCE = 7;
    private final GlobalReservationSystem reservationSystem = GlobalReservationSystem.getInstance();
    private int treeSearchCooldown = 0;
    private int chestSearchCooldown = 0;
    private static final int TREE_SEARCH_COOLDOWN = 100; // 5 second (20 ticks)
    private static final int CHEST_SEARCH_COOLDOWN = 100; // 5 second (20 ticks)


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
    protected void initGoals() {
        super.initGoals();
        this.goalSelector.clear(goal -> true);
        this.targetSelector.clear(goal -> true);

        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new EscapeDangerGoal(this, 1.4));
        this.goalSelector.add(2, new FindAndSleepGoal(this, sleeping));
        this.goalSelector.add(2, new FindAndEatFoodGoal(
                this,
                inventory,  // NPC inventory
                24              // search radius
        ) {
            @Override
            public boolean canStart() {
                return display.findFood() && !sleeping.isSleeping() && super.canStart();
            }
        });

        this.goalSelector.add(3, new WanderForBedGoal(this, 1.0, sleeping));
        this.goalSelector.add(4, new ChopTreeGoal(this) {
            @Override
            public boolean canStart() {
                return !sleeping.isSleeping() && super.canStart();
            }
        }); // Chặt cây
        this.goalSelector.add(5, new DepositWoodToChestGoal(this) {
            @Override
            public boolean canStart() {
                return !sleeping.isSleeping() && super.canStart();
            }
        }); // Cất vao chest
        this.goalSelector.add(6, new PlantSaplingGoal(this) {
            @Override
            public boolean canStart() {
                return !sleeping.isSleeping() && super.canStart();
            }
        }); // Trồng mầm cây
        this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.add(8, new WanderNearChestGoal(this) {
            @Override
            public boolean canStart() {
                return !sleeping.isSleeping() && super.canStart();
            }
        }); // Lang thang và tìm chest
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.goalSelector.add(9, new WanderAroundFarGoal(this, 1.0D) {
            @Override
            public boolean canStart() {
                return !sleeping.isSleeping() && super.canStart();
            }
        }); // ✅ GIỮ - Wander NGÀY + ĐÊM (khi idle)
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
        display.tick(this, inventory);
        sleeping.tick(this);
        if (!sleeping.isSleeping()) {
            memory.tickIdle();
        }

        if (chestSearchCooldown > 0) {
            chestSearchCooldown--;
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

        ActionResult handled = equip.interactMob(this, player, ownerUUID, hand, inventory, ModItems.LUMBERJACK_TOKEN);
        if (!handled.equals(ActionResult.FAIL)) {
            return handled;
        }
        return super.interactMob(player, hand);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (ownerUUID != null) {
            nbt.putUuid("OwnerUUID", ownerUUID);
        }
        nbt.putBoolean("IsLumberjackNpc", true);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
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
        if (sleeping.isSleeping()) {
            sleeping.wakeUp(this);
        }
        return super.damage(source, amount);
    }

    @Override
    public void remove(RemovalReason reason) {
        // ✅ Fixed: Added proper cleanup
        sleeping.wakeUp(this);
        cleanupAllReservations(); // 🔥 CLEANUP ALL (bed + trees + chests)
        super.remove(reason);
    }

    public Inventory findNearestChest() {
        if (chestSearchCooldown > 0) {
            memory.lastChestPos = null;
            return null;
        }
        chestSearchCooldown--;
        BlockPos center = getBlockPos();
        World world = getWorld();
        double closestDist = Double.MAX_VALUE;
        Inventory closestChest = null;

        for (int dx = -FIND_CHEST_DISTANCE; dx <= FIND_CHEST_DISTANCE; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -FIND_CHEST_DISTANCE; dz <= FIND_CHEST_DISTANCE; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (!world.isChunkLoaded(pos)) continue;

                    BlockEntity be = world.getBlockEntity(pos);
                    if (be instanceof Inventory inv) {
                        if (reservationSystem.isReservedByOthers(pos, this.getUuid(), "WORK_CHEST")) {
                            continue;
                        }
                        double dist = this.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (dist < closestDist) {
                            closestDist = dist;
                            closestChest = inv;
                            memory.lastChestPos = pos;
                        }
                    }
                }
            }
        }
        return closestChest;
    }

    /**
     * Tìm cây gần nhất trong bán kính 7 blocks từ chest
     * Chỉ tìm log có độ cao >= chest
     */
    public BlockPos findNearestTree() {
        if (memory.lastChestPos == null) {
            findNearestChest(); // Tìm chest trước
        }
        treeSearchCooldown--;
        if (treeSearchCooldown > 0) {
            return null;
        }

        chestSearchCooldown = CHEST_SEARCH_COOLDOWN;
        if (memory.lastChestPos == null) return null;

        BlockPos chestPos = memory.lastChestPos;
        World world = getWorld();
        double closestDist = Double.MAX_VALUE;
        BlockPos closestTree = null;

        for (int dx = -FIND_TREE_DISTANCE; dx <= FIND_TREE_DISTANCE; dx++) {
            for (int dz = -FIND_TREE_DISTANCE; dz <= FIND_TREE_DISTANCE; dz++) {
                for (int dy = 0; dy <= 7; dy++) {
                    BlockPos pos = chestPos.add(dx, dy, dz);
                    if (!world.isChunkLoaded(pos)) continue;

                    BlockState state = world.getBlockState(pos);

                    // Kiểm tra xem có phải log hoặc leaves không
                    if (isTreeBlock(state)) {
                        if (reservationSystem.isReservedByOthers(pos, this.getUuid(), "TREE")) {
                            continue;
                        }

                        double dist = this.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (dist < closestDist) {
                            closestDist = dist;
                            closestTree = pos;
                        }
                    }
                }
            }
        }
        treeSearchCooldown = TREE_SEARCH_COOLDOWN;
        return closestTree;
    }

    /**
     * Kiểm tra xem block có phải là log hoặc leaves
     */
    private boolean isTreeBlock(BlockState state) {
        Block block = state.getBlock();
        return state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES) || (block instanceof PillarBlock && (
                block == Blocks.OAK_LOG ||
                        block == Blocks.SPRUCE_LOG ||
                        block == Blocks.BIRCH_LOG ||
                        block == Blocks.JUNGLE_LOG ||
                        block == Blocks.ACACIA_LOG ||
                        block == Blocks.DARK_OAK_LOG ||
                        block == Blocks.MANGROVE_LOG ||
                        block == Blocks.CHERRY_LOG
        ) || block instanceof LeavesBlock);
    }

    public SimpleInventory getInventory() {
        return inventory;
    }

}
