package com.example.entity;

import com.example.ai.FindAndSleepGoal;
import com.example.ai.GlobalReservationSystem;
import com.example.ai.WanderForBedGoal;
import com.example.ai.foodChest.FindAndEatFoodGoal;
import com.example.ai.miner.DepositOreToChestGoal;
import com.example.ai.miner.MineOreVeinGoal;
import com.example.ai.miner.MinerMemory;
import com.example.ai.miner.MinerWanderGoal;
import com.example.entity.base.NpcDisplayComponent;
import com.example.entity.base.NpcEquipmentComponent;
import com.example.entity.base.NpcSleepingComponent;
import com.example.registry.ModBlocks;
import com.example.registry.ModItems;
import net.minecraft.block.BlockState;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class MinerNpcEntity extends PathAwareEntity {

    // ===== BASE COMPONENTS =====
    public final NpcDisplayComponent display = new NpcDisplayComponent();
    private final NpcEquipmentComponent equip = new NpcEquipmentComponent();

    /**
     * Sleep component — tìm giường và ngủ khi trời tối, thức dậy khi sáng
     */
    public final NpcSleepingComponent sleeping = new NpcSleepingComponent()
            .withSearchRadius(16)
            .withCooldown(120)
            .withWanderDuration(120);

    // ===== MINER-SPECIFIC =====
    private UUID ownerUUID;

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    /**
     * Inventory chứa khoáng sản + food
     * Slots 0-8: khoáng sản thu thập
     * Food được giữ riêng và không bị cất vào rương
     */
    public final SimpleInventory foodInventory = new SimpleInventory(9);
    private final SimpleInventory supplies = new SimpleInventory(9);
    public final MinerMemory memory = new MinerMemory();

    // ===== SEARCH DISTANCES =====
    public static final int FIND_CHEST_DISTANCE = 24;
    public static final int FIND_ORE_DISTANCE = 16; // Tìm mỏ trong bán kính 16 blocks

    // ===== GLOBAL RESERVATION SYSTEM =====
    private final GlobalReservationSystem reservationSystem = GlobalReservationSystem.getInstance(getWorld());

    // ===== SEARCH COOLDOWNS =====
    // Khởi tạo sẵn để tránh scan ngay khi spawn (gây lag)
    private int oreSearchCooldown;
    private final Map<BlockPos, Long> unreachableOres = new HashMap<>();

    public void onOreDepleted(BlockPos ore) {
        oreSearchCooldown = 0;
        unreachableOres.remove(ore);
    }

    public boolean hasEmptyOreSlot() {
        for (int slot = 0; slot < foodInventory.size(); slot++) {
            if (foodInventory.getStack(slot).isEmpty()) return true;
        }
        return false;
    }

    public void skipUnreachableOre(BlockPos ore) {
        unreachableOres.put(ore.toImmutable(), getWorld().getTime() + 200);
        oreSearchCooldown = 0;
    }
    private int chestSearchCooldown;
    private static final int ORE_SEARCH_COOLDOWN   = 60;  // 3 giây giữa các lần scan
    private static final int CHEST_SEARCH_COOLDOWN = 100; // 5 giây giữa các lần scan chest

    // ===== RANDOM =====
    private final Random random = new Random();

    // ===== RESERVATION METHODS =====

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

    // ===== CONSTRUCTOR =====

    public MinerNpcEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    // ===== ATTRIBUTES =====

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.GENERIC_ARMOR, 4.0);
    }

    // ===== AI GOALS =====

    @Override
    protected void initGoals() {
        super.initGoals();
        this.goalSelector.clear(goal -> true);
        this.targetSelector.clear(goal -> true);

        // priority 0: Bơi
        this.goalSelector.add(0, new SwimGoal(this));

        // priority 1: Chạy trốn nguy hiểm
        this.goalSelector.add(1, new EscapeDangerGoal(this, 1.5));

        // priority 2: Ngủ khi đến giường vào ban đêm
        this.goalSelector.add(2, new FindAndSleepGoal(this, () -> sleeping));

        // priority 3: Lang thang tìm giường (khi không tìm được giường)
        this.goalSelector.add(3, new WanderForBedGoal(this, 1.0, () -> sleeping));

        // priority 4: Ăn khi đói (lấy food từ chest có food)
        this.goalSelector.add(4, new FindAndEatFoodGoal(this, () -> supplies, 24, () -> display.shouldEat(), amount -> display.feed(amount)));

        // priority 5: Cất khoáng sản vào rương (theo lịch từ MineOreVeinGoal)
        this.goalSelector.add(5, new DepositOreToChestGoal(this));

        // priority 6: Đào Mỏ Quặng — goal chính
        this.goalSelector.add(6, new MineOreVeinGoal(this));

        // priority 8: Nhìn vào player khi rảnh
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F) {
            @Override
            public boolean canStart() {
                return !sleeping.isSleeping() && super.canStart();
            }
        });

        // priority 9: Lang thang khi không có mỏ
        this.goalSelector.add(9, new MinerWanderGoal(this));

        // priority 10: Nhìn xung quanh khi đứng yên
        this.goalSelector.add(10, new LookAroundGoal(this));

        // Trang bị cuốc nếu chưa có
        ItemStack mainHand = this.getEquippedStack(EquipmentSlot.MAINHAND);
        if (mainHand.isEmpty()) {
            this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
            this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
    }

    // ===== TICK =====

    @Override
    public void tick() {
        super.tick();
        World world = this.getWorld();
        if (world == null || world.isClient) return;

        // Cập nhật display (HP + inventory)
        display.tick(this, supplies, goalSelector);

        // Tick sleeping component (tự thức dậy khi sáng)
        sleeping.setSearchCenter(memory.lastChestPos);
        sleeping.tick(this);

        // Tick idle counter (chỉ khi không ngủ)
        if (!sleeping.isSleeping()) {
            if (this.getNavigation().isIdle()) {
                memory.tickIdle();
            } else {
                memory.resetIdle();
            }
        }

        // Giảm cooldown
        if (oreSearchCooldown > 0)  oreSearchCooldown--;
        if (chestSearchCooldown > 0) chestSearchCooldown--;

        // Đảm bảo luôn có cuốc trong tay (trừ khi đang ngủ)
        if (!sleeping.isSleeping()) {
            ItemStack mainHand = this.getEquippedStack(EquipmentSlot.MAINHAND);
            if (mainHand.isEmpty()) {
                this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
                this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0F);
            }
        }

        // Cleanup khi HP thấp nguy hiểm
        if (this.getHealth() <= 2.0F) {
            cleanupAllReservations();
        }
    }

    // ===== INTERACTION =====

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);

        if (!(player.isSneaking() || held.getItem() instanceof ArmorItem || held.isFood())) {
            return ActionResult.FAIL;
        }

        ActionResult handled = equip.interactMob(this, player, ownerUUID, hand, supplies, ModItems.MINER_TOKEN);
        if (!handled.equals(ActionResult.FAIL)) {
            return handled;
        }
        return super.interactMob(player, hand);
    }

    // ===== NBT =====

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        display.writeNbt(nbt);
        nbt.put("FoodInventory", foodInventory.toNbtList());
        nbt.put("Supplies", supplies.toNbtList());
        if (ownerUUID != null) {
            nbt.putUuid("OwnerUUID", ownerUUID);
        }
        if (memory.lastChestPos != null) {
            nbt.putLong("MinerChestPos", memory.lastChestPos.asLong());
            nbt.putString("MinerChestDimension", getWorld().getRegistryKey().getValue().toString());
        }
        nbt.putBoolean("IsMinerNpc", true);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        display.readNbt(nbt);
        memory.lastChestPos = nbt.contains("MinerChestPos")
                && getWorld().getRegistryKey().getValue().toString().equals(nbt.getString("MinerChestDimension"))
                ? BlockPos.fromLong(nbt.getLong("MinerChestPos")) : null;
        if (nbt.contains("Supplies")) supplies.readNbtList(nbt.getList("Supplies", 10));
        if (nbt.contains("FoodInventory")) {
            foodInventory.readNbtList(nbt.getList("FoodInventory", 10));
            for (int slot = 0; slot < foodInventory.size(); slot++) {
                ItemStack stack = foodInventory.getStack(slot);
                if (!stack.isEmpty() && !stack.isFood()) {
                    memory.needsToDeposit = true;
                    break;
                }
            }
        }
        if (nbt.containsUuid("OwnerUUID")) {
            ownerUUID = nbt.getUuid("OwnerUUID");
        }
        ItemStack mainHand = this.getEquippedStack(EquipmentSlot.MAINHAND);
        if (mainHand.isEmpty()) {
            this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
            this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
    }

    // ===== LIFECYCLE =====

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (sleeping.isSleeping()) {
            sleeping.wakeUp(this);
        }
        boolean result = super.damage(source, amount);
        if (!this.isAlive()) {
            cleanupAllReservations();
        }
        return result;
    }

    @Override
    public void remove(RemovalReason reason) {
        sleeping.cancelMovingToBed(this);
        sleeping.wakeUp(this);
        cleanupAllReservations();
        super.remove(reason);
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        cleanupAllReservations();
        super.onDeath(damageSource);
    }

    // ===== SEARCH METHODS =====

    /**
     * Tìm MỘT ore vein ngẫu nhiên trong bán kính FIND_ORE_DISTANCE (16 blocks).
     *
     * Tối ưu hiệu năng:
     * - Có cooldown 3 giây giữa các lần scan
     * - Chỉ scan Y từ -4 đến +8 (ore vein là surface block)
     * - Tổng block scan: 33 × 13 × 33 = ~14,157 (thay vì 35,937)
     *
     * @return BlockPos ngẫu nhiên hoặc null nếu không có mỏ nào
     */
    public BlockPos findRandomOreVein() {
        if (oreSearchCooldown > 0) return null;

        World world = getWorld();
        if (world == null) return null;

        if (findNearestChest() == null || memory.lastChestPos == null) {
            display.report(this, "Không tìm thấy rương thợ mỏ");
            return null;
        }
        BlockPos center = memory.lastChestPos;
        List<BlockPos> candidates = new ArrayList<>();
        long now = world.getTime();
        unreachableOres.values().removeIf(until -> until <= now);
        boolean hasBlockedOre = false;

        int radius = FIND_ORE_DISTANCE;

        // ORE VEIN là surface block nên chỉ cần scan Y từ -4 đến +8
        // Giảm từ 35,937 xuống ~14,157 block/lần scan
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -4; dy <= 8; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (!world.isChunkLoaded(pos)) continue;

                    if (world.getBlockState(pos).getBlock() != ModBlocks.ORE_VEIN) continue;

                    if (unreachableOres.containsKey(pos)) {
                        hasBlockedOre = true;
                        continue;
                    }
                    candidates.add(pos.toImmutable());
                }
            }
        }

        oreSearchCooldown = ORE_SEARCH_COOLDOWN; // 3 giây cooldown sau khi scan

        if (candidates.isEmpty()) {
            display.report(this, hasBlockedOre ? "Đang chờ thử lại đường tới mỏ" : "Chưa tìm thấy mỏ quặng");
            return null;
        }
        display.clearReport();
        return candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * Tìm Rương Thợ mỏ (MinerChestBlockEntity) gần nhất trong bán kính FIND_CHEST_DISTANCE.
     * Chỉ nhận ra rương đúng loại (craft bằng Chest + Cuốc chim full độ bền).
     * Không cần khóa (reservation) vì mỗi loại rương chỉ dành cho 1 loại NPC.
     */
    public Inventory findNearestChest() {
        World world = getWorld();
        if (world == null) return null;

        // Bước 1: Dùng cache nếu còn hiệu lực
        if (memory.lastChestPos != null) {
            BlockPos cached = memory.lastChestPos;
            if (world.isChunkLoaded(cached)) {
                net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(cached);
                if (be instanceof com.example.block.MinerChestBlockEntity inv) {
                    return inv;
                }
            }
            if (!world.isChunkLoaded(cached)) return null;
            memory.lastChestPos = null;
            chestSearchCooldown = 0;
        }

        // Bước 2: Kiểm tra cooldown
        if (chestSearchCooldown > 0) return null;

        // Bước 3: Full scan
        BlockPos center = getBlockPos();
        double closestDist = Double.MAX_VALUE;
        Inventory closestChest = null;
        BlockPos newChestPos = null;

        for (int dx = -FIND_CHEST_DISTANCE; dx <= FIND_CHEST_DISTANCE; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                for (int dz = -FIND_CHEST_DISTANCE; dz <= FIND_CHEST_DISTANCE; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (!world.isChunkLoaded(pos)) continue;

                    net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(pos);
                    if (be instanceof com.example.block.MinerChestBlockEntity minerChest) {
                        double dist = this.squaredDistanceTo(
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (dist < closestDist) {
                            closestDist = dist;
                            closestChest = minerChest;
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


    // ===== GETTERS =====

    public SimpleInventory getInventory() {
        return foodInventory;
    }

    @Override
    public Text getName() {
        return Text.literal("Thợ mỏ");
    }
}
