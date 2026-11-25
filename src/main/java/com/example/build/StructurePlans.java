package com.example.build;

import java.util.List;

import static com.example.build.BuildMaterial.BRICKS;
import static com.example.build.BuildMaterial.COBBLE;
import static com.example.build.BuildMaterial.GLASS;
import static com.example.build.BuildMaterial.LOGS;
import static com.example.build.BuildMaterial.PLANKS;

/**
 * Công trình có sẵn. Mỗi công trình dùng một TẬP vật liệu riêng → công thức shapeless
 * của bản vẽ (mỗi vật liệu x1) không trùng nhau:
 * Nhà {gỗ, đá, đá mịn} · Tường {đá, đá mịn} · Cổng {thân gỗ, gỗ, đá mịn} ·
 * Chòi {thân gỗ, đá, gỗ} · Kho {thân gỗ, gỗ} ·
 * Nhà gỗ nhỏ {thân gỗ, gỗ, kính} · Nhà đá {đá, đá mịn, kính} · Nhà hai tầng {thân gỗ, gỗ, đá, kính}.
 */
public final class StructurePlans {
    private StructurePlans() {}

    /** Nhà dân 7×5, cao 6: chân đá, tường gỗ, góc đá mịn, mái gỗ. Cửa 1×2 ở giữa mặt trước. */
    public static final StructurePlan HOUSE = StructurePlan.builder("house", "Nhà dân")
            .ring(0, 0, 6, 4, 0, 1, COBBLE)
            .ring(0, 0, 6, 4, 2, 3, PLANKS)
            .fill(0, 2, 0, 0, 3, 0, BRICKS).fill(6, 2, 0, 6, 3, 0, BRICKS)
            .fill(0, 2, 4, 0, 3, 4, BRICKS).fill(6, 2, 4, 6, 3, 4, BRICKS)
            .fill(0, 4, 0, 6, 4, 4, PLANKS)
            .fill(1, 5, 2, 5, 5, 2, BRICKS)
            .clear(3, 0, 0).clear(3, 1, 0)
            .clear(1, 2, 0).clear(5, 2, 0).clear(0, 2, 2).clear(6, 2, 2).clear(3, 2, 4)
            .build();

    /** Tường thành dài 10, dày 2, cao 4: thân đá, mặt đi tuần và lỗ châu mai đá mịn. */
    public static final StructurePlan WALL = StructurePlan.builder("wall", "Tường thành")
            .fill(0, 0, 0, 9, 2, 1, COBBLE)
            .fill(0, 3, 1, 9, 3, 1, BRICKS)
            .set(0, 3, 0, BRICKS).set(2, 3, 0, BRICKS).set(4, 3, 0, BRICKS)
            .set(6, 3, 0, BRICKS).set(8, 3, 0, BRICKS)
            .build();

    /** Cổng làng rộng 7, cao 7: hai cột thân gỗ, xà ngang gỗ, nóc đá mịn; lối đi 5×4. */
    public static final StructurePlan GATE = StructurePlan.builder("gate", "Cổng làng")
            .fill(0, 0, 0, 0, 5, 1, LOGS).fill(6, 0, 0, 6, 5, 1, LOGS)
            .fill(1, 4, 0, 5, 5, 1, PLANKS)
            .fill(0, 6, 0, 6, 6, 1, BRICKS)
            .build();

    /** Chòi canh 5×5, cao 8: 4 trụ thân gỗ, chân đá, sàn + lan can gỗ ở tầng 5–6. */
    public static final StructurePlan TOWER = StructurePlan.builder("tower", "Chòi canh")
            .ring(0, 0, 4, 4, 0, 1, COBBLE)
            .fill(0, 5, 0, 4, 5, 4, PLANKS)
            .ring(0, 0, 4, 4, 6, 6, PLANKS)
            .fill(0, 0, 0, 0, 7, 0, LOGS).fill(4, 0, 0, 4, 7, 0, LOGS)
            .fill(0, 0, 4, 0, 7, 4, LOGS).fill(4, 0, 4, 4, 7, 4, LOGS)
            .clear(2, 0, 0).clear(2, 1, 0)
            .build();

