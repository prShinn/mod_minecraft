package com.example.entity;

import com.example.ai.soldier.*;
import com.example.entity.base.CombatExperienceComponent;
import com.example.registry.ModItems;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Arm;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SoldierNPCEntity extends PathAwareEntity {
    public final CombatExperienceComponent combat = new CombatExperienceComponent();
    private double damageDealtThisAttack = 0;

    // ===== CONSTANTS =====
    public static final int MAX_HUNGER = 20;
    private static final int FOOD_TICK_INTERVAL = 1200; // 1p
    private static final int SHOW_NAME_DURATION = 20; // 1 giây
    private static final double VIEW_DISTANCE = 6.0;
    public static final float FOLLOW_DISTANCE = 48f; // Lang thang tối đa
    public static final float TELEPORT_DISTANCE = 60f; // Quá xa thì teleport
    private final float teleportDistance = 0;
    private static final int PLAYER_SEARCH_INTERVAL = 30; // 1 giây
    // 1/10 xác suất
    private static final int ARMOR_DURABILITY_CHANCE = 20; // 1/20 xác suất
    private String nameNpc = "";
    public ModeNpc.ModeMove moveMode = ModeNpc.ModeMove.WANDER;

    // ===== OWNER & FOLLOW =====
    private UUID ownerUUID;
    private final com.example.entity.base.NpcSleepingComponent sleeping = new com.example.entity.base.NpcSleepingComponent();

    @Override
    public void remove(RemovalReason reason) {
        if (!getWorld().isClient) {
            sleeping.cancelMovingToBed(this);
            sleeping.wakeUp(this);
        }
        super.remove(reason);
    }
    private UUID followPlayerUUID;

    // ===== DISPLAY =====
    private int nameVisibleTicks = 0;
    private int playerSearchCooldown = 0;

    // ===== FOOD & HEALTH =====
    private int hunger = MAX_HUNGER;
    private int foodTickCooldown = 0;
    private int eatCooldown = 0;

    // ===== HEALING =====
    private float pendingHeal = 0.0F;
    private int healTickCooldown = 0;

    // ===== INVENTORY =====
    public final SimpleInventory foodInventory = new SimpleInventory(9);
    /** Chiến lợi phẩm nhặt sau khi giết quái, mang về cất vào Rương binh lính. */
    public final SoldierLoot loot = new SoldierLoot();

    public ModeNpc.ModeMove getMoveMode() {
        int id = this.dataTracker.get(MOVE_MODE);
        return ModeNpc.ModeMove.values()[MathHelper.clamp(id, 0, ModeNpc.ModeMove.values().length - 1)];
    }

    public void setMoveMode(ModeNpc.ModeMove moveMode) {
        this.moveMode = moveMode;
        this.dataTracker.set(MOVE_MODE, moveMode.ordinal());
        guardAnchor = null; // đổi chế độ → lấy chỗ đang đứng làm gốc, tìm lại điểm neo quanh đó
        guardHome = moveMode == ModeNpc.ModeMove.FOLLOW ? null : getBlockPos().toImmutable();
        patrolDutyCheckedAt = Long.MIN_VALUE; // đổi chế độ → tính lại tuyến ngay
        patrolHint = null;
        guardAnchorCooldown = 0;
    }

    // ===== TUYẾN TUẦN TRA =====
    /** Cờ đang nhắm tới / vừa nhắm (không lưu NBT): giữ tuyến khi bị ngắt hoặc đuổi quái ra xa. */
    @Nullable
    private net.minecraft.util.math.BlockPos patrolHint;

    @Nullable
    public net.minecraft.util.math.BlockPos getPatrolHint() {
        return patrolHint;
    }

    public void setPatrolHint(@Nullable net.minecraft.util.math.BlockPos hint) {
        patrolHint = hint;
    }

    // ===== TUYẾN TUẦN TRA (cache) =====
    private long patrolDutyCheckedAt = Long.MIN_VALUE;
    private boolean patrolDuty;

    /** Kết quả "có tuyến tuần tra hợp lệ", tính lại tối đa 1 lần mỗi giây. */
    public boolean cachedPatrolDuty(java.util.function.BooleanSupplier compute) {
        long now = getWorld().getTime();
        if (now < patrolDutyCheckedAt || now - patrolDutyCheckedAt >= 20) {
            patrolDuty = compute.getAsBoolean();
            patrolDutyCheckedAt = now;
        }
        return patrolDuty;
    }

    // ===== ĐỨNG CANH GÁC =====
    /** Bán kính đi lại quanh điểm gác; ra xa hơn thì quay về. */
    public static final int STAND_GUARD_RADIUS = 3;

    /** Đang ở chế độ Đứng canh gác và có điểm gác. */
    public boolean isStandingGuard() {
        return getMoveMode() == ModeNpc.ModeMove.STAND && guardAnchor != null;
    }

    // ===== ĐIỂM NEO CANH GÁC =====
    /** Lang thang xa điểm neo hơn khoảng này thì quay về điểm neo một lần rồi lang thang lại. */
    public static final double GUARD_RETURN_RADIUS = 38.0;
    private static final double GUARD_ARRIVE_RADIUS = 4.0;
    /**
     * Điểm neo: Rương binh lính / giường / rương nghề gần nhất (40 block quanh gốc);
     * không có thì chính là gốc {@link #guardHome}. Luôn có khi không theo chủ.
     */
    @Nullable
    private net.minecraft.util.math.BlockPos guardAnchor;
    /** Gốc canh gác: chỗ lính đứng khi chuyển sang chế độ không theo chủ (lưu NBT). */
    @Nullable
    private net.minecraft.util.math.BlockPos guardHome;
    private int guardAnchorCooldown;

    @Nullable
    public net.minecraft.util.math.BlockPos getGuardAnchor() {
        return guardAnchor;
    }

    /** Đang canh tại điểm neo: không theo chủ, không đang giữ tuyến tuần tra, và có điểm neo. */
    public boolean hasGuardPost() {
        return getMoveMode() != ModeNpc.ModeMove.FOLLOW && guardAnchor != null
                && !SoldierPatrolGoal.isOnPatrolDuty(this);
    }

    /** Bị giữ tại chỗ (tuyến tuần tra hoặc điểm neo) → không chạy theo / dịch chuyển tới chủ. */
    public boolean isHeldAtPost() {
        return SoldierPatrolGoal.isOnPatrolDuty(this) || hasGuardPost();
    }

    private void updateGuardAnchor() {
        if (guardAnchorCooldown-- > 0) return;
        if (getMoveMode() == ModeNpc.ModeMove.FOLLOW) {
            guardAnchor = null;
            guardAnchorCooldown = 40;
            return;
        }
        // Tìm quanh gốc cố định để lính lang thang/đuổi quái ra xa không làm trôi căn cứ
        if (guardHome == null) guardHome = getBlockPos().toImmutable(); // lính cũ chưa có gốc
        if (getMoveMode() == ModeNpc.ModeMove.STAND) {
            // Đứng canh gác: neo đúng chỗ được giao, không tìm rương/giường
            guardAnchor = guardHome;
            guardAnchorCooldown = 200;
            return;
        }
        net.minecraft.util.math.BlockPos found = GuardPost.find(this, guardHome);
        guardAnchor = found != null ? found : guardHome;
        guardAnchorCooldown = 200;
    }

    public static final TrackedData<Integer> MOVE_MODE =
            DataTracker.registerData(SoldierNPCEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public String getNameNpc() {
        return nameNpc;
    }

    public void setNameNpc(String nameNpc) {
        this.nameNpc = nameNpc;
    }

    // ===== CONSTRUCTOR =====
    public SoldierNPCEntity(EntityType<? extends SoldierNPCEntity> type, World world) {
        super(type, world);
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    // ===== ATTRIBUTES =====
    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20D)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 2.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(MOVE_MODE, ModeNpc.ModeMove.WANDER.ordinal());
    }

    // ===== AI GOALS =====
    @Override
    protected void initGoals() {
        super.initGoals();
        // ⚠️ QUAN TRỌNG: Clear goals cũ trước
        this.goalSelector.clear(goal -> true);
        this.targetSelector.clear(goal -> true);
        this.goalSelector.add(0, new SwimGoal(this)); // ko chet duoi
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.2, true) {
            @Override
            public boolean canStart() {
                // chỉ start nếu cầm sword
                if (SoldierNPCEntity.this.getMainHandStack().getItem() instanceof SwordItem) {
                    setNameNpc("Chiến binh");
                    return super.canStart();
                }
                return false;
            }

            @Override
            public boolean shouldContinue() {
                // Đổi sang cung/nỏ giữa trận → nhường goal tầm xa (cùng priority, không tự chen được)
                return SoldierNPCEntity.this.getMainHandStack().getItem() instanceof SwordItem && super.shouldContinue();
            }
        }); // binh linh
        this.goalSelector.add(2, new SoldierBowGoal(this) {
            @Override
            public boolean canStart() {
                // chỉ start nếu cầm cung
                if (SoldierNPCEntity.this.getMainHandStack().getItem() instanceof BowItem) {
                    setNameNpc("Cung thủ");
                    return super.canStart();
                }
                return false;
            }
        }); // cung thu
        this.goalSelector.add(2, new SoldierCrossbowGoal(this) {
            @Override
            public boolean canStart() {
                // chỉ start nếu cầm nỏ
                if (SoldierNPCEntity.this.getMainHandStack().getItem() instanceof CrossbowItem) {
                    setNameNpc("Xạ tiễn");
                    return super.canStart();
                }
                return false;
            }
        }); // cung thu
        // Thứ tự ưu tiên: đánh quái (2) → né nguy hiểm / chữa đồng đội (3) → ăn uống, nhặt đồ (4)
        // → theo chủ (5) → về chủ, cất chiến lợi phẩm (6) → TUẦN TRA (7) → đi dạo, nhìn quanh (8)
        this.goalSelector.add(3, new EscapeDangerGoal(this, 1.4));
        this.goalSelector.add(3, new HealAllyGoal(this) {
            @Override
            public boolean canStart() {
                // chỉ start nếu cầm riu
                if(SoldierNPCEntity.this.getMainHandStack().getItem() instanceof AxeItem){
                    setNameNpc("Bác sĩ");
                    return super.canStart();
                }
                return false;
            }
        });
        this.goalSelector.add(4, new com.example.ai.foodChest.FindAndEatFoodGoal(this, () -> foodInventory, 24, this::shouldEat, this::feedFromChest));
        // nhặt chiến lợi phẩm quanh chỗ quái vừa bị lính giết (ngoài giao tranh)
        this.goalSelector.add(4, new CollectLootGoal(this));
        this.goalSelector.add(5, new FollowOwnerLikeGoal(this, 1.3D));
        this.goalSelector.add(6, new ReturnToPlayerGoal(this));
        // mang chiến lợi phẩm về cất vào Rương binh lính của chủ
        this.goalSelector.add(6, new DepositLootGoal(this));
        // đi tuần giữa các Cờ Tuần Tra (chế độ Đi tuần tra): sau ăn uống / theo chủ / cất chiến lợi phẩm
        this.goalSelector.add(7, new SoldierPatrolGoal(this));
