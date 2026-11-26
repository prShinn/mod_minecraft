package com.example.ai.farmer;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Quét mảnh ruộng liền quanh rương neo của Nông dân. */
class FarmFieldTest {
    private static final BlockPos CHEST = new BlockPos(0, 64, 0);

    private static Set<BlockPos> discover(Set<BlockPos> farmland) {
        return FarmField.discover(CHEST, farmland::contains);
    }

    /** Hàng ruộng thẳng từ x0 tới x1 (cùng tầng y, z). */
    private static void row(Set<BlockPos> land, int x0, int x1, int y, int z) {
        for (int x = x0; x <= x1; x++) land.add(new BlockPos(x, y, z));
    }

    @Test
    void wideFieldBeyondOldRadiusIsCovered() {
        Set<BlockPos> land = new HashSet<>();
        row(land, 2, 38, 63, 0); // ruộng liền kéo từ sát rương ra 38 block
        Set<BlockPos> field = discover(land);
        assertTrue(field.contains(new BlockPos(38, 63, 0)), "ô xa 38 block nhưng liền mảnh");
        assertEquals(land.size(), field.size());
    }

    @Test
    void waterChannelGapIsBridged() {
        Set<BlockPos> land = new HashSet<>();
        row(land, 2, 10, 63, 0);
        row(land, 12, 30, 63, 0); // x = 11 là mương nước
        assertTrue(discover(land).contains(new BlockPos(30, 63, 0)));
    }

    @Test
    void twoBlockGapSplitsField() {
        Set<BlockPos> land = new HashSet<>();
        row(land, 2, 10, 63, 0);
        row(land, 14, 30, 63, 0); // cách 3 ô và ngoài vùng gốc 12 block → mảnh khác
        Set<BlockPos> field = discover(land);
        assertTrue(field.contains(new BlockPos(10, 63, 0)));
        assertFalse(field.contains(new BlockPos(30, 63, 0)), "mảnh tách rời ngoài vùng gốc không bị nhận");
    }

    @Test
    void terracesClimbOverManyLevels() {
        Set<BlockPos> land = new HashSet<>();
        // 8 bậc thang, mỗi bậc rộng 3 ô, cao hơn bậc trước 1 tầng → bậc cuối cao hơn rương 7 tầng
        for (int step = 0; step < 8; step++) row(land, 2 + step * 3, 4 + step * 3, 63 + step, 0);
        Set<BlockPos> field = discover(land);
        assertTrue(field.contains(new BlockPos(25, 70, 0)), "bậc thang thứ 8");
        assertEquals(land.size(), field.size());
    }

    @Test
    void terraceWallTwoHighStillLinks() {
        Set<BlockPos> land = new HashSet<>();
        row(land, 2, 10, 63, 0);
        row(land, 11, 30, 65, 0); // tường bậc cao 2
        assertTrue(discover(land).contains(new BlockPos(30, 65, 0)));
    }

    @Test
    void boundsAndCapAreRespected() {
        Set<BlockPos> land = new HashSet<>();
        row(land, 2, 60, 63, 0);
        Set<BlockPos> field = discover(land);
        assertTrue(field.contains(new BlockPos(FarmField.MAX_RADIUS, 63, 0)));
        assertFalse(field.contains(new BlockPos(FarmField.MAX_RADIUS + 1, 63, 0)), "vượt 40 block");

        Set<BlockPos> huge = new HashSet<>();
        for (int x = -40; x <= 40; x++) for (int z = -40; z <= 40; z++) huge.add(new BlockPos(x, 63, z));
        assertEquals(FarmField.MAX_PLOTS, discover(huge).size(), "giới hạn số ô để không lag");
    }

    @Test
    void noFarmlandNearChestMeansEmptyField() {
        Set<BlockPos> land = new HashSet<>();
        row(land, 20, 30, 63, 0);
        assertTrue(discover(land).isEmpty());
    }
}
