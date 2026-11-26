package com.example.block;

import com.example.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/** BlockEntity cho Rương Tiều phu. */
public class LumberjackChestBlockEntity extends ProfessionChestBlockEntity {
    /** Các mức bán kính vùng chặt (block mỗi phía tính từ rương) khi Shift + chuột phải tay không: 3 → 5 → 8 → tắt. */
    public static final int[] RADIUS_STEPS = {3, 5, 8};
    public static final int DEFAULT_CUT_RADIUS = 5;
    public static final int MAX_CUT_RADIUS = 8;
    public static final int CUT_BELOW = 6;
    public static final int CUT_ABOVE = 64;
    /** Mặc định bật vùng chặt khi đặt rương mới; Shift + chuột phải tay không để tắt/bật. */
    private boolean cuttingEnabled = true;
    /** Bán kính vùng chặt riêng của rương này. */
    private int cutRadius = DEFAULT_CUT_RADIUS;

    public boolean allowsCutting(BlockPos target) {
        return cuttingEnabled && Math.abs(target.getX() - pos.getX()) <= cutRadius
                && Math.abs(target.getZ() - pos.getZ()) <= cutRadius
                && target.getY() >= pos.getY() - CUT_BELOW
                && target.getY() <= pos.getY() + CUT_ABOVE;
    }

    public int getCutRadius() {
        return cutRadius;
    }

    /** Shift + chuột phải tay không: 3 → 5 → 8 → tắt → 3. Trả về bán kính mới, 0 = đã tắt vùng chặt. */
    public int cycleCutRadius() {
        if (!cuttingEnabled) {
            cuttingEnabled = true;
            cutRadius = RADIUS_STEPS[0];
        } else {
            int next = -1;
            for (int step : RADIUS_STEPS) {
                if (step > cutRadius) {
                    next = step;
                    break;
                }
            }
            if (next < 0) cuttingEnabled = false; // đã ở mức lớn nhất → tắt
            else cutRadius = next;
        }
        markDirty();
        return cuttingEnabled ? cutRadius : 0;
    }

    @Override
    public void readNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.readNbt(nbt);
        cuttingEnabled = !nbt.contains("CuttingEnabled") || nbt.getBoolean("CuttingEnabled");
        // Rương cũ chưa lưu bán kính → dùng mặc định mới (5)
        cutRadius = nbt.contains("CutRadius")
                ? Math.max(1, Math.min(MAX_CUT_RADIUS, nbt.getInt("CutRadius"))) : DEFAULT_CUT_RADIUS;
    }

    @Override
    public void writeNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putBoolean("CuttingEnabled", cuttingEnabled);
        nbt.putInt("CutRadius", cutRadius);
    }

    public LumberjackChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LUMBERJACK_CHEST_BE, pos, state);
    }
}
