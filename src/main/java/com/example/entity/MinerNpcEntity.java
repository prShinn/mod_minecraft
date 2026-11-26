package com.example.entity;

import com.example.ai.miner.DepositOreToChestGoal;
import com.example.ai.miner.MineOreVeinGoal;
import com.example.ai.miner.MinerMemory;
import com.example.ai.miner.MinerWanderGoal;
import com.example.block.MinerChestBlockEntity;
import com.example.block.ProfessionChestBlockEntity;
import com.example.entity.base.NpcSleepingComponent;
import com.example.entity.base.WorkerNpcEntity;
import com.example.registry.ModBlocks;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Thợ mỏ: đào block Mỏ Quặng quanh Rương Thợ mỏ, nộp khoáng sản về rương. */
public class MinerNpcEntity extends WorkerNpcEntity {

    public static final int FIND_CHEST_DISTANCE = 24;
    public static final int FIND_ORE_DISTANCE = 16;
    private static final int ORE_SEARCH_COOLDOWN = 60; // 3 giây giữa các lần scan

    public final MinerMemory memory = new MinerMemory();
    private final Map<BlockPos, Long> unreachableOres = new HashMap<>();
    private final Random random = new Random();
    private int oreSearchCooldown;

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
        this.goalSelector.add(5, new DepositOreToChestGoal(this));
        this.goalSelector.add(6, new MineOreVeinGoal(this));
        this.goalSelector.add(9, new MinerWanderGoal(this));
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
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (!sleeping.isSleeping()) {
            if (getNavigation().isIdle()) memory.tickIdle();
            else memory.resetIdle();
        }
        if (oreSearchCooldown > 0) oreSearchCooldown--;
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
        // Thợ mỏ chỉ đi nộp khi có đồ không phải thức ăn
        boolean hasOre = false;
        for (int slot = 0; slot < cargo.size(); slot++) {
            ItemStack stack = cargo.getStack(slot);
            if (!stack.isEmpty() && !stack.isFood()) {
                hasOre = true;
                break;
            }
        }
        setNeedsToDeposit(hasOre);
    }

    // ===== Quặng =====

    public void onOreDepleted(BlockPos ore) {
        oreSearchCooldown = 0;
        unreachableOres.remove(ore);
    }

    public boolean hasEmptyOreSlot() {
        return !isInventoryFull();
    }

    public void skipUnreachableOre(BlockPos ore) {
        unreachableOres.put(ore.toImmutable(), getWorld().getTime() + 200);
        oreSearchCooldown = 0;
    }

    /**
     * Tìm ngẫu nhiên một block Mỏ Quặng trong bán kính FIND_ORE_DISTANCE quanh rương.
     * Có cooldown 3 giây; chỉ quét Y từ -4 đến +8 vì Mỏ Quặng là block bề mặt.
     */
    public BlockPos findRandomOreVein() {
        if (oreSearchCooldown > 0) return null;
        World world = getWorld();
        BlockPos center = findWorkChest();
        if (center == null) return null;

        List<BlockPos> candidates = new ArrayList<>();
        long now = world.getTime();
        unreachableOres.values().removeIf(until -> until <= now);
        boolean hasBlockedOre = false;
        int radius = FIND_ORE_DISTANCE;
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

        oreSearchCooldown = ORE_SEARCH_COOLDOWN;
        if (candidates.isEmpty()) {
            display.report(this, hasBlockedOre ? "Đang chờ thử lại đường tới mỏ" : "Chưa tìm thấy mỏ quặng");
            return null;
        }
        display.clearReport();
        return candidates.get(random.nextInt(candidates.size()));
    }
}
