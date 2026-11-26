package com.example.block;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vùng đào của Rương Thợ mỏ: vị trí, thứ tự, giới hạn. */
class QuarryConfigTest {
    private static final BlockPos CHEST = new BlockPos(100, 64, 200);

    @Test
    void areaStartsInFrontOfChestAlongDirection() {
        QuarryConfig c = new QuarryConfig(4, 3, 2, Direction.NORTH);
        // Bắc = -Z: ô đầu tiên ở ngay trước rương, tầng ngay dưới rương
        assertEquals(new BlockPos(99, 63, 199), c.positionAt(CHEST, 0));
        BlockBox box = c.box(CHEST);
        assertEquals(99, box.getMinX());
        assertEquals(101, box.getMaxX());
        assertEquals(196, box.getMinZ());
        assertEquals(199, box.getMaxZ());
        assertEquals(62, box.getMinY());
        assertEquals(63, box.getMaxY());
        assertFalse(box.contains(CHEST), "không bao giờ gồm ô rương");
    }

    @Test
    void everyCellVisitedOnceTopLayerFirst() {
        for (Direction dir : Direction.Type.HORIZONTAL) {
            QuarryConfig c = new QuarryConfig(5, 4, 3, dir);
            BlockBox box = c.box(CHEST);
            Set<BlockPos> seen = new HashSet<>();
            int lastY = Integer.MAX_VALUE;
            for (int i = 0; i < c.volume(); i++) {
                BlockPos p = c.positionAt(CHEST, i);
                assertTrue(box.contains(p), dir + " " + p);
                assertTrue(seen.add(p), "trùng ô " + p);
                assertTrue(p.getY() <= lastY, "đào từ trên xuống");
                lastY = p.getY();
            }
            assertEquals(c.volume(), seen.size());
            assertFalse(seen.contains(CHEST));
        }
    }

    @Test
    void nearRowsComeFirstWithinLayer() {
        QuarryConfig c = new QuarryConfig(6, 3, 1, Direction.EAST);
        int previous = -1;
        for (int i = 0; i < c.volume(); i++) {
            int distance = c.positionAt(CHEST, i).getX() - CHEST.getX();
            assertTrue(distance >= previous, "hàng gần rương trước");
            previous = distance;
        }
    }

    @Test
    void depthStartsOneBelowChestAndGoesDown() {
        QuarryConfig c = new QuarryConfig(3, 3, 5, Direction.SOUTH);
        BlockBox box = c.box(CHEST);
        assertEquals(63, box.getMaxY(), "tầng đầu = rương − 1");
        assertEquals(59, box.getMinY(), "sâu 5 tầng");
        assertEquals(63, c.positionAt(CHEST, 0).getY(), "đào tầng trên trước");
    }

    @Test
    void clampRejectsBadInput() {
        QuarryConfig c = new QuarryConfig(0, 999, -4, Direction.UP).clamp();
        assertEquals(QuarryConfig.MIN_SIZE, c.length());
        assertEquals(QuarryConfig.MAX_SIZE, c.width());
        assertEquals(QuarryConfig.MIN_SIZE, c.height());
        assertEquals(QuarryConfig.MAX_DEPTH, new QuarryConfig(1, 1, 500, Direction.EAST).clamp().height());
        assertEquals(Direction.NORTH, c.direction());
    }

    @Test
    void nbtRoundTrip() {
        QuarryConfig c = new QuarryConfig(12, 7, 9, Direction.WEST);
        assertEquals(c, QuarryConfig.fromNbt(c.toNbt()));
    }
}
