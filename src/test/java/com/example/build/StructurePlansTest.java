package com.example.build;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bất biến của mọi bản vẽ — thêm công trình mới vào StructurePlans.ALL là tự được kiểm.
 */
class StructurePlansTest {

    @Test
    void idsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (StructurePlan plan : StructurePlans.ALL) {
            assertTrue(ids.add(plan.id()), "Trùng id: " + plan.id());
            assertFalse(plan.steps().isEmpty(), plan.id());
        }
    }

    @Test
    void stepsStayInsideBoundsAndNeverOverlap() {
        for (StructurePlan plan : StructurePlans.ALL) {
            Set<Long> cells = new HashSet<>();
            int maxX = 0, maxY = 0, maxZ = 0;
            for (StructurePlan.Step s : plan.steps()) {
                assertTrue(s.x() >= 0 && s.y() >= 0 && s.z() >= 0, plan.id() + " toạ độ âm: " + s);
                assertTrue(s.x() < plan.width() && s.y() < plan.height() && s.z() < plan.depth(),
                        plan.id() + " ngoài kích thước: " + s);
                assertTrue(cells.add(BlockPos.asLong(s.x(), s.y(), s.z())), plan.id() + " trùng ô: " + s);
                maxX = Math.max(maxX, s.x());
                maxY = Math.max(maxY, s.y());
                maxZ = Math.max(maxZ, s.z());
            }
            assertEquals(maxX + 1, plan.width(), plan.id() + " width");
            assertEquals(maxY + 1, plan.height(), plan.id() + " height");
            assertEquals(maxZ + 1, plan.depth(), plan.id() + " depth");
        }
    }

    @Test
    void stepsBuildBottomUpAndOriginIsLast() {
        for (StructurePlan plan : StructurePlans.ALL) {
            List<StructurePlan.Step> steps = plan.steps();
            int origin = plan.originIndex();
            assertEquals(steps.size() - 1, origin, plan.id() + ": ô bản vẽ (0,0,0) phải là bước cuối");
            StructurePlan.Step last = steps.get(origin);
            assertEquals(0, last.x() | last.y() | last.z(), plan.id());
            for (int i = 1; i < origin; i++) {
                StructurePlan.Step prev = steps.get(i - 1), cur = steps.get(i);
                int cmp = Integer.compare(prev.y(), cur.y());
                if (cmp == 0) cmp = Integer.compare(prev.z(), cur.z());
                if (cmp == 0) cmp = Integer.compare(prev.x(), cur.x());
                assertTrue(cmp < 0, plan.id() + " lệch thứ tự xây tại bước " + i + ": " + prev + " → " + cur);
            }
        }
    }

    @Test
    void costMatchesSteps() {
        for (StructurePlan plan : StructurePlans.ALL) {
            Map<BuildMaterial, Integer> counted = new EnumMap<>(BuildMaterial.class);
            for (StructurePlan.Step s : plan.steps()) counted.merge(s.material(), 1, Integer::sum);
            int total = 0;
            for (BuildMaterial m : BuildMaterial.values()) {
                assertEquals(counted.getOrDefault(m, 0), plan.cost(m), plan.id() + " cost " + m);
                total += plan.cost(m);
            }
            assertEquals(plan.steps().size(), total, plan.id());
            assertEquals(counted.keySet(), plan.materials(), plan.id());
        }
    }

    /** Công thức bản vẽ = mỗi vật liệu x1 (shapeless) → tập vật liệu phải khác nhau, không thì 2 bản vẽ trùng công thức. */
    @Test
    void materialSetsAreUniqueSoRecipesDoNotCollide() {
        Set<Set<BuildMaterial>> seen = new HashSet<>();
        for (StructurePlan plan : StructurePlans.ALL) {
            assertTrue(seen.add(plan.materials()), plan.id() + " trùng tập vật liệu → trùng công thức");
        }
    }

    @Test
    void doorwaysAreOpenWhereExpected() {
        // Nhà dân: cửa gỗ ở ô dưới, ô trên để trống cho nửa trên cửa
        assertEquals(BuildMaterial.DOOR, stepAt(StructurePlans.HOUSE, 3, 0, 0).material(), "Nhà dân nhỏ: cửa gỗ");
        assertFalse(hasStep(StructurePlans.HOUSE, 3, 1, 0), "Nhà dân nhỏ: nửa trên cửa");
        assertEquals(BuildMaterial.DOOR, stepAt(StructurePlans.HOUSE_LARGE, 2, 0, 2).material(), "Nhà dân lớn: cửa gỗ");
        assertFalse(hasStep(StructurePlans.HOUSE_LARGE, 2, 1, 2), "Nhà dân lớn: nửa trên cửa");
        assertFalse(hasStep(StructurePlans.HOUSE_LARGE, 4, 0, 5), "Nhà dân lớn: lối thông 2 gian");
        assertFalse(hasStep(StructurePlans.HOUSE_LARGE, 4, 1, 5), "Nhà dân lớn: lối thông 2 gian");
        assertFalse(hasStep(StructurePlans.TOWER, 2, 0, 0), "Chòi canh: cửa");
        assertEquals(BuildMaterial.DOOR, stepAt(StructurePlans.CABIN, 2, 0, 0).material(), "Nhà gỗ nhỏ: cửa gỗ");
        assertFalse(hasStep(StructurePlans.CABIN, 2, 1, 0), "Nhà gỗ nhỏ: nửa trên cửa");
        assertEquals(BuildMaterial.DOOR, stepAt(StructurePlans.STONE_HOUSE, 3, 0, 0).material(), "Nhà đá: cửa gỗ");
        assertFalse(hasStep(StructurePlans.STONE_HOUSE, 3, 1, 0), "Nhà đá: nửa trên cửa");
        assertEquals(BuildMaterial.DOOR, stepAt(StructurePlans.TWO_STORY, 3, 0, 0).material(), "Nhà hai tầng: cửa gỗ");
        assertFalse(hasStep(StructurePlans.TWO_STORY, 3, 1, 0), "Nhà hai tầng: nửa trên cửa");
        assertFalse(hasStep(StructurePlans.TWO_STORY, 5, 4, 5), "Nhà hai tầng: lỗ thang");
        for (int x = 1; x <= 5; x++) {
            for (int y = 0; y <= 3; y++) {
                assertFalse(hasStep(StructurePlans.GATE, x, y, 0), "Cổng làng: lối đi " + x + "," + y);
            }
        }
    }

    @Test
    void toWorldRotatesRightAndForwardForEveryFacing() {
        BlockPos origin = new BlockPos(100, 64, 100);
        for (Direction facing : Direction.Type.HORIZONTAL) {
            Direction right = facing.rotateYClockwise();
            assertEquals(origin.offset(right, 2), StructurePlan.toWorld(origin, facing, 2, 0, 0), facing + " x→phải");
            assertEquals(origin.offset(facing, 3), StructurePlan.toWorld(origin, facing, 0, 0, 3), facing + " z→trước");
            assertEquals(origin.up(4), StructurePlan.toWorld(origin, facing, 0, 4, 0), facing + " y→lên");
        }
    }

    /** Hướng cục bộ của block (cầu thang, cửa) quay cùng phép với toạ độ. */
    @Test
    void localDirectionsRotateLikePositions() {
        BlockPos origin = new BlockPos(100, 64, 100);
        for (Direction facing : Direction.Type.HORIZONTAL) {
            for (Direction local : Direction.Type.HORIZONTAL) {
                BlockPos moved = StructurePlan.toWorld(origin, facing, 5 + local.getOffsetX(), 0, 5 + local.getOffsetZ());
                BlockPos base = StructurePlan.toWorld(origin, facing, 5, 0, 5);
                assertEquals(base.offset(StructurePlan.toWorldDir(local, facing)), moved, facing + " " + local);
            }
        }
    }

    /** Mái nhà dân: cầu thang hai bên leo vào nóc, nóc là thân gỗ nằm dọc. */
    @Test
    void houseRoofsSlopeTowardRidge() {
        for (StructurePlan plan : List.of(StructurePlans.HOUSE, StructurePlans.HOUSE_LARGE)) {
            int ridgeX = plan.width() / 2;
            boolean hasRidge = false;
            for (StructurePlan.Step s : plan.steps()) {
                if (s.material() == BuildMaterial.STAIRS && s.facing().getAxis() == Direction.Axis.X) { // mái
                    assertEquals(s.x() < ridgeX ? Direction.EAST : Direction.WEST, s.facing(), plan.id() + " " + s);
                }
                if (s.material() == BuildMaterial.LOGS && s.facing() == Direction.SOUTH) {
                    assertEquals(ridgeX, s.x(), plan.id() + " nóc");
                    assertEquals(plan.height() - 1, s.y(), plan.id() + " nóc ở trên cùng");
                    hasRidge = true;
                }
            }
            assertTrue(hasRidge, plan.id());
        }
    }

    /** Nhà dân lớn 2 tầng: cầu thang trong nhà leo đủ 5 bậc tới sàn tầng 2, phía trên bậc có chỗ đứng (lỗ sàn). */
    @Test
    void largeHouseStaircaseReachesSecondFloor() {
        StructurePlan plan = StructurePlans.HOUSE_LARGE;
        for (int k = 0; k <= 4; k++) {
            StructurePlan.Step stair = stepAt(plan, 7, k, 3 + k);
            assertEquals(BuildMaterial.STAIRS, stair.material(), "bậc " + k);
            assertEquals(Direction.SOUTH, stair.facing(), "bậc " + k + " leo về phía sau");
            assertFalse(hasStep(plan, 7, k + 1, 3 + k), "trên bậc " + k + " phải trống");
            assertFalse(hasStep(plan, 7, k + 2, 3 + k), "đầu người trên bậc " + k + " phải trống");
        }
        assertEquals(BuildMaterial.PLANKS, stepAt(plan, 5, 4, 5).material(), "sàn tầng 2");
    }

    private static StructurePlan.Step stepAt(StructurePlan plan, int x, int y, int z) {
        return plan.steps().stream().filter(s -> s.x() == x && s.y() == y && s.z() == z).findFirst().orElseThrow();
    }

    /** NPC phải đứng ngoài công trình, nếu không sẽ tự xây nhốt mình. */
    @Test
    void standPositionIsOutsideFootprint() {
        BlockPos origin = BlockPos.ORIGIN;
        for (StructurePlan plan : StructurePlans.ALL) {
            for (Direction facing : Direction.Type.HORIZONTAL) {
                BlockPos stand = plan.standPos(origin, facing);
                for (StructurePlan.Step s : plan.steps()) {
                    BlockPos cell = plan.toWorld(origin, facing, s);
                    assertFalse(cell.getX() == stand.getX() && cell.getZ() == stand.getZ(),
                            plan.id() + " " + facing + ": điểm đứng nằm trong công trình");
                }
            }
        }
    }

    @Test
    void builderRejectsNegativeCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> StructurePlan.builder("bad", "Bad").set(-1, 0, 0, BuildMaterial.COBBLE));
    }

    private static boolean hasStep(StructurePlan plan, int x, int y, int z) {
        return plan.steps().stream().anyMatch(s -> s.x() == x && s.y() == y && s.z() == z);
    }
}
