package com.example.render;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Áo Binh lính phải đúng khuôn UV dân làng 64×64: không che mặt, phủ kín áo choàng/tay/chân. */
class SoldierTextureTest {
    private static BufferedImage load() throws Exception {
        try (InputStream in = SoldierTextureTest.class.getResourceAsStream("/assets/datnc_mod/textures/entity/soldier.png")) {
            assertNotNull(in, "thiếu textures/entity/soldier.png");
            return ImageIO.read(in);
        }
    }

    private static boolean opaque(BufferedImage img, int x, int y, int w, int h) {
        for (int j = y; j < y + h; j++) for (int i = x; i < x + w; i++) if ((img.getRGB(i, j) >>> 24) != 255) return false;
        return true;
    }

    private static boolean transparent(BufferedImage img, int x, int y, int w, int h) {
        for (int j = y; j < y + h; j++) for (int i = x; i < x + w; i++) if ((img.getRGB(i, j) >>> 24) != 0) return false;
        return true;
    }

    @Test
    void layoutMatchesVillagerUv() throws Exception {
        BufferedImage img = load();
        assertEquals(64, img.getWidth());
        assertEquals(64, img.getHeight());
        assertTrue(transparent(img, 0, 0, 32, 18), "đầu + mũi phải trong suốt (giữ mặt dân làng)");
        assertTrue(transparent(img, 40, 11, 8, 7), "phần dưới lớp mũ phía trước không được che mặt");
        assertTrue(opaque(img, 0, 44, 28, 20), "áo choàng (robe) phải phủ kín để che áo biome");
        assertTrue(opaque(img, 44, 26, 16, 10), "tay áo + bao tay");
        assertTrue(transparent(img, 44, 36, 16, 2), "bàn tay để lộ da");
        assertTrue(opaque(img, 0, 26, 16, 12), "chân: quần + ủng");
        assertTrue(transparent(img, 30, 47, 34, 17), "vành mũ rơm (hat_rim) phải trong suốt");
    }
}