// nhin player
        this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        // đi lang thang xa
        // canh gác: lang thang quá 38 block khỏi điểm neo → quay về điểm neo 1 lần rồi lang thang lại
        // (priority 7 để chen được WanderAroundFar; ngoài giao tranh, không ngủ, không giữ tuyến tuần tra)
        this.goalSelector.add(7, new com.example.ai.ReturnToAnchorGoal(this, this::getGuardAnchor,
                () -> getTarget() == null && getPose() != net.minecraft.entity.EntityPose.SLEEPING && hasGuardPost()
                        && getMoveMode() != ModeNpc.ModeMove.STAND,
                GUARD_RETURN_RADIUS, GUARD_ARRIVE_RADIUS, 1.0D));
        // Đứng canh gác: rời điểm gác quá 3 block (đánh quái, nhặt đồ...) → hết việc thì quay về ngay
        this.goalSelector.add(7, new com.example.ai.ReturnToAnchorGoal(this, this::getGuardAnchor,
                () -> getTarget() == null && getPose() != net.minecraft.entity.EntityPose.SLEEPING && isStandingGuard(),
                STAND_GUARD_RADIUS, 1.0D, 1.1D));
        this.goalSelector.add(8, new WanderAroundFarGoal(this, 1.0D) {
            // Đứng canh gác thì không đi xa: chỉ bước quanh điểm gác (StandGuardWanderGoal)
            @Override
            public boolean canStart() {
                return !isStandingGuard() && super.canStart();
            }

            @Override
            public boolean shouldContinue() {
                return !isStandingGuard() && super.shouldContinue();
            }
        });
        this.goalSelector.add(8, new com.example.ai.soldier.StandGuardWanderGoal(this, STAND_GUARD_RADIUS));
        // đi xung quanh gần (phat trien sau)
