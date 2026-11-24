package com.example.entity.base;

import com.example.ai.DeliveryTarget;
import com.example.ai.FindAndSleepGoal;
import com.example.ai.GlobalReservationSystem;
import com.example.ai.WanderForBedGoal;
import com.example.ai.foodChest.FindAndEatFoodGoal;
import com.example.block.ProfessionChestBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lớp nền cho mọi NPC nghề nghiệp làm việc quanh một rương nghề
 * (Nông dân, Tiều phu, Thợ mỏ, Ngư dân, Thợ chăn nuôi, Đầu bếp).
 *
 * Phần chung nằm ở đây: ăn, ngủ, hiển thị trạng thái, thu hồi bằng token,
 * trang bị, đặt chỗ (reservation), lưu NBT, rơi đồ khi chết, tìm rương nghề.
 * Lớp con chỉ khai báo phần riêng của nghề qua các hàm abstract/hook bên dưới.
 *
 * Thứ tự goal: 0 bơi · 1 tránh nguy hiểm · 2 ngủ · 3 tìm giường · 4 ăn ·
 * 5..9 việc của nghề (addWorkGoals) · 10 nhìn người chơi · 11 nhìn quanh.
 */
public abstract class WorkerNpcEntity extends PathAwareEntity {
    private static final int CHEST_SEARCH_COOLDOWN = 100;
    private static final int DEFAULT_CARGO_SIZE = 27;
    private static final int AUTO_DEPOSIT_THRESHOLD = 16;
    private static final int AUTO_DEPOSIT_IDLE_TICKS = 200;

    public final NpcDisplayComponent display = new NpcDisplayComponent();
    public final NpcSleepingComponent sleeping = createSleepingComponent();
    private final NpcEquipmentComponent equip = new NpcEquipmentComponent();

    /** Hàng NPC đang mang, sẽ nộp về rương. */
    protected final SimpleInventory cargo;
    /** Đồ ăn của chính NPC (người chơi cho ăn bằng chuột phải). */
    protected final SimpleInventory supplies = new SimpleInventory(9);

    private final GlobalReservationSystem reservationSystem = GlobalReservationSystem.getInstance(getWorld());
    private final Map<UUID, Long> skippedTargets = new HashMap<>();
    private UUID ownerUUID;
    private BlockPos chestPos;
    private boolean needsToDeposit;
    private int idleTicks;
    private int chestSearchCooldown;

    protected WorkerNpcEntity(EntityType<? extends PathAwareEntity> type, World world) {
        this(type, world, DEFAULT_CARGO_SIZE);
    }

    /** @param cargoSize số ô túi đồ — giữ đúng kích thước cũ của từng nghề để NBT cũ không mất đồ. */
    protected WorkerNpcEntity(EntityType<? extends PathAwareEntity> type, World world, int cargoSize) {
        super(type, world);
        this.cargo = new SimpleInventory(cargoSize);
    }

    // ===== Phần riêng của từng nghề =====

    /** Loại rương nghề mà NPC nhận làm nơi làm việc. */
    protected abstract Class<? extends ProfessionChestBlockEntity> chestType();

    /** Token trả về khi thu hồi / NPC chết. */
    public abstract Item tokenItem();

    /** Tên nghề hiển thị trên đầu NPC. */
    public abstract String professionName();

    /** Công cụ cầm trên tay (chỉ để hiển thị, không rơi ra). */
    protected abstract ItemStack workTool();

    /**
     * Thêm goal riêng của nghề, dùng priority 5..9 (kể cả goal đi lang thang).
     * Lưu ý: chạy trong constructor của MobEntity, trước khi field của lớp con được khởi tạo —
     * goal chỉ được giữ tham chiếu tới NPC, không đọc field ngay trong constructor.
     */
    protected abstract void addWorkGoals();

    // ===== Hook tuỳ chọn =====

    /** Component ngủ; override để chỉnh bán kính/cooldown. Không được dùng field của lớp con. */
    protected NpcSleepingComponent createSleepingComponent() {
        return new NpcSleepingComponent();
    }

    /** Bán kính ngang tìm rương nghề quanh NPC. */
    protected int chestSearchRadius() {
        return 16;
    }

    /** Biên độ dọc (±) tìm rương nghề. */
    protected int chestSearchHeight() {
        return 4;
    }

    /** Key NBT cũ của túi đồ (trước khi chuyển sang lớp nền), null nếu không có. */
    @Nullable
    protected String legacyCargoKey() {
        return null;
    }

    /**
     * Tự bật cờ nộp hàng khi mang ≥16 món hoặc rảnh 10 giây.
     * Nghề cũ có lịch nộp riêng trong goal nên mặc định tắt.
     */
    protected boolean usesAutoDeposit() {
        return false;
    }

    protected void writeWorkerNbt(NbtCompound nbt) {
    }

    /** Chạy sau khi phần chung đã đọc xong (túi đồ, rương, cờ nộp hàng). */
    protected void readWorkerNbt(NbtCompound nbt) {
    }

