package com.example.block;

import com.example.ExampleMod;
import com.example.registry.ModBlocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Sổ đăng ký Cờ Tuần Tra theo từng dimension (lưu cùng world save).
 * Thứ tự trong danh sách = thứ tự đặt cờ → quyết định hướng đi tuần của Soldier.
 */
public class PatrolFlagState extends PersistentState {
    private static final String ID = ExampleMod.MODID + "_patrol_flags";

    /** Số cờ tối đa của 1 chủ trong cùng 1 khu vực (bán kính {@link #RANGE}). */
    public static final int MAX_FLAGS = 3;
    /** Bán kính (block) gom cờ thành 1 tuyến và Soldier tìm tuyến. */
    public static final double RANGE = 48.0;

    private record Flag(BlockPos pos, UUID owner) {}

    private final List<Flag> flags = new ArrayList<>();

    public static PatrolFlagState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(PatrolFlagState::fromNbt, PatrolFlagState::new, ID);
    }

    public void add(BlockPos pos, UUID owner) {
        BlockPos immutable = pos.toImmutable();
        flags.removeIf(f -> f.pos.equals(immutable));
        flags.add(new Flag(immutable, owner));
        markDirty();
    }

    public void remove(BlockPos pos) {
        if (flags.removeIf(f -> f.pos.equals(pos))) markDirty();
    }

    /** Số cờ của owner trong bán kính RANGE quanh center (dùng giới hạn khi đặt). */
    public int countNear(UUID owner, BlockPos center) {
        double rangeSq = RANGE * RANGE;
        int count = 0;
        for (Flag f : flags) {
            if (f.owner.equals(owner) && f.pos.getSquaredDistance(center) <= rangeSq) count++;
        }
        return count;
    }

    /**
     * Tuyến tuần tra của owner quanh center, theo thứ tự đặt cờ.
     * Tự dọn các cờ đã mất (chunk đang load mà tại đó không còn block cờ).
     */
    public List<BlockPos> routeFor(ServerWorld world, UUID owner, BlockPos center) {
        double rangeSq = RANGE * RANGE;
        List<BlockPos> route = new ArrayList<>();
        Iterator<Flag> it = flags.iterator();
        boolean dirty = false;
        while (it.hasNext()) {
            Flag f = it.next();
            if (world.isChunkLoaded(f.pos) && !world.getBlockState(f.pos).isOf(ModBlocks.PATROL_FLAG)) {
                it.remove();
                dirty = true;
                continue;
            }
            if (f.owner.equals(owner) && f.pos.getSquaredDistance(center) <= rangeSq) route.add(f.pos);
        }
        if (dirty) markDirty();
        return route;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (Flag f : flags) {
            NbtCompound e = new NbtCompound();
            e.putLong("Pos", f.pos.asLong());
            e.putUuid("Owner", f.owner);
            list.add(e);
        }
        nbt.put("Flags", list);
        return nbt;
    }

    public static PatrolFlagState fromNbt(NbtCompound nbt) {
        PatrolFlagState state = new PatrolFlagState();
        NbtList list = nbt.getList("Flags", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound e = list.getCompound(i);
            if (e.contains("Pos") && e.containsUuid("Owner")) {
                state.flags.add(new Flag(BlockPos.fromLong(e.getLong("Pos")), e.getUuid("Owner")));
            }
        }
        return state;
    }
}
