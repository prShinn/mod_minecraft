package com.example.entity;

import com.example.ai.DeliveryTarget;
import com.example.ai.cook.CookFoodGoal;
import com.example.ai.worker.DepositCargoGoal;
import com.example.ai.worker.WorkerWanderGoal;
import com.example.block.CookChestBlockEntity;
import com.example.block.ProfessionChestBlockEntity;
import com.example.config.ModConfig;
import com.example.entity.base.WorkerNpcEntity;
import com.example.helper.FoodChestHelper;
import com.example.registry.ModItems;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Đầu bếp: lấy nguyên liệu sống từ rương của mình và rương nghề khác (nông dân, ngư dân,
 * chăn nuôi), nấu tại Rương Đầu bếp rồi mang món chín tới Rương Thức Ăn chung.
 * Không có Rương Thức Ăn trong vùng thì cất món chín vào Rương Đầu bếp.
 */
public class CookNpcEntity extends WorkerNpcEntity {

    /** Nguyên liệu sống đang mang đi nấu. */
    private final SimpleInventory ingredients = new SimpleInventory(9);
    /** Số món còn nấu được với nhiên liệu đã bỏ vào. */
    private int fuelUnits;
    private BlockPos lastFoodChest;

    public CookNpcEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void addWorkGoals() {
        this.goalSelector.add(5, new DepositCargoGoal(this));
        this.goalSelector.add(6, new CookFoodGoal(this));
        this.goalSelector.add(9, new WorkerWanderGoal(this));
    }

    @Override
    protected boolean usesAutoDeposit() {
        return true;
    }

    @Override
    protected Class<? extends ProfessionChestBlockEntity> chestType() {
        return CookChestBlockEntity.class;
    }

    @Override
    public Item tokenItem() {
        return ModItems.COOK_TOKEN;
    }

    @Override
    public String professionName() {
        return "Đầu bếp";
    }

    @Override
    protected ItemStack workTool() {
        return new ItemStack(Items.BOWL);
    }

    // ===== Nộp món ăn: ưu tiên Rương Thức Ăn chung =====

    @Nullable
    @Override
    public BlockPos findDepositTarget(DeliveryTarget delivery) {
        BlockPos foodChest = findFoodChest();
        if (foodChest != null) {
            lastFoodChest = foodChest;
            display.clearReport();
            return foodChest;
        }
        return super.findDepositTarget(delivery);
    }

    @Override
    public boolean isDepositTarget(BlockEntity be) {
        return isFoodChest(be) || super.isDepositTarget(be);
    }

    /** Rương Thức Ăn chung, hoặc rương thường có sách đánh dấu "FOOD CHEST" ở ô đầu (giống NPC khác). */
    private static boolean isFoodChest(@Nullable BlockEntity be) {
        return be instanceof Inventory && be.getWorld() != null
                && FoodChestHelper.isValidFoodChest(be.getPos(), be.getWorld());
    }

    @Nullable
    private BlockPos findFoodChest() {
        World world = getWorld();
        if (lastFoodChest != null && world.isChunkLoaded(lastFoodChest)
                && canUseFoodChest(world.getBlockEntity(lastFoodChest), lastFoodChest)) {
            return lastFoodChest;
        }
        int r = ModConfig.get().cookWorkRadius;
        BlockPos center = getBlockPos();
        List<BlockPos> candidates = new ArrayList<>();
        for (var be : com.example.helper.BlockEntityScan.inBox(world, center, r, 4)) {
            if (isFoodChest(be)) candidates.add(be.getPos().toImmutable());
        }
        candidates.sort(Comparator.comparingDouble(center::getSquaredDistance));
        for (int i = 0; i < Math.min(4, candidates.size()); i++) {
            BlockPos pos = candidates.get(i);
            if (canUseFoodChest(world.getBlockEntity(pos), pos)) return pos;
        }
        return null;
    }

    private boolean canUseFoodChest(@Nullable BlockEntity be, BlockPos pos) {
        if (!isFoodChest(be) || !DeliveryTarget.hasSpace((Inventory) be, cargo, stack -> true)) return false;
        if (DeliveryTarget.canReach(this, pos)) return true;
        var path = getNavigation().findPathTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1);
        return DeliveryTarget.pathReachesChest(path, pos);
    }

    // ===== Getters & lưu trữ =====

    public SimpleInventory getIngredients() {
        return ingredients;
    }

    public int getFuelUnits() {
        return fuelUnits;
    }

    public void setFuelUnits(int fuelUnits) {
        this.fuelUnits = Math.max(0, fuelUnits);
    }

    @Override
    protected void writeWorkerNbt(NbtCompound nbt) {
        nbt.put("CookIngredients", ingredients.toNbtList());
        nbt.putInt("CookFuelUnits", fuelUnits);
    }

    @Override
    protected void readWorkerNbt(NbtCompound nbt) {
        if (nbt.contains("CookIngredients")) ingredients.readNbtList(nbt.getList("CookIngredients", 10));
        fuelUnits = Math.max(0, nbt.getInt("CookFuelUnits"));
    }

    @Override
    protected void dropWorkerItems() {
        dropInventory(ingredients);
    }
}
