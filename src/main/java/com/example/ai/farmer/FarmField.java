package com.example.ai.farmer;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Mảnh ruộng của một Nông dân: mọi ô đất cày LIỀN với ruộng quanh rương neo.
 * <ul>
 *     <li>Gốc: ô đất cày trong {@link #SEED_RADIUS} block ngang, lệch ±{@link #SEED_HEIGHT} tầng quanh rương.</li>
 *     <li>Lan ra ô kế bên, cho phép cách {@link #LINK_GAP} - 1 ô (mương nước, lối đi, hàng rào)
 *         và lệch ±{@link #LINK_STEP} tầng mỗi bước (ruộng bậc thang).</li>
 *     <li>Giới hạn {@link #MAX_RADIUS} block ngang, ±{@link #MAX_HEIGHT} tầng quanh rương và {@link #MAX_PLOTS} ô.</li>
 * </ul>
 * Logic thuần (không đụng World) để test được: {@code isFarmland} do NPC cung cấp.
 */
public final class FarmField {
    public static final int SEED_RADIUS = 12;
    public static final int SEED_HEIGHT = 3;
    public static final int LINK_GAP = 2;
    public static final int LINK_STEP = 2;
    public static final int MAX_RADIUS = 40;
    public static final int MAX_HEIGHT = 16;
    public static final int MAX_PLOTS = 1500;

    private FarmField() {
    }

    public static Set<BlockPos> discover(BlockPos chest, Predicate<BlockPos> isFarmland) {
        Set<BlockPos> field = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos pos : BlockPos.iterate(chest.add(-SEED_RADIUS, -SEED_HEIGHT, -SEED_RADIUS),
                chest.add(SEED_RADIUS, SEED_HEIGHT, SEED_RADIUS))) {
            if (field.size() >= MAX_PLOTS) break;
            if (isFarmland.test(pos)) {
                BlockPos plot = pos.toImmutable();
                field.add(plot);
                queue.add(plot);
            }
        }
        BlockPos.Mutable next = new BlockPos.Mutable();
        // Ô đã đọc mà không phải đất cày: nhớ lại để các ô ruộng lân cận không đọc lại (giảm ~10 lần số lần đọc block)
        it.unimi.dsi.fastutil.longs.LongOpenHashSet rejected = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        while (!queue.isEmpty() && field.size() < MAX_PLOTS) {
            BlockPos plot = queue.poll();
            for (int dx = -LINK_GAP; dx <= LINK_GAP; dx++) {
                for (int dz = -LINK_GAP; dz <= LINK_GAP; dz++) {
                    // 8 ô kề + 4 ô cách 1 theo hàng thẳng (mương nước) — bỏ ô chéo xa để giảm một nửa số lần đọc block
                    if ((dx == 0 && dz == 0) || (Math.abs(dx) == LINK_GAP && dz != 0) || (Math.abs(dz) == LINK_GAP && dx != 0)) continue;
                    for (int dy = -LINK_STEP; dy <= LINK_STEP; dy++) {
                        next.set(plot.getX() + dx, plot.getY() + dy, plot.getZ() + dz);
                        if (!inBounds(chest, next) || field.contains(next)) continue;
                        long key = next.asLong();
                        if (rejected.contains(key)) continue;
                        if (!isFarmland.test(next)) {
                            rejected.add(key);
                            continue;
                        }
                        BlockPos found = next.toImmutable();
                        field.add(found);
                        queue.add(found);
                        if (field.size() >= MAX_PLOTS) return Collections.unmodifiableSet(field);
                    }
                }
            }
        }
        return Collections.unmodifiableSet(field);
    }

    public static boolean inBounds(BlockPos chest, BlockPos pos) {
        return Math.abs(pos.getX() - chest.getX()) <= MAX_RADIUS
                && Math.abs(pos.getZ() - chest.getZ()) <= MAX_RADIUS
                && Math.abs(pos.getY() - chest.getY()) <= MAX_HEIGHT;
    }
}