//        this.goalSelector.add(8, new WanderAroundGoal(this, 0.8));
        // nhìn xung quanh
        this.goalSelector.add(8, new LookAroundGoal(this));
        // đánh quái
        this.targetSelector.add(1, new DefendPlayerGoal(this, 32.0F)); // bảo vệ
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, HostileEntity.class, true, target -> {
            if (!hasCombatWeapon() || isTargetIgnored(target)) return false; // bác sĩ/tay không không khoá quái
            // Mở rộng khoảng cách check quanh npc hoặc player: 25 block
            PlayerEntity owner = this.getOwner();
            return this.squaredDistanceTo(target) <= 26 * 26
                    || (owner != null && owner.squaredDistanceTo(target) <= 25 * 25) || isThreateningVillager(target);

        })); // đánh hostile
        // đánh trả khi bị tấn công (PlayerEntity.class = loại KHÔNG đánh trả); không vũ khí thì để EscapeDanger bỏ chạy
        this.targetSelector.add(2, new RevengeGoal(this, PlayerEntity.class) {
            @Override
            public boolean canStart() {
                LivingEntity attacker = getAttacker();
                // Kẻ vừa bị bỏ vì không đánh được (vd xương bắn từ vách) không được khoá lại mỗi phát trúng
                return hasCombatWeapon() && (attacker == null || !isTargetIgnored(attacker)) && super.canStart();
            }
        });

    }

    // ===== TICK =====
    @Override
    public void tick() {
        super.tick();
        if (this.getWorld() == null || this.getWorld().isClient) return;// check ở server
        // Thân bị block đè (cây mọc trùm lên...) → dịch sang ô trống gần nhất, không phá công trình
        if (this.age % 10 == 0) com.example.helper.NpcStuckHelper.escapeIfStuck(this, false, null);
        updateGuardAnchor();
        // Chủ đang chết: getOwner() trả null (không đi theo xác) nhưng GIỮ ownerUUID để hồi sinh vẫn là chủ

        double currentMaxHealth = combat.getCurrentMaxHealth();
        if (this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).getBaseValue()
                != currentMaxHealth) {
            this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)
                    .setBaseValue(currentMaxHealth);

            // Heal lên max khi level up
            if (this.getHealth() < currentMaxHealth) {
                this.setHealth((float) currentMaxHealth);
            }
        }

        dropStuckTarget();
        // Display management
        updateNameDisplay();
        // Food system
        sleeping.tick(this);
        handleFoodSystem();
        // Healing system
        handleGradualHeal();
    }

    // ===== MỤC TIÊU KẸT =====
    /** Có mục tiêu quá lâu mà không goal tấn công nào chạy (không tới được, sai vũ khí) → bỏ để theo chủ/đi tuần tiếp. */
    private static final int STUCK_TARGET_TICKS = 100;
    /** Mục tiêu vừa bỏ bị bỏ qua 30s, tránh khoá lại ngay rồi đơ tiếp. */
    private static final int IGNORE_TARGET_TICKS = 600;
    private int noAttackTicks;
    @Nullable
    private UUID ignoredTarget;
    private long ignoreTargetUntil;

    public boolean hasCombatWeapon() {
        Item item = getMainHandStack().getItem();
        return item instanceof SwordItem || item instanceof BowItem || item instanceof CrossbowItem;
    }

    public boolean isTargetIgnored(LivingEntity target) {
        return target.getUuid().equals(ignoredTarget) && getWorld().getTime() < ignoreTargetUntil;
    }

    private void dropStuckTarget() {
        LivingEntity target = getTarget();
        boolean attacking = target != null && goalSelector.getRunningGoals().anyMatch(e ->
                e.getGoal() instanceof MeleeAttackGoal
                        || e.getGoal() instanceof com.example.ai.soldier.SoldierBowGoal
                        || e.getGoal() instanceof com.example.ai.soldier.SoldierCrossbowGoal);
        if (target == null || attacking) {
            noAttackTicks = 0;
            return;
        }
        if (++noAttackTicks < STUCK_TARGET_TICKS) return;
        noAttackTicks = 0;
        ignoredTarget = target.getUuid();
        ignoreTargetUntil = getWorld().getTime() + IGNORE_TARGET_TICKS;
        setTarget(null);
    }

    // ===== FOOD SYSTEM =====
    private void handleFoodSystem() {
        // Decrease hunger over time
        if (foodTickCooldown > 0) {
            foodTickCooldown--;
        } else {
            decreaseHunger();
            foodTickCooldown = FOOD_TICK_INTERVAL;
        }
        if (eatCooldown > 0) {
            eatCooldown--;
        }
        if (shouldEat()) {
            this.npcEatFood();
        }
    }

    private void npcEatFood() {
        for (int i = 0; i < this.foodInventory.size(); i++) {
            ItemStack stack = this.foodInventory.getStack(i);
            if (stack.isEmpty() || !stack.isFood()) continue;
            FoodComponent food = stack.getItem().getFoodComponent();
            if (food == null) continue;

            // Tăng food/hunger
            hunger = Math.min(MAX_HUNGER, hunger + food.getHunger());

            stack.decrement(1);
            eatCooldown = 100; // 5 giây cooldown
            return;
        }
    }

    private void decreaseHunger() {
        if (hunger > 0) {
            hunger--;
        } else {
            // Starve damage
            this.damage(this.getWorld().getDamageSources().starve(), 1.0F);
        }
    }

    private void feedFromChest(int nutrition) {
        hunger = Math.min(MAX_HUNGER, hunger + nutrition);
        eatCooldown = 100;
    }

    private boolean shouldEat() {
        // Chỉ ăn khi food thấp hơn 50%
        return eatCooldown <= 0 && hunger <= MAX_HUNGER * 0.5f;
    }

    // ===== HEALING SYSTEM =====
    public void requestHeal(float amount) {
        if (amount <= 0) return;
        this.pendingHeal += amount;
    }

    private void handleGradualHeal() {
        // ===== ƯU TIÊN HEAL TỪ MEDIC (KHÔNG TỐN HUNGER) =====
        if (pendingHeal > 0 && this.getHealth() < this.getMaxHealth()) {
            float heal = Math.min(pendingHeal, this.getMaxHealth() - this.getHealth());
            this.heal(heal);
            pendingHeal -= heal;
            return;
        }
        if (this.getHealth() >= this.getMaxHealth()) return;
        if (hunger <= 0) {
            hunger = 0;
            return;
        }
        if (healTickCooldown > 0) {
            healTickCooldown--;
            return;
        }
        float healAmount = 2.0F; // 0.5 tim
        this.heal(healAmount);
        hunger--;                // 🔥 Heal tốn Hunger
        healTickCooldown = 30;   // 1.5 giây
    }

    // ===== DISPLAY SYSTEM =====
    private void updateNameDisplay() {
        // Only search for player periodically
        if (playerSearchCooldown > 0) {
            playerSearchCooldown--;
        } else {
            if (isPlayerNearby()) {
                this.setCustomNameVisible(true);
                nameVisibleTicks = SHOW_NAME_DURATION;
                updateNameText();
            }
            playerSearchCooldown = PLAYER_SEARCH_INTERVAL;
        }
        // Update name visibility
        if (nameVisibleTicks > 0) {
            nameVisibleTicks--;
            if (this.age % 10 == 0) updateNameText();
        } else {
            this.setCustomNameVisible(false);
        }
    }

    private boolean isPlayerNearby() {
        PlayerEntity player = this.getWorld().getClosestPlayer(this.getX(), this.getY(), this.getZ(), VIEW_DISTANCE, false);

        if (player != null) {
            followPlayerUUID = player.getUuid();
            return true;
        }
        return false;
    }

    private void updateNameText() {
        int hp = Math.round(this.getHealth());
        int maxHp = Math.round(this.getMaxHealth());
        int foodCount = getTotalFoodCount();
        MutableText name = Text.literal(getNameNpc()).formatted(Formatting.WHITE)
                .append(Text.literal(" [Cấp " + combat.getLevel() + "] ").formatted(Formatting.AQUA))
                .append(Text.literal(" [" + hp + "/" + maxHp + "] ").formatted(Formatting.GREEN))
                .append(Text.literal("🍖[" + hunger + "/" + MAX_HUNGER + "] x" + foodCount).formatted(Formatting.GOLD));

        this.setCustomName(name.append(Text.literal(" • "
                + com.example.entity.base.NpcDisplayComponent.workLabel(this, goalSelector, null))
                .formatted(Formatting.YELLOW)));
    }

    public int getTotalFoodCount() {
        int total = 0;
        for (int i = 0; i < foodInventory.size(); i++) {
            ItemStack stack = foodInventory.getStack(i);
            if (!stack.isEmpty() && stack.isFood()) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // ===== DEATH =====
    @Override
    public void onDeath(DamageSource damageSource) {
        if (!this.getWorld().isClient) {
            // Rơi 100% trang bị + túi đồ ăn rồi xoá khỏi NPC, để vanilla không rơi thêm lần nữa (tránh nhân đồ)
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = this.getEquippedStack(slot);
                if (!stack.isEmpty()) {
                    this.dropStack(stack.copy());
                    this.equipStack(slot, ItemStack.EMPTY);
                }
            }
            for (int i = 0; i < foodInventory.size(); i++) {
                ItemStack stack = foodInventory.removeStack(i);
                if (!stack.isEmpty()) this.dropStack(stack);
            }
            // Chiến lợi phẩm chưa kịp cất cũng rơi ra
            for (int i = 0; i < loot.bag.size(); i++) {
                ItemStack stack = loot.bag.removeStack(i);
                if (!stack.isEmpty()) this.dropStack(stack);
            }
        }
        super.onDeath(damageSource);
    }

    /** Ghi nhận chỗ quái do lính giết (kể cả bằng tên) để chỉ nhặt đồ rơi của chính con đó. */
    @Override
    public boolean onKilledOther(ServerWorld world, LivingEntity other) {
        if (other instanceof net.minecraft.entity.mob.Monster) {
            loot.recordKill(other.getPos(), world.getTime());
        }
        return super.onKilledOther(world, other);
    }

    // ===== INTERACTION =====
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) return ActionResult.SUCCESS;
        // Xác còn hiện ~1 giây khi chết: chặn thu hồi/tương tác để không "hồi sinh" bằng token
        if (!this.isAlive()) return ActionResult.PASS;
        // ===== SHIFT + RIGHT CLICK → THU HỒI NPC =====
        ActionResult recallResult = tryRecallNpc(player);
        if (recallResult != null) return recallResult;
        ItemStack held = player.getStackInHand(hand);
        // ===== ADD FOOD =====
        if (held.isFood()) {
            boolean added = addFoodToInventory(held);
            if (added) {
                this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 0.5F, 1.0F);
                if (!player.isCreative()) {
                    held.decrement(1);
                }
                return ActionResult.CONSUME;
            }
        }
        if (ownerUUID != null && !ownerUUID.equals(player.getUuid())) {
            return super.interactMob(player, hand); // người lạ không được thay/lột trang bị
        }
        ActionResult handleItems = tryHandleItems(player, held);
        if (handleItems != null) return handleItems;

        return super.interactMob(player, hand);
    }

    private boolean isThreateningVillager(LivingEntity hostile) {
        List<VillagerEntity> villagers =
                this.getWorld().getEntitiesByClass(
                        VillagerEntity.class,
                        hostile.getBoundingBox().expand(8),
                        v -> true
                );

        return !villagers.isEmpty();
    }

    private ActionResult tryHandleItems(PlayerEntity player, ItemStack held) {
        // Equip weapon
        if (isWeapon(held)) {
            ItemStack oldWeapon = this.getMainHandStack();
            if (!oldWeapon.isEmpty()) {
                this.dropStack(oldWeapon);
            }
            this.equipStack(EquipmentSlot.MAINHAND, held.copyWithCount(1));
            if (!player.isCreative()) held.decrement(1);
            return ActionResult.CONSUME;
        }
        // Equip armor
        if (held.getItem() instanceof ArmorItem armor) {
            EquipmentSlot slot = armor.getSlotType();
            ItemStack equipped = this.getEquippedStack(slot);

            if (equipped.isEmpty()) {
                this.equipStack(slot, held.copyWithCount(1));
                // Lấy âm thanh mặc giáp chuẩn theo loại giáp (da, sắt, kim cương...)
                this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                        armor.getEquipSound(), SoundCategory.NEUTRAL, 1.0F, 1.0F);
                if (!player.isCreative()) held.decrement(1);
                return ActionResult.CONSUME;
            }
        }
        // Remove armor (empty hand)
        if (held.isEmpty()) {

            ItemStack equippedHand = this.getEquippedStack(EquipmentSlot.MAINHAND);
            if (!equippedHand.isEmpty()) {
                this.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                this.dropStack(equippedHand);
                return ActionResult.CONSUME;
            }

            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (!slot.isArmorSlot()) continue;

                ItemStack equipped = this.getEquippedStack(slot);
                if (!equipped.isEmpty()) {
                    this.equipStack(slot, ItemStack.EMPTY);
                    this.dropStack(equipped);
                    return ActionResult.CONSUME;
                }
            }
        }
        return null;
    }

    private ActionResult tryRecallNpc(PlayerEntity player) {
        if (player.isSneaking()) {
            // chỉ owner được thu hồi
            if (ownerUUID != null && !player.getUuid().equals(ownerUUID)) {
                return ActionResult.FAIL;
            }
            ItemStack token = new ItemStack(ModItems.SOLDIER_TOKEN);

            com.example.item.WorkChestToken.save(this, token);

            if (!player.getInventory().insertStack(token)) {
                this.dropStack(token);
            }

            this.discard(); // xoá NPC

            return ActionResult.CONSUME;
        }
        return null;
    }

    private boolean addFoodToInventory(ItemStack foodStack) {
        if (!foodStack.isFood()) return false;

        ItemStack oneFood = foodStack.copyWithCount(1);

        for (int i = 0; i < foodInventory.size(); i++) {
            ItemStack slot = foodInventory.getStack(i);

            if (slot.isEmpty()) {
                foodInventory.setStack(i, oneFood);
                return true;
            }

            if (ItemStack.canCombine(slot, oneFood) && slot.getCount() < slot.getMaxCount()) {
                slot.increment(1);
                return true;
            }
        }
        return false; // full
    }

    /** Vũ khí Binh lính dùng được (kiếm, rìu, cung, nỏ) — dùng chung cho GUI trang bị. */
    public static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem;
    }

    // ===== COMBAT =====
    @Override
    public boolean tryAttack(Entity target) {
        float damage = (float) this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        damage += (float) combat.getCurrentDamage();

        ItemStack weapon = this.getMainHandStack();
        damage += getWeaponDamage(weapon);
        // ===== ÁP DỤNG ENCHANTMENTS =====
        if (!weapon.isEmpty() && target instanceof LivingEntity livingTarget) {

            // 1️⃣ SHARPNESS (I-V) - Tăng damage chung
            int sharpnessLevel = EnchantmentHelper.getLevel(Enchantments.SHARPNESS, weapon);
            if (sharpnessLevel > 0) {
                // Công thức: +0.5 + 0.5 * level damage
                damage += 0.5F + (sharpnessLevel * 0.5F);
            }

            // 2️⃣ SMITE (I-V) - Tăng damage với undead
            int smiteLevel = EnchantmentHelper.getLevel(Enchantments.SMITE, weapon);
            if (smiteLevel > 0 && livingTarget.getGroup() == EntityGroup.UNDEAD) {
                // Công thức: +2.5 * level damage
                damage += smiteLevel * 2.5F;
            }

            // 3️⃣ BANE OF ARTHROPODS (I-V) - Tăng damage với arthropod
            int baneLevel = EnchantmentHelper.getLevel(Enchantments.BANE_OF_ARTHROPODS, weapon);
            if (baneLevel > 0 && livingTarget.getGroup() == EntityGroup.ARTHROPOD) {
                // Công thức: +2.5 * level damage
                damage += baneLevel * 2.5F;
            }
        }
        this.getWorld().playSound(null, // null = tất cả player gần đó đều nghe
                target.getX(),
                target.getY(),
                target.getZ(),
                net.minecraft.sound.SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                net.minecraft.sound.SoundCategory.NEUTRAL,
                0.6F,  // volume
                1.6F);
        DamageSources sources = this.getWorld().getDamageSources();
        boolean success = target.damage(sources.mobAttack(this), damage);
        damageDealtThisAttack = damage;
        // Weapon durability
        if (success && !weapon.isEmpty() && weapon.isDamageable()) {
            //không bao gio hỏng vu khi
            //     if (this.random.nextInt(WEAPON_DURABILITY_CHANCE) == 0) {
            //        weapon.damage(1, this, e -> e.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND));
            //     }
            if (target instanceof LivingEntity livingTarget) {
                // 4️⃣ FIRE ASPECT (I-II) - Đốt cháy mục tiêu
                int fireAspectLevel = EnchantmentHelper.getLevel(Enchantments.FIRE_ASPECT, weapon);
                if (fireAspectLevel > 0) {
                    // Level I: 4 giây (80 ticks), Level II: 8 giây (160 ticks)
                    int fireTicks = fireAspectLevel * 80;
                    livingTarget.setOnFireFor(fireTicks / 20); // Convert ticks to seconds
                }

                // 5️⃣ KNOCKBACK (I-II) - Đẩy lùi mục tiêu
                int knockbackLevel = EnchantmentHelper.getLevel(Enchantments.KNOCKBACK, weapon);
                if (knockbackLevel > 0) {
                    // Tính vector đẩy
                    Vec3d direction = livingTarget.getPos()
                            .subtract(this.getPos())
                            .normalize()
                            .multiply(knockbackLevel * 2); // 0.5 strength per level

                    // Đẩy lùi theo chiều ngang (x, z) và hơi bay lên (y)
                    livingTarget.takeKnockback(
                            knockbackLevel * 2,
                            -direction.x,
                            -direction.z
                    );

                    // Thêm velocity bay lên một chút
                    livingTarget.addVelocity(0, 0.5 * knockbackLevel, 0);
                    livingTarget.velocityModified = true;
                }
                if (!livingTarget.isAlive()) {
                    combat.addExperience(livingTarget, damageDealtThisAttack);
                    spawnLevelUpParticles();
                }
            }
            this.getWorld().playSound(null, // null = tất cả player gần đó đều nghe
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
                    net.minecraft.sound.SoundCategory.NEUTRAL,
                    0.6F,  // volume
                    1.6F);
        }

        return success;
    }
    private void spawnLevelUpParticles() {
        if (this.getWorld() == null) return;

        for (int i = 0; i < 15; i++) {
            double offsetX = (this.random.nextDouble() - 0.5) * 2;
            double offsetY = (this.random.nextDouble() - 0.5) * 2 + 1;
            double offsetZ = (this.random.nextDouble() - 0.5) * 2;

            this.getWorld().addParticle(
                    ParticleTypes.ENCHANT,
                    this.getX() + offsetX,
                    this.getY() + offsetY,
                    this.getZ() + offsetZ,
                    0, 0, 0
            );
        }
    }
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (isFriendlyFire(source, ownerUUID)) return false; // tên lạc của đồng đội: không mất máu, không đánh trả
        boolean result = super.damage(source, amount);

        if (!this.getWorld().isClient && result) {
            // Armor durability
            if (amount > 0 && this.random.nextInt(ARMOR_DURABILITY_CHANCE) == 0) {
                damageRandomArmor();
            }
        }

        return result;
    }

    /** Sát thương đến từ Binh lính (kể cả tên/đạn họ bắn) có cùng chủ với nạn nhân. */
    /**
     * Phe ta của lính: chủ, Binh lính và NPC nghề cùng chủ, thú cưng của chủ, dân làng, golem sắt.
     * Tên của lính bay XUYÊN qua phe ta (không trúng, không mất tên vào người đồng đội).
     */
    public boolean isAlly(net.minecraft.entity.Entity e) {
        if (e == this) return true;
        UUID owner = ownerUUID;
        if (e instanceof PlayerEntity player) return owner != null && owner.equals(player.getUuid());
        if (e instanceof SoldierNPCEntity soldier) return owner != null && owner.equals(soldier.getOwnerUuid());
        if (e instanceof com.example.entity.base.WorkerNpcEntity worker) return owner != null && owner.equals(worker.getOwnerUUID());
        if (e instanceof net.minecraft.entity.passive.TameableEntity pet) return owner != null && owner.equals(pet.getOwnerUuid());
        return e instanceof VillagerEntity || e instanceof net.minecraft.entity.passive.IronGolemEntity;
    }

    /** Mũi tên của lính: bắn vô hạn, không nhặt được, bay xuyên qua phe ta ({@link #isAlly}). */
    public static net.minecraft.entity.projectile.ArrowEntity createAllySafeArrow(SoldierNPCEntity shooter) {
        net.minecraft.entity.projectile.ArrowEntity arrow = new net.minecraft.entity.projectile.ArrowEntity(shooter.getWorld(), shooter) {
            @Override
            protected boolean canHit(net.minecraft.entity.Entity entity) {
                return super.canHit(entity) && !shooter.isAlly(entity);
            }
        };
        arrow.initFromStack(Items.ARROW.getDefaultStack());
        arrow.pickupType = net.minecraft.entity.projectile.PersistentProjectileEntity.PickupPermission.DISALLOWED;
        return arrow;
    }

    public static boolean isFriendlyFire(DamageSource source, @Nullable UUID victimOwner) {
        return victimOwner != null && source.getAttacker() instanceof SoldierNPCEntity soldier
                && victimOwner.equals(soldier.getOwnerUuid());
    }

    private void damageRandomArmor() {
        List<EquipmentSlot> armorSlots = new ArrayList<>();

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmorSlot()) continue;

            ItemStack armor = this.getEquippedStack(slot);
            if (armor.isEmpty() || !armor.isDamageable()) continue;
            armorSlots.add(slot);
        }
        if (armorSlots.isEmpty()) return;

        EquipmentSlot randomSlot =
                armorSlots.get(this.random.nextInt(armorSlots.size()));

        ItemStack armor = this.getEquippedStack(randomSlot);
        armor.damage(1, this, e -> e.sendEquipmentBreakStatus(randomSlot));

    }

    private float getWeaponDamage(ItemStack weapon) {
        if (weapon.isEmpty()) return 0f;

        if (weapon.getItem() instanceof SwordItem sword) {
            return sword.getAttackDamage();
        }
        if (weapon.getItem() instanceof AxeItem axe) {
            return axe.getAttackDamage();
        }

        return 0f;
    }


    // ===== NBT SAVE/LOAD =====
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);

        // Owner
        if (ownerUUID != null) {
            nbt.putUuid("OwnerUUID", ownerUUID);
        }

        // Follow player
        if (followPlayerUUID != null) {
            nbt.putUuid("FollowPlayer", followPlayerUUID);
        }

        // Equipment
        saveEquipment(nbt);

        // Food stats
        nbt.putInt("Hunger", hunger);

        Inventories.writeNbt(nbt, foodInventory.stacks);
        loot.writeNbt(nbt);

        nbt.putInt("MoveMode", this.dataTracker.get(MOVE_MODE));
        if (guardHome != null) nbt.putLong("GuardHome", guardHome.asLong());

        NbtCompound combatNbt = new NbtCompound();
        combat.writeToNbt(combatNbt);
        nbt.put("CombatData", combatNbt);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        // Owner
        if (nbt.containsUuid("OwnerUUID")) {
            ownerUUID = nbt.getUuid("OwnerUUID");
        }

        // Follow player
        if (nbt.containsUuid("FollowPlayer")) {
            followPlayerUUID = nbt.getUuid("FollowPlayer");
        }

        // Equipment
        loadEquipment(nbt);

        // Food stats
        if (nbt.contains("Hunger")) hunger = nbt.getInt("Hunger");

        Inventories.readNbt(nbt, foodInventory.stacks);
        loot.readNbt(nbt);

        if (nbt.contains("MoveMode")) {
            this.dataTracker.set(MOVE_MODE, nbt.getInt("MoveMode"));
        }
        guardHome = nbt.contains("GuardHome") ? net.minecraft.util.math.BlockPos.fromLong(nbt.getLong("GuardHome")) : null;
        if (nbt.contains("CombatData")) {
            combat.readFromNbt(nbt.getCompound("CombatData"));
        }

        // Cập nhật MaxHealth theo level
        this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)
                .setBaseValue(combat.getCurrentMaxHealth());
        // Giữ máu đã lưu (vanilla đọc trước), chỉ kẹp về max mới
        if (this.getHealth() > this.getMaxHealth()) this.setHealth(this.getMaxHealth());

    }

    private void saveEquipment(NbtCompound nbt) {
        // Weapon
        ItemStack weapon = this.getMainHandStack();
        if (!weapon.isEmpty()) {
            nbt.put("Weapon", weapon.writeNbt(new NbtCompound()));
        }

        // Armor
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = this.getEquippedStack(slot);
            if (!stack.isEmpty()) {
                nbt.put(slot.getName(), stack.writeNbt(new NbtCompound()));
            }
        }
    }

    private void loadEquipment(NbtCompound nbt) {
        // Weapon
        if (nbt.contains("Weapon")) {
            ItemStack weapon = ItemStack.fromNbt(nbt.getCompound("Weapon"));
            this.equipStack(EquipmentSlot.MAINHAND, weapon);
        }

        // Armor
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (nbt.contains(slot.getName())) {
                this.equipStack(slot, ItemStack.fromNbt(nbt.getCompound(slot.getName())));
            }
        }
    }

    // ===== OWNER MANAGEMENT =====
    public void setOwner(PlayerEntity player) {
        this.ownerUUID = player != null ? player.getUuid() : null;
    }

    @Nullable
    public PlayerEntity getOwner() {
        if (ownerUUID == null) return null;
        if (this.getWorld().isClient) return null;

        return ((ServerWorld) this.getWorld()).getEntity(ownerUUID) instanceof PlayerEntity player
                && player.isAlive() ? player : null;
    }

    @Nullable
    public UUID getOwnerUuid() {
        return ownerUUID;
    }

    public int getHunger() {
        return hunger;
    }

    /** Nhãn việc đang làm cho Sổ quản lý làng (goalSelector là protected). */
    public String overviewLabel() {
        return com.example.entity.base.NpcDisplayComponent.workLabel(this, goalSelector, null);
    }

    public UUID getFollowPlayerUUID() {
        return followPlayerUUID;
    }

    @Override
    public Arm getMainArm() {
        return Arm.RIGHT;
    }


}