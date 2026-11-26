package com.example.build;

import java.util.List;

import static com.example.build.BuildMaterial.BRICKS;
import static com.example.build.BuildMaterial.COBBLE;
import static com.example.build.BuildMaterial.GLASS;
import static com.example.build.BuildMaterial.LOGS;
import static com.example.build.BuildMaterial.PLANKS;
import static com.example.build.BuildMaterial.STAIRS;
import static net.minecraft.util.math.Direction.EAST;
import static net.minecraft.util.math.Direction.SOUTH;
import static net.minecraft.util.math.Direction.WEST;

/**
 * Công trình có sẵn. Mỗi công trình dùng một TẬP vật liệu riêng → công thức shapeless
 * của bản vẽ (mỗi vật liệu x1) không trùng nhau:
 * Nhà dân nhỏ {đá, gỗ, thân gỗ, kính, cầu thang, cửa} · Nhà dân lớn {… + phiến gỗ} · Tường {đá, đá mịn} · Cổng {thân gỗ, gỗ, đá mịn} ·
 * Chòi {thân gỗ, đá, gỗ} · Kho {thân gỗ, gỗ} ·
 * Nhà gỗ nhỏ {thân gỗ, gỗ, kính} · Nhà đá {đá, đá mịn, kính} · Nhà hai tầng {thân gỗ, gỗ, đá, kính}.
 */
public final class StructurePlans {
    private StructurePlans() {}

    /**
     * Nhà dân nhỏ 7×6, cao 8: chân móng đá, tường ván, cột góc thân gỗ, cửa sổ kính 4 phía,
     * mái dốc cầu thang gỗ hai bên, nóc thân gỗ nằm dọc, hồi nhà có ô thoáng. Cửa gỗ giữa mặt trước.
     */
    public static final StructurePlan HOUSE = StructurePlan.builder("house", "Nhà dân nhỏ")
            .ring(0, 0, 6, 5, 0, 0, COBBLE)
            .ring(0, 0, 6, 5, 1, 3, PLANKS)
            .fill(0, 0, 0, 0, 3, 0, LOGS).fill(6, 0, 0, 6, 3, 0, LOGS)
            .fill(0, 0, 5, 0, 3, 5, LOGS).fill(6, 0, 5, 6, 3, 5, LOGS)
            .fill(1, 1, 0, 1, 2, 0, GLASS).fill(5, 1, 0, 5, 2, 0, GLASS)
            .fill(0, 1, 2, 0, 2, 3, GLASS).fill(6, 1, 2, 6, 2, 3, GLASS)
            .fill(3, 1, 5, 3, 2, 5, GLASS)
            // hồi nhà trước/sau (tam giác dưới mái)
            .fill(1, 4, 0, 5, 4, 0, PLANKS).fill(2, 5, 0, 4, 5, 0, PLANKS).set(3, 6, 0, PLANKS)
            .fill(1, 4, 5, 5, 4, 5, PLANKS).fill(2, 5, 5, 4, 5, 5, PLANKS).set(3, 6, 5, PLANKS)
            .set(3, 5, 0, GLASS)
            // mái dốc: cầu thang leo vào giữa, nóc thân gỗ nằm dọc
            .stairsRow(0, 4, 0, 5, EAST).stairsRow(6, 4, 0, 5, WEST)
            .stairsRow(1, 5, 0, 5, EAST).stairsRow(5, 5, 0, 5, WEST)
            .stairsRow(2, 6, 0, 5, EAST).stairsRow(4, 6, 0, 5, WEST)
            .logRow(3, 7, 0, 5)
            .door(3, 0, 0, SOUTH)
            .build();

