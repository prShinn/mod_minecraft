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
    private static final int MAX_HUNGER = 20;
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

    public ModeNpc.ModeMove getMoveMode() {
        int id = this.dataTracker.get(MOVE_MODE);
        return ModeNpc.ModeMove.values()[MathHelper.clamp(id, 0, ModeNpc.ModeMove.values().length - 1)];
    }

    public void setMoveMode(ModeNpc.ModeMove moveMode) {
        this.moveMode = moveMode;
        this.dataTracker.set(MOVE_MODE, moveMode.ordinal());
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
        this.goalSelector.add(3, new EscapeDangerGoal(this, 1.4));
        this.goalSelector.add(4, new com.example.ai.FindAndSleepGoal(this, () -> sleeping));
        this.goalSelector.add(4, new HealAllyGoal(this) {
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
        this.goalSelector.add(5, new FollowOwnerLikeGoal(this, 1.3D));
        this.goalSelector.add(6, new ReturnToPlayerGoal(this));
// nhin player
        this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        // đi lang thang xa
        this.goalSelector.add(8, new WanderAroundFarGoal(this, 1.0D));
        // đi xung quanh gần (phat trien sau)
//        this.goalSelector.add(8, new WanderAroundGoal(this, 0.8));
        // nhìn xung quanh
        this.goalSelector.add(8, new LookAroundGoal(this));
        // đánh quái
        this.targetSelector.add(1, new DefendPlayerGoal(this, 32.0F)); // bảo vệ
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, HostileEntity.class, true, target -> {
            // Mở rộng khoảng cách check quanh npc hoặc player: 25 block
            PlayerEntity owner = this.getOwner();
            return this.squaredDistanceTo(target) <= 26 * 26
                    || (owner != null && owner.squaredDistanceTo(target) <= 25 * 25) || isThreateningVillager(target);

        })); // đánh hostile
        this.targetSelector.add(2, new RevengeGoal(this, PlayerEntity.class)); // đánh trả khi bị tấn công

    }

    // ===== TICK =====
    @Override
    public void tick() {
        super.tick();
        if (this.getWorld() == null || this.getWorld().isClient) return;// check ở server
        // Check player alive
        PlayerEntity owner = getOwner();
        if (owner != null && !owner.isAlive()) {
            this.ownerUUID = null;
        }

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

        // Display management
        updateNameDisplay();
        // Food system
        sleeping.tick(this);
        handleFoodSystem();
        // Healing system
        handleGradualHeal();
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
            }
            playerSearchCooldown = PLAYER_SEARCH_INTERVAL;
        }
        // Update name visibility
        if (nameVisibleTicks > 0) {
            nameVisibleTicks--;
            updateNameText();
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
                .append(Text.literal(" [Lv." + combat.getLevel() + "] ").formatted(Formatting.AQUA))
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

    // ===== INTERACTION =====
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) return ActionResult.SUCCESS;
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

    private boolean isWeapon(ItemStack stack) {
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
        boolean result = super.damage(source, amount);

        if (!this.getWorld().isClient && result) {
            // Armor durability
            if (amount > 0 && this.random.nextInt(ARMOR_DURABILITY_CHANCE) == 0) {
                damageRandomArmor();
            }
        }

        return result;
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

        nbt.putInt("MoveMode", this.dataTracker.get(MOVE_MODE));

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

        if (nbt.contains("MoveMode")) {
            this.dataTracker.set(MOVE_MODE, nbt.getInt("MoveMode"));
        }
        if (nbt.contains("CombatData")) {
            combat.readFromNbt(nbt.getCompound("CombatData"));
        }

        // Cập nhật MaxHealth theo level
        this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)
                .setBaseValue(combat.getCurrentMaxHealth());
        this.setHealth((float) combat.getCurrentMaxHealth());

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

        return ((ServerWorld) this.getWorld()).getEntity(ownerUUID) instanceof PlayerEntity player ? player : null;
    }

    public UUID getFollowPlayerUUID() {
        return followPlayerUUID;
    }

    @Override
    public Arm getMainArm() {
        return Arm.RIGHT;
    }


}