    /** Kho 7×5, cao 4: tường gỗ, trụ thân gỗ, mái gỗ, cửa đôi 3×2. */
    public static final StructurePlan WAREHOUSE = StructurePlan.builder("warehouse", "Kho")
            .ring(0, 0, 6, 4, 0, 2, PLANKS)
            .fill(0, 0, 0, 0, 2, 0, LOGS).fill(6, 0, 0, 6, 2, 0, LOGS)
            .fill(0, 0, 4, 0, 2, 4, LOGS).fill(6, 0, 4, 6, 2, 4, LOGS)
            .fill(3, 0, 4, 3, 2, 4, LOGS)
            .fill(0, 3, 0, 6, 3, 4, PLANKS)
            .clear(2, 0, 0).clear(3, 0, 0).clear(4, 0, 0)
            .clear(2, 1, 0).clear(3, 1, 0).clear(4, 1, 0)
            .build();

    /** Nhà gỗ nhỏ 5×5, cao 5: trụ góc thân gỗ, tường + mái gỗ, 3 cửa sổ kính, nóc thân gỗ. Cửa ở giữa mặt trước. */
    public static final StructurePlan CABIN = StructurePlan.builder("cabin", "Nhà gỗ nhỏ")
            .ring(0, 0, 4, 4, 0, 2, PLANKS)
            .fill(0, 0, 0, 0, 2, 0, LOGS).fill(4, 0, 0, 4, 2, 0, LOGS)
            .fill(0, 0, 4, 0, 2, 4, LOGS).fill(4, 0, 4, 4, 2, 4, LOGS)
            .set(0, 1, 2, GLASS).set(4, 1, 2, GLASS).set(2, 1, 4, GLASS)
            .fill(0, 3, 0, 4, 3, 4, PLANKS)
            .fill(1, 4, 2, 3, 4, 2, LOGS)
            .clear(2, 0, 0).clear(2, 1, 0)
            .build();

    /** Nhà đá 7×5, cao 6: tường đá, góc + mái đá mịn, 5 cửa sổ kính. Cửa ở giữa mặt trước. */
    public static final StructurePlan STONE_HOUSE = StructurePlan.builder("stone_house", "Nhà đá")
            .ring(0, 0, 6, 4, 0, 3, COBBLE)
            .fill(0, 0, 0, 0, 3, 0, BRICKS).fill(6, 0, 0, 6, 3, 0, BRICKS)
            .fill(0, 0, 4, 0, 3, 4, BRICKS).fill(6, 0, 4, 6, 3, 4, BRICKS)
            .set(1, 2, 0, GLASS).set(5, 2, 0, GLASS)
            .set(0, 2, 2, GLASS).set(6, 2, 2, GLASS).set(3, 2, 4, GLASS)
            .fill(0, 4, 0, 6, 4, 4, BRICKS)
            .fill(1, 5, 2, 5, 5, 2, BRICKS)
            .clear(3, 0, 0).clear(3, 1, 0)
            .build();

    /**
     * Nhà hai tầng 7×7, cao 10: tầng 1 đá, sàn + tầng 2 gỗ, trụ góc thân gỗ, cửa sổ kính hai tầng.
     * Chừa lỗ sàn 1×1 ở góc trong (5,4,5) để người chơi tự đặt thang.
     */
    public static final StructurePlan TWO_STORY = StructurePlan.builder("two_story", "Nhà hai tầng")
            .ring(0, 0, 6, 6, 0, 3, COBBLE)
            .fill(0, 4, 0, 6, 4, 6, PLANKS)
            .ring(0, 0, 6, 6, 5, 7, PLANKS)
            .fill(0, 0, 0, 0, 7, 0, LOGS).fill(6, 0, 0, 6, 7, 0, LOGS)
            .fill(0, 0, 6, 0, 7, 6, LOGS).fill(6, 0, 6, 6, 7, 6, LOGS)
            .set(1, 2, 0, GLASS).set(5, 2, 0, GLASS).set(0, 2, 3, GLASS).set(6, 2, 3, GLASS).set(3, 2, 6, GLASS)
            .set(2, 6, 0, GLASS).set(4, 6, 0, GLASS).set(0, 6, 3, GLASS).set(6, 6, 3, GLASS)
            .set(2, 6, 6, GLASS).set(4, 6, 6, GLASS)
            .fill(0, 8, 0, 6, 8, 6, PLANKS)
            .fill(1, 9, 3, 5, 9, 3, LOGS)
            .clear(3, 0, 0).clear(3, 1, 0)
            .clear(5, 4, 5)
            .build();

    public static final List<StructurePlan> ALL = List.of(HOUSE, WALL, GATE, TOWER, WAREHOUSE,
            CABIN, STONE_HOUSE, TWO_STORY);
}