    /**
     * Nhà dân lớn 2 tầng 9×9, cao 13: hiên trước (sàn + mái phiến gỗ, cột thân gỗ).
     * Tầng 1: 2 gian ngăn bằng vách ván có lối thông, cầu thang gỗ dọc tường phải gian phải lên tầng 2.
     * Sàn tầng 2 bằng ván, dầm thân gỗ quanh nhà; tầng 2 một phòng rộng, cửa sổ 4 phía.
     * Mái dốc cầu thang gỗ, nóc thân gỗ nằm dọc. Cửa gỗ ở gian trái.
     */
    public static final StructurePlan HOUSE_LARGE = StructurePlan.builder("house_large", "Nhà dân lớn")
            // ===== Tầng 1 (y0..3) =====
            .ring(0, 2, 8, 8, 0, 0, COBBLE)
            .ring(0, 2, 8, 8, 1, 3, PLANKS)
            .fill(4, 0, 3, 4, 3, 7, PLANKS).clear(4, 0, 5).clear(4, 1, 5)          // vách ngăn + lối thông
            .fill(1, 1, 2, 1, 2, 2, GLASS).fill(6, 1, 2, 7, 2, 2, GLASS)            // cửa sổ trước
            .fill(0, 1, 4, 0, 2, 6, GLASS).fill(8, 1, 4, 8, 2, 6, GLASS)            // cửa sổ hai bên
            .fill(2, 1, 8, 2, 2, 8, GLASS).fill(6, 1, 8, 6, 2, 8, GLASS)            // cửa sổ sau
            // ===== Sàn tầng 2 (y4): ván, dầm thân gỗ quanh nhà, lỗ cầu thang =====
            .fill(1, 4, 3, 7, 4, 7, PLANKS)
            .ring(0, 2, 8, 8, 4, 4, LOGS)
            .clear(7, 4, 4).clear(7, 4, 5).clear(7, 4, 6)
            // cầu thang gỗ dọc tường phải gian phải: leo về phía sau, bậc cuối nằm trong sàn
            .shaped(7, 0, 3, STAIRS, SOUTH, false).shaped(7, 1, 4, STAIRS, SOUTH, false)
            .shaped(7, 2, 5, STAIRS, SOUTH, false).shaped(7, 3, 6, STAIRS, SOUTH, false)
            .shaped(7, 4, 7, STAIRS, SOUTH, false)
            // ===== Tầng 2 (y5..7) =====
            .ring(0, 2, 8, 8, 5, 7, PLANKS)
            .fill(2, 5, 2, 2, 6, 2, GLASS).fill(6, 5, 2, 6, 6, 2, GLASS)
            .fill(0, 5, 4, 0, 6, 6, GLASS).fill(8, 5, 4, 8, 6, 6, GLASS)
            .fill(2, 5, 8, 2, 6, 8, GLASS).fill(6, 5, 8, 6, 6, 8, GLASS)
            // cột góc + cột đầu vách suốt 2 tầng
            .fill(0, 0, 2, 0, 7, 2, LOGS).fill(8, 0, 2, 8, 7, 2, LOGS)
            .fill(0, 0, 8, 0, 7, 8, LOGS).fill(8, 0, 8, 8, 7, 8, LOGS)
            .fill(4, 0, 2, 4, 3, 2, LOGS).fill(4, 0, 8, 4, 3, 8, LOGS)
            // ===== Hồi nhà trước/sau (y8..11) + ô thoáng =====
            .fill(1, 8, 2, 7, 8, 2, PLANKS).fill(2, 9, 2, 6, 9, 2, PLANKS).fill(3, 10, 2, 5, 10, 2, PLANKS).set(4, 11, 2, PLANKS)
            .fill(1, 8, 8, 7, 8, 8, PLANKS).fill(2, 9, 8, 6, 9, 8, PLANKS).fill(3, 10, 8, 5, 10, 8, PLANKS).set(4, 11, 8, PLANKS)
            .set(4, 10, 2, GLASS)
            // ===== Mái dốc =====
            .stairsRow(0, 8, 2, 8, EAST).stairsRow(8, 8, 2, 8, WEST)
            .stairsRow(1, 9, 2, 8, EAST).stairsRow(7, 9, 2, 8, WEST)
            .stairsRow(2, 10, 2, 8, EAST).stairsRow(6, 10, 2, 8, WEST)
            .stairsRow(3, 11, 2, 8, EAST).stairsRow(5, 11, 2, 8, WEST)
            .logRow(4, 12, 2, 8)
            // ===== Hiên trước =====
            .fill(0, 0, 0, 0, 2, 0, LOGS).fill(8, 0, 0, 8, 2, 0, LOGS)
            .slabs(1, 0, 7, 1, 0, false)
            .slabs(0, 0, 8, 1, 3, true)
            .door(2, 0, 2, SOUTH)
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

    public static final List<StructurePlan> ALL = List.of(HOUSE, HOUSE_LARGE, WALL, GATE, TOWER, WAREHOUSE,
            CABIN, STONE_HOUSE, TWO_STORY);
}
