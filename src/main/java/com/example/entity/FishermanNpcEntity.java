package com.example.entity;

import com.example.ai.FindAndSleepGoal;
import com.example.ai.GlobalReservationSystem;
import com.example.ai.WanderForBedGoal;
import com.example.ai.foodChest.FindAndEatFoodGoal;
import com.example.ai.fisherman.FishermanMemory;
import com.example.ai.fisherman.DepositFishToChestGoal;
import com.example.ai.fisherman.FindFishingSpotGoal;
import com.example.ai.fisherman.FishermanFishGoal;
import com.example.ai.fisherman.FishermanWanderGoal;
import com.example.entity.base.NpcDisplayComponent;
import com.example.entity.base.NpcEquipmentComponent;
import com.example.entity.base.NpcSleepingComponent;
import com.example.registry.ModItems;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
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
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;

import java.util.UUID;

public class FishermanNpcEntity extends PathAwareEntity {

    public final NpcDisplayComponent display = new NpcDisplayComponent();
    private final NpcEquipmentComponent equip = new NpcEquipmentComponent();
    public final NpcSleepingComponent sleeping = new NpcSleepingComponent();
    public final FishermanMemory memory = new FishermanMemory();

    private final SimpleInventory foodInventory = new SimpleInventory(36);
    private final SimpleInventory supplies = new SimpleInventory(9);
    private UUID ownerUUID;
    private final GlobalReservationSystem reservationSystem = GlobalReservationSystem.getInstance(getWorld());

    private static final int CHEST_SEARCH_COOLDOWN = 100;
    private int chestSearchCooldown = 0;
    
    private static final int WATER_SEARCH_COOLDOWN = 100;
    private int waterSearchCooldown = 0;
    private final java.util.Map<BlockPos, Long> blockedFishingSpots = new java.util.HashMap<>();

    public void skipFishingSpot(BlockPos spot) {
        blockedFishingSpots.put(spot.toImmutable(), getWorld().getTime() + 600);
        waterSearchCooldown = Math.min(waterSearchCooldown, 10);
        display.report(this, "Không có đường tới bờ câu");
    }

