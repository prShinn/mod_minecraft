package com.example.build;

import com.example.ExampleMod;
import com.example.block.BlueprintBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Sổ đăng ký công trình (block bản vẽ đã đặt) theo từng dimension.
 * Chỉ lưu vị trí + chủ; tiến độ nằm trong BlueprintBlockEntity (single writer).
 * Tránh phải quét hàng chục nghìn block để tìm công trình.
 */
public class BuildSiteState extends PersistentState {
    private static final String ID = ExampleMod.MODID + "_build_sites";
    /** Số công trình tối đa mỗi chủ trong 1 dimension. */
    public static final int MAX_SITES_PER_OWNER = 10;

    private record Site(BlockPos pos, @Nullable UUID owner) {}

    private final List<Site> sites = new ArrayList<>();

    public static BuildSiteState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(BuildSiteState::fromNbt, BuildSiteState::new, ID);
    }

    public void add(BlockPos pos, @Nullable UUID owner) {
        BlockPos immutable = pos.toImmutable();
        sites.removeIf(s -> s.pos.equals(immutable));
        sites.add(new Site(immutable, owner));
        markDirty();
    }

    public void remove(BlockPos pos) {
        if (sites.removeIf(s -> s.pos.equals(pos))) markDirty();
    }

    /** Số công trình của owner; dọn trước các site đã mất block (chunk đang nạp) để không đếm site rác. */
    public int countFor(ServerWorld world, UUID owner) {
        if (sites.removeIf(s -> world.isChunkLoaded(s.pos)
                && !(world.getBlockState(s.pos).getBlock() instanceof BlueprintBlock))) {
            markDirty();
        }
        int count = 0;
        for (Site s : sites) if (owner.equals(s.owner)) count++;
        return count;
    }

    /**
     * Công trình của owner (null = mọi chủ) trong bán kính quanh center, gần nhất trước.
     * Tự dọn site mà chunk đang load nhưng không còn block bản vẽ.
     */
    public List<BlockPos> sitesFor(ServerWorld world, @Nullable UUID owner, BlockPos center, double radius) {
        double radiusSq = radius * radius;
        List<BlockPos> result = new ArrayList<>();
        Iterator<Site> it = sites.iterator();
        boolean dirty = false;
        while (it.hasNext()) {
            Site s = it.next();
            if (world.isChunkLoaded(s.pos) && !(world.getBlockState(s.pos).getBlock() instanceof BlueprintBlock)) {
                it.remove();
                dirty = true;
                continue;
            }
            if ((owner == null || s.owner == null || s.owner.equals(owner)) && s.pos.getSquaredDistance(center) <= radiusSq) {
                result.add(s.pos);
            }
        }
        if (dirty) markDirty();
        result.sort(Comparator.comparingDouble(center::getSquaredDistance));
        return result;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (Site s : sites) {
            NbtCompound e = new NbtCompound();
            e.putLong("Pos", s.pos.asLong());
            if (s.owner != null) e.putUuid("Owner", s.owner);
            list.add(e);
        }
        nbt.put("Sites", list);
        return nbt;
    }

    public static BuildSiteState fromNbt(NbtCompound nbt) {
        BuildSiteState state = new BuildSiteState();
        NbtList list = nbt.getList("Sites", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound e = list.getCompound(i);
            if (e.contains("Pos")) {
                state.sites.add(new Site(BlockPos.fromLong(e.getLong("Pos")),
                        e.containsUuid("Owner") ? e.getUuid("Owner") : null));
            }
        }
        return state;
    }
}
