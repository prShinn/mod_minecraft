package com.example.entity;

import com.example.ai.DefendPlayerGoal;
import com.example.ai.FollowOwnerLikeGoal;
import com.example.ai.HealAllyGoal;
import com.example.ai.SoldierBowGoal;
import com.example.registry.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Arm;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SoldierNPCEntity extends PathAwareEntity {
    // ===== CONSTANTS =====
    private static final int MAX_HUNGER = 20;
    private static final int FOOD_TICK_INTERVAL = 1200; // 1p
    private static final int SHOW_NAME_DURATION = 20; // 1 giây
    private static final double VIEW_DISTANCE = 6.0;
    private static final float FOLLOW_DISTANCE = 25f;
    private static final float TELEPORT_DISTANCE = 40f;
    private static final int PLAYER_SEARCH_INTERVAL = 20; // 1 giây
    private static final int WEAPON_DURABILITY_CHANCE = 10; // 1/10 xác suất
    private static final int ARMOR_DURABILITY_CHANCE = 10; // 1/10 xác suất

    // ===== OWNER & FOLLOW =====
    private UUID ownerUUID;
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

    // ===== CONSTRUCTOR =====
    public SoldierNPCEntity(EntityType<? extends SoldierNPCEntity> type, World world) {
        super(type, world);
    }

    // ===== ATTRIBUTES =====
    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 25.0D)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 2.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30D);
    }


    // ===== AI GOALS =====
    @Override
    protected void initGoals() {
        super.initGoals();
        // ⚠️ QUAN TRỌNG: Clear goals cũ trước
        this.goalSelector.clear(goal -> true);
        this.targetSelector.clear(goal -> true);

        this.goalSelector.add(0, new SwimGoal(this)); // ko chet duoi
        this.goalSelector.add(3, new EscapeDangerGoal(this, 1.4));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.2, true) {
            @Override
            public boolean canStart() {
                // chỉ start nếu cầm sword
                return super.canStart() && SoldierNPCEntity.this.getMainHandStack().getItem() instanceof SwordItem;
            }
        });
        this.goalSelector.add(2, new HealAllyGoal(this) {
            @Override
            public boolean canStart() {
                // chỉ start nếu cầm riu
                return super.canStart() && SoldierNPCEntity.this.getMainHandStack().getItem() instanceof AxeItem;
            }
        });
        this.goalSelector.add(2, new SoldierBowGoal(this) {
            @Override
            public boolean canStart() {
                // chỉ start nếu cầm cung
                return super.canStart() && SoldierNPCEntity.this.getMainHandStack().getItem() instanceof BowItem;
            }
        });

        this.goalSelector.add(4, new FollowOwnerLikeGoal(this, 1.3D, FOLLOW_DISTANCE, TELEPORT_DISTANCE)); // uu tien di theo
        // đi lang thang xa
        this.goalSelector.add(6, new WanderAroundFarGoal(this, 1.0D));
        // nhin player
        this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        // đi xung quanh
        this.goalSelector.add(8, new WanderAroundGoal(this, 0.8));
        // nhìn xung quanh
        this.goalSelector.add(9, new LookAroundGoal(this));
        // đánh quái
        this.targetSelector.add(1, new DefendPlayerGoal(this, 32.0F)); // bảo vệ
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, HostileEntity.class, true, target -> {
            // Mở rộng khoảng cách check quanh npc hoặc player: 20 block
            return this.squaredDistanceTo(target) <= 21 * 21 || this.getOwner().squaredDistanceTo(target) <= 21 * 21;
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

        // Display management
        updateNameDisplay();

        // Food system
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
        if (this.getWorld().isClient) return; // Chỉ server xử lý
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

        float healAmount = 1.0F; // 0.5 tim

        this.heal(healAmount);
        hunger -= 2;                // 🔥 Heal tốn Hunger
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

        MutableText name = Text.literal("Đệ tử").formatted(Formatting.WHITE).append(Text.literal(" [" + hp + "/" + maxHp + "] ").formatted(Formatting.GREEN)).append(Text.literal("🍖[" + hunger + "/" + MAX_HUNGER + "] x" + foodCount).formatted(Formatting.GOLD));

        this.setCustomName(name);
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
        if (player.isSneaking()) {

            // chỉ owner được thu hồi
            if (ownerUUID != null && !player.getUuid().equals(ownerUUID)) {
                return ActionResult.FAIL;
            }

            ItemStack token = new ItemStack(ModItems.SOLDIER_TOKEN);

            NbtCompound entityNbt = new NbtCompound();
            this.writeCustomDataToNbt(entityNbt);
            entityNbt.putFloat("Health", this.getHealth());

            token.getOrCreateNbt().put("EntityTag", entityNbt);

            if (!player.getInventory().insertStack(token)) {
                this.dropStack(token);
            }

            this.discard(); // xoá NPC

            return ActionResult.CONSUME;
        }
        ItemStack held = player.getStackInHand(hand);
        // ===== ADD FOOD =====
        if (held.isFood()) {
            boolean added = addFoodToInventory(held);
            if (added) {
                if (!player.isCreative()) {
                    held.decrement(1);
                }
                return ActionResult.CONSUME;
            }
        }

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

        return super.interactMob(player, hand);
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
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem || stack.getItem() instanceof BowItem;
    }

    // ===== COMBAT =====
    @Override
    public boolean tryAttack(Entity target) {
        float damage = (float) this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);

        ItemStack weapon = this.getMainHandStack();
        damage += getWeaponDamage(weapon);

        DamageSources sources = this.getWorld().getDamageSources();
        boolean success = target.damage(sources.mobAttack(this), damage);

        // Weapon durability
        if (success && !weapon.isEmpty() && weapon.isDamageable()) {
            if (this.random.nextInt(WEAPON_DURABILITY_CHANCE) == 0) {
                weapon.damage(1, this, e -> e.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND));
            }
        }

        return success;
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

    // ===== MISC =====
    @Override
    public void initEquipment(Random random, LocalDifficulty difficulty) {
        // Không spawn với trang bị ngẫu nhiên
    }

    @Override
    public Arm getMainArm() {
        return Arm.RIGHT;
    }
}