    /** Rơi thêm đồ riêng của nghề khi NPC chết. */
    protected void dropWorkerItems() {
    }

    public static DefaultAttributeContainer.Builder createWorkerAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.GENERIC_ARMOR, 4.0);
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new EscapeDangerGoal(this, 1.5));
        this.goalSelector.add(2, new FindAndSleepGoal(this, () -> sleeping));
        this.goalSelector.add(3, new WanderForBedGoal(this, 1.0, () -> sleeping));
        this.goalSelector.add(4, new FindAndEatFoodGoal(this, () -> supplies, 24,
                () -> display.shouldEat(), amount -> display.feed(amount)));
        addWorkGoals();
        this.goalSelector.add(10, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F) {
            @Override
            public boolean canStart() {
                return !sleeping.isSleeping() && super.canStart();
            }
        });
        this.goalSelector.add(11, new LookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;

        display.tick(this, supplies, goalSelector);
        sleeping.setSearchCenter(chestPos);
        sleeping.tick(this);

        if (!sleeping.isSleeping()) {
            if (getNavigation().isIdle()) idleTicks++;
            else idleTicks = 0;

            if (getEquippedStack(EquipmentSlot.MAINHAND).isEmpty()) {
                equipStack(EquipmentSlot.MAINHAND, workTool());
                setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0F);
            }
            if (usesAutoDeposit() && !needsToDeposit && !cargo.isEmpty()
                    && (countItems(cargo) >= AUTO_DEPOSIT_THRESHOLD || idleTicks > AUTO_DEPOSIT_IDLE_TICKS)) {
                needsToDeposit = true;
            }
        }

        if (chestSearchCooldown > 0) chestSearchCooldown--;
        if (getHealth() <= 2.0F) reservationSystem.releaseAll(getUuid());
    }

    // ===== Rương nghề =====

    /** Trả về vị trí rương nghề gần nhất (có cache), null nếu không có. */
    @Nullable
    public BlockPos findWorkChest() {
        World world = getWorld();
        if (chestPos != null) {
            // NPC bị đưa đi quá xa (dắt dây, đẩy...) → quên rương cũ, tìm rương mới quanh chỗ đang đứng
            double far = 3.0 * chestSearchRadius();
            if (getBlockPos().getSquaredDistance(chestPos) > far * far) {
                chestPos = null;
                chestSearchCooldown = 0;
            }
        }
        if (chestPos != null) {
            // Chunk của rương chưa nạp: giữ nguyên vị trí, chờ nạp lại (giống Thợ mỏ cũ)
            if (!world.isChunkLoaded(chestPos)) return null;
            if (isOwnChest(world.getBlockEntity(chestPos))) return chestPos;
            chestPos = null;
            chestSearchCooldown = 0;
        }
        if (chestSearchCooldown > 0) return null;
        chestSearchCooldown = CHEST_SEARCH_COOLDOWN;

        int r = chestSearchRadius();
        int h = chestSearchHeight();
        BlockPos center = getBlockPos();
        double best = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.iterate(center.add(-r, -h, -r), center.add(r, h, r))) {
            if (!world.isChunkLoaded(pos) || !isOwnChest(world.getBlockEntity(pos))) continue;
            double distance = center.getSquaredDistance(pos);
            if (distance < best) {
                best = distance;
                chestPos = pos.toImmutable();
            }
        }
        if (chestPos == null) display.report(this, "Không tìm thấy rương " + professionName().toLowerCase());
        return chestPos;
    }

    /** Như findWorkChest nhưng trả về inventory của rương (API cũ của các goal). */
    @Nullable
    public Inventory findNearestChest() {
        BlockPos pos = findWorkChest();
        return pos != null && getWorld().getBlockEntity(pos) instanceof Inventory inventory ? inventory : null;
    }

    /** Quên rương cũ và tìm lại ngay (dùng khi triệu hồi từ token ở chỗ mới). */
    public void relocateWorkChest() {
        chestPos = null;
        chestSearchCooldown = 0;
        findWorkChest();
    }

    /** Đặt vị trí rương (chỉ dùng khi đọc dữ liệu NBT kiểu cũ). */
    protected void restoreChestPos(@Nullable BlockPos pos) {
        chestPos = pos;
    }

    private boolean isOwnChest(BlockEntity be) {
        // NPC chưa có chủ (dữ liệu rất cũ) vẫn dùng được mọi rương đúng loại như trước
        return chestType().isInstance(be)
                && (ownerUUID == null || ((ProfessionChestBlockEntity) be).isAccessibleBy(ownerUUID));
    }

    /** Tìm nơi nộp hàng; mặc định là rương nghề của NPC. */
    @Nullable
    public BlockPos findDepositTarget(DeliveryTarget delivery) {
        BlockPos chest = findWorkChest();
        if (chest == null) return null;
        return delivery.find(this, chest, chestType(), cargo, stack -> true,
                chestSearchRadius(), chestSearchHeight(), display);
    }

    /** Block entity tại vị trí nộp hàng còn hợp lệ không. */
    public boolean isDepositTarget(BlockEntity be) {
        return isOwnChest(be);
    }

    // ===== Đặt chỗ (reservation) =====

    protected GlobalReservationSystem reservations() {
        return reservationSystem;
    }

    public boolean reserveChest(BlockPos pos) {
        return reservationSystem.tryReserve(pos, getUuid(), "WORK_CHEST");
    }

    public void releaseChest(BlockPos pos) {
        reservationSystem.release(pos, getUuid(), "WORK_CHEST");
    }

    public boolean isChestReserved(BlockPos pos) {
        return reservationSystem.isReservedByOthers(pos, getUuid(), "WORK_CHEST");
    }

    // ===== Tương tác & lưu trữ =====

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        // Xác NPC còn hiện ~1 giây khi chết: chặn thu hồi để không nhân đôi token
        if (!isAlive()) return ActionResult.PASS;
        ItemStack held = player.getStackInHand(hand);
        if (!(player.isSneaking() || held.getItem() instanceof ArmorItem || held.isFood())) {
            return ActionResult.FAIL;
        }
        ActionResult handled = equip.interactMob(this, player, ownerUUID, hand, supplies, tokenItem());
        if (!handled.equals(ActionResult.FAIL)) return handled;
        return super.interactMob(player, hand);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        display.writeNbt(nbt);
        if (ownerUUID != null) nbt.putUuid("OwnerUUID", ownerUUID);
        if (chestPos != null) {
            nbt.put("WorkChest", NbtHelper.fromBlockPos(chestPos));
            nbt.putString("WorkChestDimension", getWorld().getRegistryKey().getValue().toString());
        }
        nbt.put("Cargo", cargo.toNbtList());
        nbt.put("Supplies", supplies.toNbtList());
        writeWorkerNbt(nbt);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        display.readNbt(nbt);
        if (nbt.containsUuid("OwnerUUID")) ownerUUID = nbt.getUuid("OwnerUUID");
        // Chỉ nhớ rương nếu cùng chiều không gian
        chestPos = nbt.contains("WorkChest")
                && getWorld().getRegistryKey().getValue().toString().equals(nbt.getString("WorkChestDimension"))
                ? NbtHelper.toBlockPos(nbt.getCompound("WorkChest")) : null;
        String legacyKey = legacyCargoKey();
        if (nbt.contains("Cargo")) cargo.readNbtList(nbt.getList("Cargo", 10));
        else if (legacyKey != null && nbt.contains(legacyKey)) cargo.readNbtList(nbt.getList(legacyKey, 10));
        if (nbt.contains("Supplies")) supplies.readNbtList(nbt.getList("Supplies", 10));
        needsToDeposit = !cargo.isEmpty();
        readWorkerNbt(nbt);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!getWorld().isClient) {
            sleeping.cancelMovingToBed(this);
            sleeping.wakeUp(this);
        }
        reservationSystem.releaseAll(getUuid());
        super.remove(reason);
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        reservationSystem.releaseAll(getUuid());
        super.onDeath(damageSource);
        if (getWorld().isClient) return;
        dropInventory(cargo);
        dropInventory(supplies);
        dropWorkerItems();
        dropStack(new ItemStack(tokenItem()));
    }

    protected void dropInventory(Inventory inventory) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) dropStack(stack);
        }
        inventory.clear();
    }

    public static int countItems(Inventory inventory) {
        int total = 0;
        for (int i = 0; i < inventory.size(); i++) total += inventory.getStack(i).getCount();
        return total;
    }

    /** Túi đồ không còn ô trống. */
    public boolean isInventoryFull() {
        for (int i = 0; i < cargo.size(); i++) {
            if (cargo.getStack(i).isEmpty()) return false;
        }
        return true;
    }

    /**
     * Tên mặc định khi chưa có custom name. KHÔNG override getName(): nhãn trên đầu
     * dùng getDisplayName() → getName(), override sẽ che mất dòng máu/đói của NpcDisplayComponent.
     */
    @Override
    protected Text getDefaultName() {
        return Text.literal(professionName());
    }

    // ===== Mục tiêu không tới được =====

    /** Tạm bỏ qua entity (cừu, vật phẩm...) không tới được, tránh nhắm lại liên tục. */
    public void skipTarget(Entity target, int ticks) {
        skippedTargets.put(target.getUuid(), getWorld().getTime() + ticks);
    }

    public boolean isSkipped(Entity target) {
        long now = getWorld().getTime();
        skippedTargets.values().removeIf(until -> until <= now);
        return skippedTargets.containsKey(target.getUuid());
    }

    // ===== Getters / setters =====

    public SimpleInventory getCargo() { return cargo; }
    @Nullable public BlockPos getChestPos() { return chestPos; }
    public boolean needsToDeposit() { return needsToDeposit; }
    public void setNeedsToDeposit(boolean value) { needsToDeposit = value; }
    public boolean isIdleFor(int ticks) { return idleTicks > ticks; }
    public void resetIdle() { idleTicks = 0; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public void setOwnerUUID(UUID uuid) { this.ownerUUID = uuid; }
}
