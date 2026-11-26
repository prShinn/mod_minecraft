package com.example.entity;

import com.example.ai.miner.MineQuarryGoal;
import com.example.ai.worker.DepositCargoGoal;
import com.example.block.MinerChestBlockEntity;
import com.example.block.ProfessionChestBlockEntity;
import com.example.entity.base.NpcSleepingComponent;
import com.example.entity.base.WorkerNpcEntity;
import com.example.registry.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Thợ mỏ: đứng cạnh Rương Thợ mỏ, đào từ xa mọi block trong vùng đào cấu hình trên rương
 * (Shift + chuột phải tay không vào rương), đồ đào được cho thẳng vào rương. Hết block thì đứng chờ việc.
 */
public class MinerNpcEntity extends WorkerNpcEntity {

    public static final int FIND_CHEST_DISTANCE = 24;

    public MinerNpcEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world, 9);
    }

    @Override
    protected NpcSleepingComponent createSleepingComponent() {
        return new NpcSleepingComponent()
                .withSearchRadius(32)
                .withCooldown(120)
                .withWanderDuration(120);
    }

    @Override
    protected void addWorkGoals() {
        // Túi đồ cũ (bản đào Mỏ Quặng) còn khoáng sản → cất nốt vào rương
        this.goalSelector.add(5, new DepositCargoGoal(this));
        this.goalSelector.add(6, new MineQuarryGoal(this));
    }

    @Override
    protected boolean usesAutoDeposit() {
        return true;
    }

    @Override
    protected Class<? extends ProfessionChestBlockEntity> chestType() {
        return MinerChestBlockEntity.class;
    }

    @Override
    public Item tokenItem() {
        return ModItems.MINER_TOKEN;
    }

    @Override
    public String professionName() {
        return "Thợ mỏ";
    }

    @Override
    protected ItemStack workTool() {
        return new ItemStack(Items.IRON_PICKAXE);
    }

    @Override
    protected int chestSearchRadius() {
        return FIND_CHEST_DISTANCE;
    }

    @Override
    protected int chestSearchHeight() {
        return 5;
    }

    @Override
    protected String legacyCargoKey() {
        return "FoodInventory";
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (sleeping.isSleeping()) sleeping.wakeUp(this);
        return super.damage(source, amount);
    }

    // ===== NBT =====

    @Override
    protected void writeWorkerNbt(NbtCompound nbt) {
        nbt.putBoolean("IsMinerNpc", true);
    }

    @Override
    protected void readWorkerNbt(NbtCompound nbt) {
        // Dữ liệu kiểu cũ: vị trí rương lưu bằng MinerChestPos
        if (getChestPos() == null && nbt.contains("MinerChestPos")
                && getWorld().getRegistryKey().getValue().toString().equals(nbt.getString("MinerChestDimension"))) {
            restoreChestPos(BlockPos.fromLong(nbt.getLong("MinerChestPos")));
        }
    }
}
