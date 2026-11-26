package com.example.ai.miner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Thời gian đào theo độ cứng block và độ tốt của cúp. */
class MineQuarryGoalTest {
    @Test
    void betterPickaxeDigsFaster() {
        float stone = 1.5F;
        int wood = MineQuarryGoal.breakTime(stone, 2.0F, 0);
        int iron = MineQuarryGoal.breakTime(stone, 6.0F, 0);
        int diamond = MineQuarryGoal.breakTime(stone, 8.0F, 0);
        int netherite = MineQuarryGoal.breakTime(stone, 9.0F, 0);
        assertTrue(wood > iron && iron > diamond && diamond >= netherite);
        assertEquals(23, wood);   // 1.5 × 30 / 2
        assertEquals(8, iron);    // 1.5 × 30 / 6 = 7.5 → 8
    }

    @Test
    void efficiencyAndHardnessMatter() {
        assertTrue(MineQuarryGoal.breakTime(1.5F, 8.0F, 5) < MineQuarryGoal.breakTime(1.5F, 8.0F, 0));
        assertTrue(MineQuarryGoal.breakTime(50.0F, 8.0F, 0) > MineQuarryGoal.breakTime(3.0F, 8.0F, 0)); // obsidian vs quặng
        assertEquals(2, MineQuarryGoal.breakTime(0.0F, 6.0F, 0), "block mềm vẫn tối thiểu 2 tick");
    }
}
