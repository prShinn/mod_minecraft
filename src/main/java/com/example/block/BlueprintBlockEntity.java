package com.example.block;

import com.example.build.BuildMaterial;
import com.example.build.StructurePlan;
import com.example.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.BitSet;
import java.util.UUID;

/**
 * Giữ tiến độ xây của 1 công trình (bitset các bước đã xong) và "claim" để 2 Thợ xây
 * không tranh cùng 1 công trình. Claim chỉ ở RAM — hết hạn tự nhả.
 */
public class BlueprintBlockEntity extends BlockEntity {
    private static final int CLAIM_TICKS = 200;

    private BitSet done = new BitSet();
    private UUID claimBy;
    private long claimUntil;

    public BlueprintBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BLUEPRINT_BE, pos, state);
    }

    public StructurePlan plan() {
        return ((BlueprintBlock) getCachedState().getBlock()).getPlan();
    }

    public Direction facing() {
        return getCachedState().get(BlueprintBlock.FACING);
    }

    public boolean isDone(int step) {
        return done.get(step);
    }

    public void markDone(int step) {
        done.set(step);
        markDirty();
    }

    public int doneCount() {
        return done.cardinality();
    }

    public int remaining() {
        return plan().steps().size() - doneCount();
    }

    /** Số vật liệu còn cần cho các bước chưa xong, theo thứ tự BuildMaterial. */
    public int[] remainingNeed() {
        int[] need = new int[BuildMaterial.values().length];
        var steps = plan().steps();
        for (int i = 0; i < steps.size(); i++) {
            if (!done.get(i)) need[steps.get(i).material().ordinal()]++;
        }
        return need;
    }

    /** Nhận/gia hạn quyền xây; false nếu NPC khác đang giữ. */
    public boolean claim(UUID npc, long now) {
        if (claimBy == null || claimBy.equals(npc) || now >= claimUntil) {
            claimBy = npc;
            claimUntil = now + CLAIM_TICKS;
            return true;
        }
        return false;
    }

    public void release(UUID npc) {
        if (npc.equals(claimBy)) {
            claimBy = null;
            claimUntil = 0;
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putLongArray("Done", done.toLongArray());
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        done = BitSet.valueOf(nbt.getLongArray("Done"));
    }
}
