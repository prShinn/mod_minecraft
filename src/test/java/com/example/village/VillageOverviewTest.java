package com.example.village;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phân loại thông báo sự cố thật của NPC sang trạng thái trong Sổ quản lý làng. */
class VillageOverviewTest {
    private static void expect(VillageNpcStatus status, String issue) {
        assertEquals(status, VillageOverview.classifyIssue(issue), issue);
    }

    @Test
    void chestFull() {
        expect(VillageNpcStatus.CHEST_FULL, "Rương nghề đã đầy");
        expect(VillageNpcStatus.CHEST_FULL, "Rương nộp hàng đã đầy");
        expect(VillageNpcStatus.CHEST_FULL, "Rương đầy, tiều phu vẫn giữ mầm dư");
    }

    @Test
    void missing() {
        expect(VillageNpcStatus.MISSING, "Rương chăn nuôi thiếu thức ăn cho vật nuôi");
        expect(VillageNpcStatus.MISSING, "Rương đầu bếp thiếu nhiên liệu (than, gỗ...)");
        expect(VillageNpcStatus.MISSING, "Không tìm thấy rương thức ăn còn đồ");
    }

    @Test
    void stuck() {
        expect(VillageNpcStatus.STUCK, "Không có đường tới rương nghề");
        expect(VillageNpcStatus.STUCK, "Không có đường tới bờ câu");
        expect(VillageNpcStatus.STUCK, "Không tới được công trình");
        expect(VillageNpcStatus.STUCK, "Đang chờ thử lại đường tới mỏ");
    }

    @Test
    void otherIssuesNeedAttention() {
        expect(VillageNpcStatus.ATTENTION, "Chưa bật vùng chặt trên rương tiều phu");
        expect(VillageNpcStatus.ATTENTION, "Không tìm thấy rương nghề");
        expect(VillageNpcStatus.ATTENTION, "Rương nghề không còn dùng được");
    }

    @Test
    void problemsSortFirst() {
        for (VillageNpcStatus s : VillageNpcStatus.values()) {
            if (s.isProblem()) assertTrue(s.ordinal() < VillageNpcStatus.COMBAT.ordinal(), s.name());
        }
        assertFalse(VillageNpcStatus.SLEEPING.isProblem());
        assertEquals(VillageNpcStatus.ATTENTION, VillageNpcStatus.byId(99));
    }
}
