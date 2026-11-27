package com.example.ai.cook;

import com.example.ai.DeliveryTarget;
import com.example.ai.worker.DepositCargoGoal;
import com.example.block.FarmerChestBlockEntity;
import com.example.block.FishermanChestBlockEntity;
import com.example.block.ProfessionChestBlockEntity;
import com.example.config.ModConfig;
import com.example.entity.CookNpcEntity;
import com.example.entity.base.WorkerNpcEntity;
import com.example.helper.BlockEntityScan;
import com.example.helper.NpcFeedback;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.CampfireBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.block.entity.SmokerBlockEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.RecipeType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Chu trình nấu ăn của Đầu bếp:
 * 1. GATHER — tới rương có nguyên liệu sống (rương mình trước, sau đó rương nông dân /
 *    ngư dân / chăn nuôi cùng chủ), lấy tối đa 16 món, chừa lại một phần cho nghề khác.
 * 2. COOK — đứng cạnh Rương Đầu bếp nấu từng món: 3 lúa mì → 1 bánh mì (không cần lửa),
 *    đồ hun khói được (thịt, cá, khoai tây) thì tốn nhiên liệu lấy từ Rương Đầu bếp.
 * Xong mẻ thì bật cờ nộp hàng để DepositCargoGoal mang món chín đi.
 */
public class CookFoodGoal extends Goal {
    private static final int MAX_INGREDIENTS = 16;
    private static final int MAX_WALK_TICKS = 400;
    private static final int WHEAT_PER_BREAD = 3;

    private enum Phase { GATHER, COOK }

    private final CookNpcEntity npc;
    private Phase phase;
    private BlockPos chest;
    private BlockPos source;
    /** Trạm nấu gần rương: ưu tiên Smoker → Furnace → Campfire; null/bằng chest nếu chưa đặt lò. */
    private BlockPos station;
    /** Đánh dấu NPC đã bật lửa lò (Smoker/Furnace) để tắt lại khi xong mẻ hoặc bị ngắt. */
    private boolean litByNpc;
    private long nextScan;
    private int walkTicks;
    private int cookTicks;
    private boolean done;

    public CookFoodGoal(CookNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
    }

    // ===== Nhận diện nguyên liệu =====

    private static ItemStack smokedResult(World world, ItemStack stack) {
        return world.getRecipeManager()
                .getFirstMatch(RecipeType.SMOKING, new SimpleInventory(stack.copyWithCount(1)), world)
                .map(recipe -> recipe.getOutput(world.getRegistryManager()).copy())
                .orElse(ItemStack.EMPTY);
    }

