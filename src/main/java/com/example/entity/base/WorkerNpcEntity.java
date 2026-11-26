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
    /** Số tick liên tục không có goal việc nào chạy (cập nhật mỗi 10 tick). */
    private int workIdleTicks;
    private static final int IDLE_BEFORE_HOME_TICKS = 100;
    private static final double IDLE_HOME_RADIUS = 20.0;
    private static final double IDLE_HOME_ARRIVE = 6.0;
    private int chestSearchCooldown;
    /** Đang dùng chung rương neo của NPC khác (không lưu NBT, tính lại khi kiểm tra rương). */
    private boolean sharedChest;
    private long sharedRecheckAt;
    private static final int SHARED_RECHECK_TICKS = 200; // đang dùng chung: 10 giây tìm rương trống một lần

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
        // Rảnh (không goal việc nào chạy ≥ 5s) mà ở xa rương nghề quá 20 block → về rương; đang làm thì không ép
        this.goalSelector.add(8, new com.example.ai.ReturnToAnchorGoal(this, this::getChestPos,
                () -> !sleeping.isSleeping() && workIdleTicks >= IDLE_BEFORE_HOME_TICKS && !isInWorkArea(),
                IDLE_HOME_RADIUS, IDLE_HOME_ARRIVE, 1.0));
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
        if (age % 2 == 0 && !sleeping.isSleeping()) doorOpener.tick(this, canOpenFenceGates());
        if (age % 10 == 0) {
            workIdleTicks = NpcDisplayComponent.hasWorkGoal(goalSelector) ? 0 : Math.min(workIdleTicks + 10, 20 * 60);
        }
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

        // Thân bị block đè (cây mọc trùm lên người...) → xử lý trước mọi việc khác để không ngạt thở
        if (age % 10 == 0 && com.example.helper.NpcStuckHelper.escapeIfStuck(
                this, canChopBlockingTrees(), canChopBlockingTrees() ? cargo : null)) {
            display.report(this, "Bị kẹt trong block, đang thoát ra");
        }
    }

    /** Được chặt gỗ/lá đang đè lên người khi bị kẹt (Tiều phu). NPC khác chỉ dịch sang ô trống. */
    protected boolean canChopBlockingTrees() {
        return false;
    }

    /**
     * Tính đường tới target; chấp nhận khi đường tới đích hoặc điểm cuối nằm trong tầm thao tác
     * (reachSq, tính từ đáy điểm đứng tới tâm block target). Null nếu không tới được.
     */
    @Nullable
    public net.minecraft.entity.ai.pathing.Path findReachablePath(BlockPos target, double reachSq) {
        var path = getNavigation().findPathTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1);
        if (path == null) return null;
        if (path.reachesTarget()) return path;
        var end = path.getEnd();
        return end != null && net.minecraft.util.math.Vec3d.ofBottomCenter(end.getBlockPos())
                .squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(target)) <= reachSq ? path : null;
    }

    // ===== Rương nghề =====

    /**
     * Rương neo của NPC (1 NPC ↔ 1 rương, ghi cả trong rương lẫn NPC).
     * - Rương neo còn → dùng; nếu đang phải dùng chung thì định kỳ tìm rương trống để chuyển sang.
     * - Rương bị phá / mất → tìm rương đúng nghề của chủ mà chưa NPC nào neo (gần nhất) và neo vào.
     * - Không còn rương trống → tạm dùng chung rương đúng nghề gần nhất cho tới khi có rương mới.
     */
    @Nullable
    public BlockPos findWorkChest() {
        World world = getWorld();
        if (chestPos != null) {
            // NPC bị đưa đi quá xa (dắt dây, đẩy...) → quên rương cũ, tìm rương mới quanh chỗ đang đứng
            double far = chestForgetDistance();
            if (getBlockPos().getSquaredDistance(chestPos) > far * far) {
                unbindChest();
                chestSearchCooldown = 0;
            }
        }
        if (chestPos != null) {
            // Chunk của rương chưa nạp: giữ nguyên vị trí, chờ nạp lại (giống Thợ mỏ cũ)
            if (!world.isChunkLoaded(chestPos)) return null;
            BlockEntity be = world.getBlockEntity(chestPos);
            if (isOwnChest(be)) {
                ProfessionChestBlockEntity chest = (ProfessionChestBlockEntity) be;
                if (chest.isFreeFor(getUuid())) {
                    chest.bind(getUuid());
                    sharedChest = false;
                } else {
                    sharedChest = true;
                    if (world.getTime() >= sharedRecheckAt) {
                        sharedRecheckAt = world.getTime() + SHARED_RECHECK_TICKS;
                        BlockPos free = searchChest(true);
                        if (free != null) bindChest(free);
                    }
                }
                return chestPos;
            }
            chestPos = null; // rương bị phá hoặc đổi chỗ
            sharedChest = false;
            chestSearchCooldown = 0;
        }
        if (chestSearchCooldown > 0) return null;
        chestSearchCooldown = CHEST_SEARCH_COOLDOWN;

        BlockPos free = searchChest(true);
        if (free != null) {
            bindChest(free);
            return chestPos;
        }
        BlockPos shared = searchChest(false);
        if (shared != null) {
            chestPos = shared; // dùng chung, KHÔNG neo; vẫn tìm rương trống định kỳ
            sharedChest = true;
            sharedRecheckAt = world.getTime() + SHARED_RECHECK_TICKS;
            return chestPos;
        }
        display.report(this, "Không tìm thấy rương " + professionName().toLowerCase());
        return null;
    }

    /** Rương đúng nghề của chủ gần nhất quanh NPC; freeOnly = chỉ rương chưa NPC khác neo. */
    @Nullable
    private BlockPos searchChest(boolean freeOnly) {
        World world = getWorld();
        int r = chestSearchRadius();
        int h = chestSearchHeight();
        BlockPos center = getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        // Duyệt block entity theo chunk thay vì từng block
        for (int cx = (center.getX() - r) >> 4; cx <= (center.getX() + r) >> 4; cx++) {
            for (int cz = (center.getZ() - r) >> 4; cz <= (center.getZ() + r) >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                for (BlockEntity be : world.getChunk(cx, cz).getBlockEntities().values()) {
                    BlockPos pos = be.getPos();
                    if (Math.abs(pos.getX() - center.getX()) > r || Math.abs(pos.getZ() - center.getZ()) > r
                            || Math.abs(pos.getY() - center.getY()) > h || !isOwnChest(be)) continue;
                    if (freeOnly && !((ProfessionChestBlockEntity) be).isFreeFor(getUuid())) continue;
                    double distance = center.getSquaredDistance(pos);
                    if (distance < bestDist) {
                        bestDist = distance;
                        best = pos.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    private void bindChest(BlockPos pos) {
        if (!pos.equals(chestPos)) unbindChest();
        chestPos = pos.toImmutable();
        sharedChest = false;
        if (getWorld().getBlockEntity(pos) instanceof ProfessionChestBlockEntity chest) chest.bind(getUuid());
        display.clearReport();
    }

    /** Gỡ neo khỏi rương hiện tại (nếu rương còn và đúng là NPC này neo) rồi quên rương. */
    private void unbindChest() {
        if (chestPos != null && getWorld().isChunkLoaded(chestPos)
                && getWorld().getBlockEntity(chestPos) instanceof ProfessionChestBlockEntity chest) {
            chest.unbind(getUuid());
        }
        chestPos = null;
        sharedChest = false;
    }

    /** Đang tạm dùng chung rương với NPC khác (chưa có rương riêng). */
    public boolean isSharingChest() {
        return sharedChest;
    }

    // ===== Cửa gỗ / cổng rào =====
    private final com.example.ai.pathing.DoorGateOpener doorOpener = new com.example.ai.pathing.DoorGateOpener();

    /** Được mở cổng rào gỗ (mặc định có). Thợ chăn nuôi làm việc qua hàng rào nên không mở để vật nuôi không xổng. */
    protected boolean canOpenFenceGates() {
        return true;
    }

    @Override
    protected net.minecraft.entity.ai.pathing.EntityNavigation createNavigation(World world) {
        // Gọi trong constructor (trước khi field lớp con khởi tạo): canOpenFenceGates() chỉ được trả hằng số
        boolean gates = canOpenFenceGates();
        net.minecraft.entity.ai.pathing.MobNavigation navigation = new net.minecraft.entity.ai.pathing.MobNavigation(this, world) {
            @Override
            protected net.minecraft.entity.ai.pathing.PathNodeNavigator createPathNodeNavigator(int range) {
                this.nodeMaker = new com.example.ai.pathing.GateAwareNodeMaker(gates);
                this.nodeMaker.setCanEnterOpenDoors(true);
                return new net.minecraft.entity.ai.pathing.PathNodeNavigator(this.nodeMaker, range);
            }
        };
        navigation.setCanPathThroughDoors(true); // tính đường xuyên cửa gỗ (và cổng rào) đang đóng
        return navigation;
    }

    /** Đang đứng trong khu làm việc riêng của nghề (vd. mảnh ruộng) → rảnh cũng không bị kéo về rương. */
    protected boolean isInWorkArea() {
        return false;
    }

    /** Đi xa rương neo hơn khoảng này thì quên rương (bị dắt/đẩy đi nơi khác). */
    protected double chestForgetDistance() {
        return 3.0 * chestSearchRadius();
    }

    /** Có được dùng rương này không (nộp hàng dự phòng): đúng nghề, của chủ, không phải rương NPC khác đã neo. */
    public boolean canUseChest(BlockEntity be) {
        return isOwnChest(be) && (be.getPos().equals(chestPos)
                || ((ProfessionChestBlockEntity) be).isFreeFor(getUuid()));
    }

    /** Như findWorkChest nhưng trả về inventory của rương (API cũ của các goal). */
    @Nullable
    public Inventory findNearestChest() {
        BlockPos pos = findWorkChest();
        return pos != null && getWorld().getBlockEntity(pos) instanceof Inventory inventory ? inventory : null;
    }

    /** Quên rương cũ và tìm lại ngay (dùng khi triệu hồi từ token ở chỗ mới). */
    public void relocateWorkChest() {
        unbindChest();
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
        // Chết / thu hồi bằng token → trả rương cho NPC khác; dỡ chunk thì giữ neo
        if (!getWorld().isClient && reason.shouldDestroy()) unbindChest();
        if (!getWorld().isClient) doorOpener.closeAll(this);
        super.remove(reason);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        // Tên lạc của Binh lính cùng chủ: không mất máu, không hoảng chạy
        if (com.example.entity.SoldierNPCEntity.isFriendlyFire(source, ownerUUID)) return false;
        return super.damage(source, amount);
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        reservationSystem.releaseAll(getUuid());
        if (!getWorld().isClient) {
            // Giáp người chơi mặc cho NPC rơi 100% (vanilla chỉ 8.5%). Phải làm TRƯỚC super.onDeath
            // và xoá khỏi slot, nếu không vanilla tung xúc xắc rơi thêm lần nữa → nhân đôi giáp.
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (!slot.isArmorSlot()) continue;
                ItemStack armor = getEquippedStack(slot);
                if (!armor.isEmpty()) {
                    dropStack(armor.copy());
                    equipStack(slot, ItemStack.EMPTY);
                }
            }
        }
        super.onDeath(damageSource);
        if (getWorld().isClient) return;
        // Chết là mất NPC: KHÔNG rơi token (token chỉ có khi thu hồi bằng Shift + chuột phải).
        // Đồ đang mang vẫn rơi ra để người chơi không mất tài nguyên.
        dropInventory(cargo);
        dropInventory(supplies);
        dropWorkerItems();
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
    /** Nhãn việc đang làm cho Sổ quản lý làng (goalSelector là protected). */
    public String overviewLabel(@Nullable String issue) { return NpcDisplayComponent.workLabel(this, goalSelector, issue); }
    public int suppliesFoodCount() { return display.getTotalFoodCount(supplies); }
    public void setOwnerUUID(UUID uuid) { this.ownerUUID = uuid; }
}
