package com.example.entity;

import com.example.ai.fisherman.DepositFishToChestGoal;
import com.example.ai.fisherman.FindFishingSpotGoal;
import com.example.ai.fisherman.FishermanFishGoal;
import com.example.ai.fisherman.FishermanMemory;
import com.example.ai.fisherman.FishermanWanderGoal;
import com.example.block.FishermanChestBlockEntity;
import com.example.block.ProfessionChestBlockEntity;
import com.example.entity.base.WorkerNpcEntity;
import com.example.registry.ModItems;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/** Ngư dân: tìm hồ nước nguồn 4×4 gần Rương Ngư dân, câu cá và nộp về rương. */
public class FishermanNpcEntity extends WorkerNpcEntity {

    public final FishermanMemory memory = new FishermanMemory();

    private static final int WATER_SEARCH_COOLDOWN = 100;
    private int waterSearchCooldown = 0;
    private final Map<BlockPos, Long> blockedFishingSpots = new HashMap<>();

    public FishermanNpcEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world, 36);
    }

    @Override
    protected void addWorkGoals() {
        this.goalSelector.add(5, new DepositFishToChestGoal(this));
        this.goalSelector.add(6, new FishermanFishGoal(this));
        this.goalSelector.add(7, new FindFishingSpotGoal(this));
        this.goalSelector.add(9, new FishermanWanderGoal(this));
    }

    @Override
    protected Class<? extends ProfessionChestBlockEntity> chestType() {
        return FishermanChestBlockEntity.class;
    }

    @Override
    public Item tokenItem() {
        return ModItems.FISHERMAN_TOKEN;
    }

    @Override
    public String professionName() {
        return "Ngư dân";
    }

    @Override
    protected ItemStack workTool() {
        return new ItemStack(Items.FISHING_ROD);
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
        if (waterSearchCooldown > 0) waterSearchCooldown--;
    }

    @Override
    protected void writeWorkerNbt(NbtCompound nbt) {
        nbt.putBoolean("IsFishermanNpc", true);
    }

    // ===== Tìm chỗ câu =====

    public void skipFishingSpot(BlockPos spot) {
        blockedFishingSpots.put(spot.toImmutable(), getWorld().getTime() + 600);
        waterSearchCooldown = Math.min(waterSearchCooldown, 10);
        display.report(this, "Không có đường tới bờ câu");
    }

    public BlockPos findFishingSpot() {
        if (waterSearchCooldown > 0) return null;
        BlockPos center = findWorkChest();
        if (center == null) return null;
        waterSearchCooldown = WATER_SEARCH_COOLDOWN;
        World world = getWorld();
        blockedFishingSpots.values().removeIf(until -> until <= world.getTime());
        boolean blockedShore = false;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -16; x <= 16; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -16; z <= 16; z++) {
                    BlockPos pos = center.add(x, y, z);
                    double distance = squaredDistanceTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    if (distance >= bestDistance || !world.isChunkLoaded(pos)) continue;
                    if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()
                            || !world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) continue;
                    if (findFishingWater(pos) == null) continue;
                    if (blockedFishingSpots.containsKey(pos)) {
                        blockedShore = true;
                        continue;
                    }
                    best = pos;
                    bestDistance = distance;
                }
            }
        }
        if (best == null) display.report(this, blockedShore ? "Không có đường tới bờ câu" : "Chưa tìm thấy hồ nước nguồn 4×4");
        return best;
    }

    public BlockPos findFishingWater(BlockPos shore) {
        World world = getWorld();
        for (Direction forward : Direction.Type.HORIZONTAL) {
            Direction side = forward.rotateYClockwise();
            for (int depth = 0; depth <= 2; depth++) {
                BlockPos edge = shore.down(depth).offset(forward);
                if (!isSurfaceWater(world, edge)) continue;
                for (int offset = 0; offset < 4; offset++) {
                    BlockPos corner = edge.offset(side.getOpposite(), offset);
                    boolean valid = true;
                    for (int row = 0; row < 4 && valid; row++) {
                        for (int col = 0; col < 4; col++) {
                            if (!isSurfaceWater(world, corner.offset(forward, row).offset(side, col))) {
                                valid = false;
                                break;
                            }
                        }
                    }
                    if (valid) return edge;
                }
            }
        }
        return null;
    }

    private boolean isSurfaceWater(World world, BlockPos pos) {
        return world.isChunkLoaded(pos) && world.getBlockState(pos).isOf(Blocks.WATER)
                && world.getFluidState(pos).isStill() && world.getBlockState(pos.up()).isAir();
    }
}
