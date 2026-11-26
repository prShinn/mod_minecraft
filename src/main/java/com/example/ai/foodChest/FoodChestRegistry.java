package com.example.ai.foodChest;

import com.example.ExampleMod;
import com.example.helper.BlockEntityScan;
import com.example.helper.FoodChestHelper;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Sổ đăng ký rương thức ăn của cả thế giới (mỗi dimension một sổ, lưu cùng save).
 * Rương thức ăn là kho CHUNG của làng: đặt vài cái ở khu trung tâm, làng rộng thì đặt thêm.
 * NPC tra sổ để tìm rương gần nhất (vài chục phần tử) thay vì quét block quanh mình.
 * <ul>
 *     <li>Rương Thức ăn của mod: ghi sổ khi đặt, xoá khi phá.</li>
 *     <li>Rương thường có sách đánh dấu FOOD CHEST ở ô đầu: NPC đi ngang (trong bán kính nhỏ) thì tự ghi sổ.</li>
 *     <li>Mục không còn là rương thức ăn (chunk đang nạp) thì tự xoá khi tra.</li>
 * </ul>
 */
public class FoodChestRegistry extends PersistentState {
    private static final String ID = ExampleMod.MODID + "_food_chests";

    private final Set<BlockPos> chests = new HashSet<>();

    public static FoodChestRegistry get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(FoodChestRegistry::fromNbt, FoodChestRegistry::new, ID);
    }

    public void register(BlockPos pos) {
        if (chests.add(pos.toImmutable())) markDirty();
    }

    public void remove(BlockPos pos) {
        if (chests.remove(pos)) markDirty();
    }

    public int size() {
        return chests.size();
    }

    /** Ghi sổ các rương thức ăn (kể cả rương thường có sách đánh dấu) quanh center — rẻ, chỉ duyệt block entity. */
    public void discover(World world, BlockPos center, int radius, int height) {
        for (BlockEntity be : BlockEntityScan.inBox(world, center, radius, height)) {
            if (FoodChestHelper.isFoodChest(be.getPos(), world)) register(be.getPos());
        }
    }

    /**
     * Rương thức ăn gần {@code from} nhất trong tầm (ngang ±range, dọc ±vertical) thoả {@code usable}.
     * Chunk chưa nạp thì bỏ qua (giữ trong sổ); chunk đang nạp mà không còn là rương thức ăn thì xoá khỏi sổ.
     */
    @Nullable
    public BlockPos nearest(World world, BlockPos from, int range, int vertical, Predicate<BlockPos> usable) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        List<BlockPos> stale = new ArrayList<>();
        for (BlockPos pos : chests) {
            if (Math.abs(pos.getX() - from.getX()) > range || Math.abs(pos.getZ() - from.getZ()) > range
                    || Math.abs(pos.getY() - from.getY()) > vertical || !world.isChunkLoaded(pos)) continue;
            if (!FoodChestHelper.isValidFoodChest(pos, world)) {
                stale.add(pos);
                continue;
            }
            double dist = from.getSquaredDistance(pos);
            if (dist < bestDist && usable.test(pos)) {
                bestDist = dist;
                best = pos;
            }
        }
        if (!stale.isEmpty()) {
            stale.forEach(chests::remove);
            markDirty();
        }
        return best;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putLongArray("Chests", chests.stream().mapToLong(BlockPos::asLong).toArray());
        return nbt;
    }

    public static FoodChestRegistry fromNbt(NbtCompound nbt) {
        FoodChestRegistry registry = new FoodChestRegistry();
        for (long packed : nbt.getLongArray("Chests")) registry.chests.add(BlockPos.fromLong(packed));
        return registry;
    }
}