    private static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && AbstractFurnaceBlockEntity.canUseAsFuel(stack)
                && !stack.getItem().hasRecipeRemainder(); // tránh xô dung nham
    }

    /** Có nấu được món này ngay bây giờ không (đồ hun khói cần nhiên liệu). */
    private boolean canCookNow(ItemStack stack, boolean hasFuel) {
        if (stack.isEmpty()) return false;
        if (stack.isOf(Items.WHEAT)) return true;
        return hasFuel && !smokedResult(npc.getWorld(), stack).isEmpty();
    }

    private boolean hasFuel() {
        if (npc.getFuelUnits() > 0) return true;
        if (!(npc.getWorld().getBlockEntity(chest) instanceof Inventory inv)) return false;
        for (int i = 0; i < inv.size(); i++) if (isFuel(inv.getStack(i))) return true;
        return false;
    }

    // ===== Goal =====

    @Override
    public boolean canStart() {
        if (npc.sleeping.isSleeping() || npc.needsToDeposit()) return false;
        long now = npc.getWorld().getTime();
        if (now < nextScan) return false;
        nextScan = now + 60;
        chest = npc.findWorkChest();
        if (chest == null) return false;
        station = findStation();
        if (!npc.getIngredients().isEmpty()) {
            phase = Phase.COOK;
            return true;
        }
        source = findSource();
        if (source == null) return false;
        phase = Phase.GATHER;
        return true;
    }

    /** Tìm trạm nấu gần rương nhất theo thứ tự ưu tiên: Lò hun khói → Lò nung → Bếp lửa trại → Rương. */
    private BlockPos findStation() {
        World world = npc.getWorld();
        int r = ModConfig.get().cookWorkRadius;
        BlockPos best = null;
        int bestTier = Integer.MAX_VALUE;
        double bestDist = Double.MAX_VALUE;
        for (BlockEntity be : BlockEntityScan.inBox(world, chest, r, 4)) {
            int tier = stationTier(be);
            if (tier == Integer.MAX_VALUE) continue;
            double dist = chest.getSquaredDistance(be.getPos());
            if (tier < bestTier || (tier == bestTier && dist < bestDist)) {
                bestTier = tier;
                bestDist = dist;
                best = be.getPos().toImmutable();
            }
        }
        return best != null ? best : chest;
    }

    private static int stationTier(BlockEntity be) {
        if (be instanceof SmokerBlockEntity) return 0;
        if (be instanceof FurnaceBlockEntity) return 1;
        if (be instanceof CampfireBlockEntity) return 2;
        return Integer.MAX_VALUE;
    }

    /** Tìm rương gần nhất còn nguyên liệu nấu được. */
    private BlockPos findSource() {
        World world = npc.getWorld();
        boolean fuel = hasFuel();
        boolean rawWithoutFuel = false;
        if (world.getBlockEntity(chest) instanceof Inventory own) {
            if (takeable(own, 0, fuel) > 0) return chest;
            rawWithoutFuel = takeable(own, 0, true) > 0;
        }
        int r = ModConfig.get().cookWorkRadius;
        int reserve = ModConfig.get().cookLeaveInSupplierChest;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockEntity be : com.example.helper.BlockEntityScan.inBox(world, chest, r, 4)) {
            BlockPos pos = be.getPos();
            if (!isSupplierChest(be)) continue;
            Inventory inv = (Inventory) be;
            if (takeable(inv, reserve, fuel) == 0) {
                rawWithoutFuel |= !fuel && takeable(inv, reserve, true) > 0;
                continue;
            }
            double distance = npc.getBlockPos().getSquaredDistance(pos);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.toImmutable();
            }
        }
        if (best == null) {
            npc.display.report(npc, rawWithoutFuel ? "Rương đầu bếp thiếu nhiên liệu (than, gỗ...)"
                    : "Chưa có nguyên liệu sống để nấu");
        }
        return best;
    }

    private boolean isSupplierChest(BlockEntity be) {
        // Không lấy từ Rương Thợ chăn nuôi: lúa mì/cà rốt trong đó là thức ăn cho vật nuôi
        return (be instanceof FarmerChestBlockEntity || be instanceof FishermanChestBlockEntity)
                && ((ProfessionChestBlockEntity) be).isAccessibleBy(npc.getOwnerUUID());
    }

    /** Số món lấy được từ rương, sau khi chừa lại reserve món mỗi loại. */
    private int takeable(Inventory inv, int reserve, boolean fuel) {
        int total = 0;
        for (int available : availableByItem(inv, reserve, fuel).values()) total += available;
        return total;
    }

    private Map<Item, Integer> availableByItem(Inventory inv, int reserve, boolean fuel) {
        Map<Item, Integer> counts = new HashMap<>();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (canCookNow(stack, fuel)) counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        counts.replaceAll((item, count) -> Math.max(0, count - reserve));
        // Lúa mì chỉ lấy theo bội số 3 (đủ làm bánh mì), tránh vòng lặp mang 1-2 hạt qua lại
        counts.computeIfPresent(Items.WHEAT, (item, count) -> count - count % WHEAT_PER_BREAD);
        return counts;
    }

    @Override
    public void start() {
        done = false;
        litByNpc = false;
        walkTicks = 0;
        cookTicks = 0;
        if (station == null) station = chest;
        moveTo(phase == Phase.GATHER ? source : station);
    }

    private void moveTo(BlockPos pos) {
        npc.getNavigation().startMovingTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
    }

    @Override
    public void tick() {
        BlockPos target = phase == Phase.GATHER ? source : station;
        if (!DeliveryTarget.canReach(npc, target)) {
            if (++walkTicks >= MAX_WALK_TICKS) {
                npc.display.report(npc, phase == Phase.GATHER ? "Không có đường tới rương nguyên liệu"
                        : "Không có đường tới bếp nấu");
                done = true;
            } else if (walkTicks % 20 == 0) {
                moveTo(target);
            }
            return;
        }
        walkTicks = 0;
        npc.resetIdle(); // đứng nấu không phải "rảnh", tránh bị kéo đi nộp hàng giữa mẻ
        npc.getNavigation().stop();
        npc.getLookControl().lookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (phase == Phase.GATHER) gather();
        else cook();
    }

    private void gather() {
        if (!(npc.getWorld().getBlockEntity(source) instanceof Inventory inv)) {
            done = true;
            return;
        }
        int reserve = source.equals(chest) ? 0 : ModConfig.get().cookLeaveInSupplierChest;
        Map<Item, Integer> allowed = availableByItem(inv, reserve, hasFuel());
        int room = MAX_INGREDIENTS - WorkerNpcEntity.countItems(npc.getIngredients());
        for (int i = 0; i < inv.size() && room > 0; i++) {
            ItemStack stack = inv.getStack(i);
            int canTake = Math.min(room, Math.min(stack.getCount(), allowed.getOrDefault(stack.getItem(), 0)));
            if (canTake <= 0) continue;
            ItemStack taken = stack.split(canTake);
            ItemStack rest = DepositCargoGoal.insert(npc.getIngredients(), taken);
            stack.increment(rest.getCount());
            int moved = canTake - rest.getCount();
            allowed.merge(taken.getItem(), -moved, Integer::sum);
            room -= moved;
            inv.markDirty();
        }
        if (npc.getIngredients().isEmpty()) {
            done = true;
            return;
        }
        // Nếu đang ở rương đầu bếp, tiện tay lấy trước nhiên liệu (gỗ/than) để mang sang lò bật bếp
        if (npc.getFuelUnits() <= 0 && needsFire(npc.getWorld())) refuel();
        phase = Phase.COOK;
        moveTo(station);
    }

    private boolean needsFire(World world) {
        SimpleInventory ingredients = npc.getIngredients();
        for (int i = 0; i < ingredients.size(); i++) {
            ItemStack s = ingredients.getStack(i);
            if (!s.isEmpty() && !smokedResult(world, s).isEmpty()) return true;
        }
        return false;
    }

    private void cook() {
        if (!(npc.getWorld() instanceof ServerWorld world)) return;
        if (!ensureStationLit(world)) return;

        cookTicks++;
        if (cookTicks % 10 == 0) {
            world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    station.getX() + 0.5, station.getY() + 1.1, station.getZ() + 0.5, 1, 0.15, 0.05, 0.15, 0.01);
        }
        if (cookTicks < ModConfig.get().cookTicksPerItem) return;
        cookTicks = 0;

        ItemStack result = cookOne(world);
        if (result.isEmpty()) {
            finishBatch();
            return;
        }
        npc.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        world.playSound(null, station,
                world.getBlockEntity(station) instanceof CampfireBlockEntity
                        ? SoundEvents.BLOCK_CAMPFIRE_CRACKLE : SoundEvents.BLOCK_SMOKER_SMOKE,
                SoundCategory.BLOCKS, 0.8F, 1.0F);
        ItemStack rest = DepositCargoGoal.insert(npc.getCargo(), result);
        if (!rest.isEmpty()) npc.dropStack(rest);
        if (npc.getIngredients().isEmpty()) finishBatch();
    }

    /** Tự bật lò (Smoker/Furnace) hoặc châm lửa bếp trại (Campfire) khi có nhiên liệu từ rương. */
    private boolean ensureStationLit(ServerWorld world) {
        if (station == null || station.equals(chest) || !world.isChunkLoaded(station)) return true;
        BlockState state = world.getBlockState(station);
        BlockEntity be = world.getBlockEntity(station);
        if (be instanceof AbstractFurnaceBlockEntity && state.contains(AbstractFurnaceBlock.LIT)) {
            if (needsFire(world) && npc.getFuelUnits() <= 0 && !refuel()) {
                npc.display.report(npc, "Rương đầu bếp thiếu nhiên liệu (than, gỗ...)");
                finishBatch();
                return false;
            }
            if (!state.get(AbstractFurnaceBlock.LIT)) {
                world.setBlockState(station, state.with(AbstractFurnaceBlock.LIT, true), Block.NOTIFY_ALL);
                litByNpc = true;
            }
        } else if (be instanceof CampfireBlockEntity && state.contains(CampfireBlock.LIT) && !state.get(CampfireBlock.LIT)) {
            if (npc.getFuelUnits() <= 0 && !refuel()) {
                npc.display.report(npc, "Rương đầu bếp thiếu nhiên liệu (than, gỗ...) để nhóm bếp");
                finishBatch();
                return false;
            }
            world.setBlockState(station, state.with(CampfireBlock.LIT, true), Block.NOTIFY_ALL);
            world.playSound(null, station, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 0.7F, 1.0F);
        }
        return true;
    }

    private void extinguishStationIfLit() {
        if (!litByNpc || station == null || !(npc.getWorld() instanceof ServerWorld world)) return;
        litByNpc = false;
        if (!world.isChunkLoaded(station)) return;
        BlockState state = world.getBlockState(station);
        if (world.getBlockEntity(station) instanceof AbstractFurnaceBlockEntity
                && state.contains(AbstractFurnaceBlock.LIT) && state.get(AbstractFurnaceBlock.LIT)) {
            world.setBlockState(station, state.with(AbstractFurnaceBlock.LIT, false), Block.NOTIFY_ALL);
        }
    }

    /** Nấu 1 món từ nguyên liệu đang mang; trả về EMPTY nếu không nấu được gì nữa. */
    private ItemStack cookOne(World world) {
        SimpleInventory ingredients = npc.getIngredients();
        if (countOf(ingredients, Items.WHEAT) >= WHEAT_PER_BREAD) {
            removeItem(ingredients, Items.WHEAT, WHEAT_PER_BREAD);
            return new ItemStack(Items.BREAD);
        }
        for (int i = 0; i < ingredients.size(); i++) {
            ItemStack stack = ingredients.getStack(i);
            if (stack.isEmpty()) continue;
            ItemStack result = smokedResult(world, stack);
            if (result.isEmpty()) continue;
            if (npc.getFuelUnits() <= 0 && !refuel()) {
                npc.display.report(npc, "Rương đầu bếp thiếu nhiên liệu (than, gỗ...)");
                return ItemStack.EMPTY;
            }
            stack.decrement(1);
            ingredients.markDirty();
            npc.setFuelUnits(npc.getFuelUnits() - 1);
            return result;
        }
        return ItemStack.EMPTY;
    }

    /** Lấy 1 nhiên liệu từ Rương Đầu bếp. */
    private boolean refuel() {
        if (!(npc.getWorld().getBlockEntity(chest) instanceof Inventory inv)) return false;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!isFuel(stack)) continue;
            stack.decrement(1);
            inv.markDirty();
            npc.setFuelUnits(ModConfig.get().cookItemsPerFuel);
            return true;
        }
        return false;
    }

    /** Kết thúc mẻ: trả nguyên liệu dư về Rương Đầu bếp, bật cờ đi nộp món chín. */
    private void finishBatch() {
        extinguishStationIfLit();
        if (npc.getWorld().getBlockEntity(chest) instanceof Inventory inv) {
            DepositCargoGoal.moveAll(npc.getIngredients(), inv);
        }
        if (!npc.getCargo().isEmpty()) {
            npc.setNeedsToDeposit(true);
            NpcFeedback.success(npc);
        }
        done = true;
    }

    private static int countOf(Inventory inv, Item item) {
        int count = 0;
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isOf(item)) count += inv.getStack(i).getCount();
        }
        return count;
    }

    private static void removeItem(Inventory inv, Item item, int amount) {
        for (int i = 0; i < inv.size() && amount > 0; i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isOf(item)) continue;
            int take = Math.min(amount, stack.getCount());
            stack.decrement(take);
            amount -= take;
        }
        inv.markDirty();
    }

    @Override
    public boolean shouldContinue() {
        return !done && !npc.sleeping.isSleeping();
    }

    @Override
    public void stop() {
        extinguishStationIfLit();
        npc.getNavigation().stop();
        source = null;
        station = null;
        npc.resetIdle();
    }
}
