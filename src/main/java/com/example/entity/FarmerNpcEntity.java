package com.example.entity;

import com.example.ai.farmer.*;
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
import net.minecraft.world.World;

import java.util.*;

public class FarmerNpcEntity extends PathAwareEntity {
    private final NpcDisplayComponent display = new NpcDisplayComponent();
    private final NpcEquipmentComponent equip = new NpcEquipmentComponent();
    public final NpcSleepingComponent sleeping = new NpcSleepingComponent();

    private BlockPos currentFarmPos;
    private UUID ownerUUID;
    private UUID followPlayerUUID;
    public final SimpleInventory foodInventory = new SimpleInventory(9);
    public final FarmerMemory memory = new FarmerMemory();
    // Thay vì static Set
    private static final Map<World, Set<BlockPos>> RESERVED_BEDS_MAP = new HashMap<>();
    private static final Set<BlockPos> RESERVED_CROPS = new HashSet<>();

    public boolean reserveCrop(BlockPos pos) {
        return RESERVED_CROPS.add(pos);
    }

    public void releaseCrop(BlockPos pos) {
        RESERVED_CROPS.remove(pos);
    }

    private static final Set<BlockPos> RESERVED_FARMLAND = new HashSet<>();

    public boolean reserveFarmland(BlockPos pos) {
        return RESERVED_FARMLAND.add(pos);
    }

    public void releaseFarmland(BlockPos pos) {
        RESERVED_FARMLAND.remove(pos);
    }

    public Set<BlockPos> getReservedBeds() {
        return RESERVED_BEDS_MAP.computeIfAbsent(this.getWorld(), w -> new HashSet<>());
    }

    private static final Set<BlockPos> RESERVED_CHESTS = new HashSet<>();

    public boolean reserveChest(BlockPos pos) {
        return RESERVED_CHESTS.add(pos);
    }

    public void releaseChest(BlockPos pos) {
        RESERVED_CHESTS.remove(pos);
    }

