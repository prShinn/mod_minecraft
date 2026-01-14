package com.example.entity;

import com.example.ai.FindAndSleepGoal;
import com.example.ai.GlobalReservationSystem;
import com.example.ai.WanderForBedGoal;
import com.example.ai.miner.MinerMemory;
import com.example.entity.base.NpcDisplayComponent;
import com.example.entity.base.NpcEquipmentComponent;
import com.example.entity.base.NpcSleepingComponent;
import com.example.registry.ModItems;
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

import java.util.UUID;

public class MinerNpcEntity extends PathAwareEntity {
    // ===== BASE COMPONENTS (Reusable across all NPCs) =====
    private final NpcDisplayComponent display = new NpcDisplayComponent();
    private final NpcEquipmentComponent equip = new NpcEquipmentComponent();
    public final NpcSleepingComponent sleeping = new NpcSleepingComponent()
            .withSearchRadius(16)
            .withCooldown(120)
            .withWanderDuration(120);

    // ===== MINER-SPECIFIC PROPERTIES =====
    private UUID ownerUUID;
    public final SimpleInventory foodInventory = new SimpleInventory(9);
    public final MinerMemory memory = new MinerMemory();

    public final static int FIND_CHEST_DISTANCE = 24;
    public final static int FIND_ORE_DISTANCE = 16;

    // ===== GLOBAL RESERVATION SYSTEM =====
    // ✅ Dùng chung GlobalReservationSystem với tất cả NPCs (Farmer, Lumberjack, Soldier, v.v.)
    private final GlobalReservationSystem reservationSystem = GlobalReservationSystem.getInstance();

    // ===== COOLDOWN VARIABLES =====
    private int oreSearchCooldown = 0;
    private static final int ORE_SEARCH_COOLDOWN = 30; // 1.5 seconds

    // ===== RESERVATION METHODS (Using GlobalReservationSystem) =====

    public boolean reserveOre(BlockPos pos) {
        return reservationSystem.tryReserve(pos, this.getUuid(), "ORE");
    }


    public void releaseOre(BlockPos pos) {
        reservationSystem.release(pos, this.getUuid(), "ORE");
    }

    public boolean reserveChest(BlockPos pos) {
        return reservationSystem.tryReserve(pos, this.getUuid(), "CHEST");
    }

    public void releaseChest(BlockPos pos) {
        reservationSystem.release(pos, this.getUuid(), "CHEST");
    }


    public boolean isChestReserved(BlockPos pos) {
        return reservationSystem.isReservedByOthers(pos, this.getUuid(), "CHEST");
    }

    /**
     * Cleanup tất cả reservations khi NPC bị remove
     * ✅ Includes bed, ores, chests
     */
    private void cleanupAllReservations() {
        reservationSystem.releaseAll(this.getUuid());
    }

    public MinerNpcEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        this.goalSelector.clear(goal -> true);
        this.targetSelector.clear(goal -> true);

        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new EscapeDangerGoal(this, 1.5));

        // ✅ Sleep Goals - Dùng GlobalReservationSystem cho bed
        this.goalSelector.add(2, new FindAndSleepGoal(this, sleeping));
        this.goalSelector.add(3, new WanderForBedGoal(this, 1.0, sleeping));

        // TODO: Implement Miner-specific goals
        // this.goalSelector.add(4, new MineOreGoal(this));
        // this.goalSelector.add(5, new DepositOreToChestGoal(this));

        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F) {
            @Override
            public boolean canStart() {
                return !sleeping.isSleeping() && super.canStart();
            }
        });

        this.goalSelector.add(7, new WanderAroundFarGoal(this, 1.0D) {
            @Override
            public boolean canStart() {
                return !sleeping.isSleeping() && super.canStart();
            }
        });

        this.goalSelector.add(8, new LookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        World world = this.getWorld();
        if (world == null || world.isClient) return;

        // ===== UPDATE DISPLAY (HP + HUNGER) =====
        display.tick(this, foodInventory);

        sleeping.tick(this);
        if (!sleeping.isSleeping()) {
            memory.tickIdle();
        }

        // ===== EQUIPMENT SETUP =====
        ItemStack mainHand = this.getEquippedStack(EquipmentSlot.MAINHAND);
        if (mainHand.isEmpty()) {
            this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
            this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }

        // ===== COOLDOWN DECREMENT =====
        if (oreSearchCooldown > 0) {
            oreSearchCooldown--;
        }
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);

        if (!(player.isSneaking() || held.getItem() instanceof ArmorItem || held.isFood())) {
            return ActionResult.FAIL;
        }

        ActionResult handled = equip.interactMob(this, player, ownerUUID, hand, foodInventory, ModItems.MINER_TOKEN);
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
        nbt.putBoolean("IsMinerNpc", true);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("OwnerUUID")) {
            ownerUUID = nbt.getUuid("OwnerUUID");
        }

        ItemStack mainHand = this.getEquippedStack(EquipmentSlot.MAINHAND);
        if (mainHand.isEmpty()) {
            this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
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
        sleeping.wakeUp(this);
        cleanupAllReservations(); // 🔥 CLEANUP ALL (bed + ores + chests)
        super.remove(reason);
    }

    /**
     * Tìm ore gần nhất
     * ✅ Có cooldown để tránh lag
     * ✅ Dùng GlobalReservationSystem
     */
    public BlockPos findNearestOre() {
        // ===== COOLDOWN CHECK =====
        if (oreSearchCooldown > 0) {
            return null;
        }

        World world = getWorld();
        if (world == null) return null;

        BlockPos center = getBlockPos();
        // TODO: Implement ore scanning logic
        // Scan cho iron, gold, diamond, deepslate ores
        // Check với GlobalReservationSystem: isReservedByOthers(pos, getUuid(), "ORE")

        // ===== SET COOLDOWN AFTER SEARCH =====
        oreSearchCooldown = ORE_SEARCH_COOLDOWN;

        return null; // TODO: Return found ore or null
    }

    /**
     * Tìm chest gần nhất
     */
    public Inventory findNearestChest() {
        BlockPos center = getBlockPos();
        World world = getWorld();
        if (world == null) return null;

        double closestDist = Double.MAX_VALUE;
        Inventory closestChest = null;

        for (int dx = -FIND_CHEST_DISTANCE; dx <= FIND_CHEST_DISTANCE; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                for (int dz = -FIND_CHEST_DISTANCE; dz <= FIND_CHEST_DISTANCE; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (!world.isChunkLoaded(pos)) continue;

                    BlockEntity be = world.getBlockEntity(pos);
                    if (be instanceof Inventory inv) {
                        // ✅ Check xem chest có bị reserve bởi NPC khác không
                        if (reservationSystem.isReservedByOthers(pos, this.getUuid(), "CHEST")) {
                            continue;
                        }

                        double dist = this.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (dist < closestDist) {
                            closestDist = dist;
                            closestChest = inv;
                        }
                    }
                }
            }
        }
        return closestChest;
    }

    public SimpleInventory getInventory() {
        return foodInventory;
    }

    @Override
    public Text getName() {
        return Text.literal("Thợ mỏ");
    }
}
