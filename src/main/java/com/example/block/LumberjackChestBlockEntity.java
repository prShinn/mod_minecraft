package com.example.block;

import com.example.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/** BlockEntity cho Rương Tiều phu. */
public class LumberjackChestBlockEntity extends ProfessionChestBlockEntity {
    public static final int CUT_RADIUS = 8;
    public static final int CUT_BELOW = 6;
    public static final int CUT_ABOVE = 64;
    /** Mặc định bật vùng chặt khi đặt rương mới; Shift + chuột phải tay không để tắt/bật. */
    private boolean cuttingEnabled = true;

    public boolean allowsCutting(BlockPos target) {
        return cuttingEnabled && Math.abs(target.getX() - pos.getX()) <= CUT_RADIUS
                && Math.abs(target.getZ() - pos.getZ()) <= CUT_RADIUS
                && target.getY() >= pos.getY() - CUT_BELOW
                && target.getY() <= pos.getY() + CUT_ABOVE;
    }

    public boolean toggleCutting() {
        cuttingEnabled = !cuttingEnabled;
        markDirty();
        return cuttingEnabled;
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.readNbt(nbt);
        cuttingEnabled = !nbt.contains("CuttingEnabled") || nbt.getBoolean("CuttingEnabled");
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putBoolean("CuttingEnabled", cuttingEnabled);
    }

    public LumberjackChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LUMBERJACK_CHEST_BE, pos, state);
    }
}