    public FarmerNpcEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
    }

    // ===== AI GOALS =====
    @Override
    protected void initGoals() {
        super.initGoals();
        this.goalSelector.clear(goal -> true);
        this.targetSelector.clear(goal -> true);

        this.goalSelector.add(0, new SwimGoal(this)); // ko chet duoi
        this.goalSelector.add(1, new EscapeDangerGoal(this, 1.4)); // chay khi bi dame

        this.goalSelector.add(2, new SleepAtNightGoal(this)); // di ngu
        this.goalSelector.add(3, new HarvestCropGoal(this) {
            @Override
            public boolean canStart() {
                return !isSleeping() && super.canStart();
            }
        }); // thu hoach
        this.goalSelector.add(4, new DepositToChestGoal(this) {
            @Override
            public boolean canStart() {
                return !isSleeping() && super.canStart();
            }
        }); // cât đô
        this.goalSelector.add(5, new PlantSeedGoal(this) {
            @Override
            public boolean canStart() {
                return !isSleeping() && super.canStart();
            }
        }); // trồng cây
        this.goalSelector.add(6, new ReturnToFarmGoal(this) {
            @Override
            public boolean canStart() {
                return !isSleeping() && super.canStart();
            }
        }); // quay lại farm
        this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F) {
            @Override
            public boolean canStart() {
                return !isSleeping() && super.canStart();
            }
        }); // nhin player
        this.goalSelector.add(9, new WanderAroundFarGoal(this, 1.0D) {
            @Override
            public boolean canStart() {
                return !isSleeping() && super.canStart();
            }
        }); // đi lang thang xa
        this.goalSelector.add(8, new LookAroundGoal(this) {
            @Override
            public boolean canStart() {
                return !isSleeping() && super.canStart();
            }
        });//nhin xunh quanh
    }


    @Override
    public void tick() {
        super.tick();
        if (this.getWorld() == null || this.getWorld().isClient) return;
        display.tick(this, foodInventory);

        sleeping.tick(this, getReservedBeds());
        if (!sleeping.isSleeping()) {
            memory.tickIdle();
        }
        ItemStack mainHand = this.getEquippedStack(EquipmentSlot.MAINHAND);
        if (mainHand.isEmpty()) {
            this.equipStack(
                    EquipmentSlot.MAINHAND,
                    new ItemStack(Items.IRON_HOE)
            );

            // Không drop khi chết
            this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }

    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        // Nếu player đang cố gắng lấy cuốc từ NPC
        ItemStack held = player.getStackInHand(hand);
        // Nếu đang cầm armor hoặc food → ignore
        if (held.getItem() instanceof ArmorItem || held.isFood()) {

        } else {
            return ActionResult.FAIL;
        }


        ActionResult handled = equip.interactMob(this, player, ownerUUID, hand, foodInventory, ModItems.FARMER_TOKEN);
        if (!handled.equals(ActionResult.FAIL)) {
            return handled;
        }
        return super.interactMob(player, hand);
    }

    // ===== NBT SAVE/LOAD =====
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (ownerUUID != null) {
            nbt.putUuid("OwnerUUID", ownerUUID);
        }
        if (followPlayerUUID != null) {
            nbt.putUuid("FollowPlayer", followPlayerUUID);
        }
        nbt.putBoolean("IsFarmerNpc", true);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("OwnerUUID")) {
            ownerUUID = nbt.getUuid("OwnerUUID");
        }
        if (nbt.containsUuid("FollowPlayer")) {
            followPlayerUUID = nbt.getUuid("FollowPlayer");
        }
        ItemStack mainHand = this.getEquippedStack(EquipmentSlot.MAINHAND);
        if (mainHand.isEmpty()) {
            this.equipStack(
                    EquipmentSlot.MAINHAND,
                    new ItemStack(Items.IRON_HOE)
            );

            // Không drop khi chết
            this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }

    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (sleeping.isSleeping()) {
            sleeping.wakeUp(this, getReservedBeds());
        }
        return super.damage(source, amount);
    }

    @Override
    public void remove(RemovalReason reason) {
        sleeping.wakeUp(this, getReservedBeds());
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
    public BlockPos findNearestFarmland() {
        World world = getWorld();
        BlockPos center = getBlockPos();
        BlockPos nearestMatureCrop = null;
        double nearestCropDist = Double.MAX_VALUE;
        BlockPos nearestEmptyFarmland = null;
        double nearestFarmlandDist = Double.MAX_VALUE;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int dx = -24; dx <= 24; dx++) {
            for (int dz = -24; dz <= 24; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    mutable.set(
                            center.getX() + dx,
                            center.getY() + dy,
                            center.getZ() + dz
                    );
                    if (!world.isChunkLoaded(mutable)) continue;
                    BlockState state = world.getBlockState(mutable);
                    if (!(state.getBlock() instanceof FarmlandBlock)) continue;
                    BlockPos farmlandPos = mutable.toImmutable();
                    BlockPos cropPos = farmlandPos.up();
                    BlockState cropState = world.getBlockState(cropPos);

                    double dist = this.squaredDistanceTo(
                            cropPos.getX() + 0.5,
                            cropPos.getY() + 0.5,
                            cropPos.getZ() + 0.5
                    );
                    // 🌾 Ưu tiên cây chín
                    if (cropState.getBlock() instanceof CropBlock crop
                            && crop.isMature(cropState)) {

                        if (RESERVED_CROPS.contains(cropPos)) continue;

                        if (dist < nearestCropDist) {
                            nearestCropDist = dist;
                            nearestMatureCrop = cropPos.toImmutable();
                        }
                    }
                    // 🌱 Farmland trống
                    else if (cropState.isAir()) {

                        if (RESERVED_FARMLAND.contains(farmlandPos)) continue;

                        if (dist < nearestFarmlandDist) {
                            nearestFarmlandDist = dist;
                            nearestEmptyFarmland = farmlandPos.toImmutable();
                        }
                    }
                }
            }
        }
        // Ưu tiên cây chín
        if (nearestMatureCrop != null) return nearestMatureCrop;
        // Không có cây → trả farmland trống
        return nearestEmptyFarmland;
    }

    /**
     * Check xem có hạt giống trong inventory không
     */
    public boolean hasSeeds() {
        for (int i = 0; i < foodInventory.size(); i++) {
            ItemStack stack = foodInventory.getStack(i);
            if (isSeedItem(stack.getItem())) {
                return true;
            }
        }
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
    public ItemStack takeSeed() {
        for (int i = 0; i < foodInventory.size(); i++) {
            ItemStack stack = foodInventory.getStack(i);
            if (isSeedItem(stack.getItem())) {
                return foodInventory.removeStack(i, 1);
            }
        }


        Inventory chest = findNearestChest();
        if (chest == null) return ItemStack.EMPTY;

        for (int i = 0; i < chest.size(); i++) {
            ItemStack stack = chest.getStack(i);
            if (isSeedItem(stack.getItem())) {
                return chest.removeStack(i, 1);
            }
        }
        return ItemStack.EMPTY;

    }

    /**
     * Check xem item có phải hạt giống không
     */
    private boolean isSeedItem(Item item) {
        return item == Items.WHEAT_SEEDS ||
                item == Items.BEETROOT_SEEDS ||
                item == Items.CARROT ||
                item == Items.POTATO ||
                item == Items.PUMPKIN_SEEDS ||
                item == Items.MELON_SEEDS;
    }

    /**
     * Trồng hạt giống tại vị trí farmland
     */
    public void plantSeedAt(BlockPos farmlandPos) {
        ItemStack seed = takeSeed();
        if (seed.isEmpty()) return;

        World world = getWorld();
        BlockPos cropPos = farmlandPos.up();

        // Nếu đã có cây thì bỏ qua
        if (!world.getBlockState(cropPos).isAir()) {
            foodInventory.addStack(seed);
            return;
        }

        // Trồng dựa vào loại hạt giống
        if (seed.getItem() == Items.WHEAT_SEEDS) {
            world.setBlockState(cropPos, net.minecraft.block.Blocks.WHEAT.getDefaultState());
        } else if (seed.getItem() == Items.BEETROOT_SEEDS) {
            world.setBlockState(cropPos, net.minecraft.block.Blocks.BEETROOTS.getDefaultState());
        } else if (seed.getItem() == Items.PUMPKIN_SEEDS) {
            world.setBlockState(cropPos, net.minecraft.block.Blocks.PUMPKIN_STEM.getDefaultState());
        } else if (seed.getItem() == Items.MELON_SEEDS) {
            world.setBlockState(cropPos, net.minecraft.block.Blocks.MELON_STEM.getDefaultState());
        } else if (seed.getItem() == Items.CARROT) {
            world.setBlockState(cropPos, Blocks.CARROTS.getDefaultState());
        } else if (seed.getItem() == Items.POTATO) {
            world.setBlockState(cropPos, Blocks.POTATOES.getDefaultState());
        }
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
     * Tìm rương gần nhất trong bán kính 12 block
     */
    public Inventory findNearestChest() {
        BlockPos center = getBlockPos();
        World world = getWorld();

        for (BlockPos pos : BlockPos.iterate(center.add(-12, -2, -12), center.add(12, 2, 12))) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof Inventory inv) {
                return inv;
            }
        }
        return null;
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

    public boolean isChestReserved(BlockPos pos) {
        return RESERVED_CHESTS.contains(pos);
    }

    @Override
    public Text getName() {
        return Text.literal("Nông dân");
    }

}