    public FishermanNpcEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.GENERIC_ARMOR, 4.0);
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        this.goalSelector.clear(goal -> true);
        this.targetSelector.clear(goal -> true);

        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new EscapeDangerGoal(this, 1.5));
        this.goalSelector.add(2, new FindAndSleepGoal(this, () -> sleeping));
        this.goalSelector.add(3, new WanderForBedGoal(this, 1.0, () -> sleeping));
        this.goalSelector.add(4, new FindAndEatFoodGoal(this, () -> supplies, 24, () -> display.shouldEat(), amount -> display.feed(amount)));
        
        // Goals cho Fisherman
        this.goalSelector.add(5, new DepositFishToChestGoal(this));
        this.goalSelector.add(6, new FishermanFishGoal(this));
        this.goalSelector.add(7, new FindFishingSpotGoal(this));
        this.goalSelector.add(9, new FishermanWanderGoal(this));

        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F) {
            @Override
            public boolean canStart() {
                return !sleeping.isSleeping() && super.canStart();
            }
        });
        this.goalSelector.add(10, new LookAroundGoal(this));

        ItemStack mainHand = this.getEquippedStack(EquipmentSlot.MAINHAND);
        if (mainHand.isEmpty()) {
            this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
            this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
    }

    @Override
    public void tick() {
        super.tick();
        World world = this.getWorld();
        if (world == null || world.isClient) return;

        display.tick(this, supplies, goalSelector);
        sleeping.setSearchCenter(memory.lastChestPos);
        sleeping.tick(this);

        if (!sleeping.isSleeping()) {
            if (this.getNavigation().isIdle()) {
                memory.tickIdle();
            } else {
                memory.resetIdle();
            }
            
            ItemStack mainHand = this.getEquippedStack(EquipmentSlot.MAINHAND);
            if (mainHand.isEmpty()) {
                this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
                this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0F);
            }
        }

        if (chestSearchCooldown > 0) chestSearchCooldown--;
        if (waterSearchCooldown > 0) waterSearchCooldown--;

        if (this.getHealth() <= 2.0F) {
            reservationSystem.releaseAll(this.getUuid());
        }
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);
        if (!(player.isSneaking() || held.getItem() instanceof ArmorItem || held.isFood())) {
            return ActionResult.FAIL;
        }
        ActionResult handled = equip.interactMob(this, player, ownerUUID, hand, supplies, ModItems.FISHERMAN_TOKEN);
        if (!handled.equals(ActionResult.FAIL)) return handled;
        return super.interactMob(player, hand);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        display.writeNbt(nbt);
        if (ownerUUID != null) {
            nbt.putUuid("OwnerUUID", ownerUUID);
        }
        nbt.putBoolean("IsFishermanNpc", true);
        nbt.put("FoodInventory", foodInventory.toNbtList());
        nbt.put("Supplies", supplies.toNbtList());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        display.readNbt(nbt);
        if (nbt.contains("OwnerUUID")) {
            ownerUUID = nbt.getUuid("OwnerUUID");
        }
        if (nbt.contains("Supplies")) supplies.readNbtList(nbt.getList("Supplies", 10));
        if (nbt.contains("FoodInventory")) {
            foodInventory.readNbtList(nbt.getList("FoodInventory", 10));
            memory.needsToDeposit = !foodInventory.isEmpty();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!getWorld().isClient) {
            sleeping.cancelMovingToBed(this);
            sleeping.wakeUp(this);
            reservationSystem.releaseAll(getUuid());
        }
        super.remove(reason);
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        if (!this.getWorld().isClient) {
            reservationSystem.releaseAll(this.getUuid());
            for (int i = 0; i < foodInventory.size(); i++) {
                ItemStack stack = foodInventory.getStack(i);
                if (!stack.isEmpty()) {
                    this.dropStack(stack);
                }
            }
            for (int i = 0; i < supplies.size(); i++) {
                if (!supplies.getStack(i).isEmpty()) this.dropStack(supplies.getStack(i));
            }
            this.dropStack(new ItemStack(ModItems.FISHERMAN_TOKEN));
        }
    }

    public UUID getOwnerUUID() { return ownerUUID; }
    public void setOwnerUUID(UUID uuid) { this.ownerUUID = uuid; }

    public boolean reserveChest(BlockPos pos) { return reservationSystem.tryReserve(pos, this.getUuid(), "WORK_CHEST"); }
    public void releaseChest(BlockPos pos) { reservationSystem.release(pos, this.getUuid(), "WORK_CHEST"); }
    public boolean isChestReserved(BlockPos pos) { return reservationSystem.isReservedByOthers(pos, this.getUuid(), "WORK_CHEST"); }

    public Inventory findNearestChest() {
        World world = this.getWorld();
        if (world == null) return null;

        // Dùng cache nếu còn hiệu lực
        if (memory.lastChestPos != null && chestSearchCooldown > 0) {
            BlockPos cached = memory.lastChestPos;
            if (world.isChunkLoaded(cached)) {
                BlockEntity be = world.getBlockEntity(cached);
                if (be instanceof com.example.block.FishermanChestBlockEntity inv) {
                    return inv;
                }
            }
            memory.lastChestPos = null;
        }

        if (chestSearchCooldown > 0) return null;

        BlockPos myPos = this.getBlockPos();
        int radius = 16;
        Inventory closestChest = null;
        double closestDist = Double.MAX_VALUE;
        BlockPos newChestPos = null;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = myPos.add(x, y, z);
                    if (!world.isChunkLoaded(pos)) continue;
                    BlockEntity be = world.getBlockEntity(pos);
                    if (be instanceof com.example.block.FishermanChestBlockEntity fishermanChest) {
                        double dist = this.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (dist < closestDist) {
                            closestDist = dist;
                            closestChest = fishermanChest;
                            newChestPos = pos;
                        }
                    }
                }
            }
        }
        chestSearchCooldown = 100;
        if (newChestPos != null) {
            memory.lastChestPos = newChestPos;
        }
        return closestChest;
    }


    public BlockPos findFishingSpot() {
        if (waterSearchCooldown > 0) return null;
        if (findNearestChest() == null) {
            display.report(this, "Không tìm thấy rương ngư dân");
            return null;
        }
        waterSearchCooldown = WATER_SEARCH_COOLDOWN;
        World world = getWorld();
        BlockPos center = memory.lastChestPos;
        blockedFishingSpots.values().removeIf(until -> until <= world.getTime());
        boolean blockedShore = false;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -16; x <= 16; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -16; z <= 16; z++) {
                    BlockPos pos = center.add(x, y, z);
                    double distance = squaredDistanceTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    if (distance >= bestDistance || !world.isChunkLoaded(pos)) continue;
                    if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()
                            || !world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) continue;
                    if (findFishingWater(pos) == null) continue;
                    if (blockedFishingSpots.containsKey(pos)) {
                        blockedShore = true;
                        continue;
                    }
                    best = pos;
                    bestDistance = distance;
                }
            }
        }
        if (best == null) display.report(this, blockedShore ? "Không có đường tới bờ câu" : "Chưa tìm thấy hồ nước nguồn 4×4");
        return best;
    }

    public BlockPos findFishingWater(BlockPos shore) {
        World world = getWorld();
        for (net.minecraft.util.math.Direction forward : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            net.minecraft.util.math.Direction side = forward.rotateYClockwise();
            for (int depth = 0; depth <= 2; depth++) {
                BlockPos edge = shore.down(depth).offset(forward);
                if (!isSurfaceWater(world, edge)) continue;
                for (int offset = 0; offset < 4; offset++) {
                    BlockPos corner = edge.offset(side.getOpposite(), offset);
                    boolean valid = true;
                    for (int row = 0; row < 4 && valid; row++) {
                        for (int col = 0; col < 4; col++) {
                            if (!isSurfaceWater(world, corner.offset(forward, row).offset(side, col))) {
                                valid = false;
                                break;
                            }
                        }
                    }
                    if (valid) return edge;
                }
            }
        }
        return null;
    }

    private boolean isSurfaceWater(World world, BlockPos pos) {
        return world.isChunkLoaded(pos) && world.getBlockState(pos).isOf(Blocks.WATER)
                && world.getFluidState(pos).isStill() && world.getBlockState(pos.up()).isAir();
    }

    public SimpleInventory getInventory() {
        return foodInventory;
    }

    @Override
    public Text getName() {
        return Text.literal("Ngư Dân");
    }
}
