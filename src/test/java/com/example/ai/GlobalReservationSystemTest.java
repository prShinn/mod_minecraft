package com.example.ai;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GlobalReservationSystemTest {
    private static final String CHEST = "WORK_CHEST";

    private long now;
    private GlobalReservationSystem system;
    private final UUID npcA = UUID.randomUUID();
    private final UUID npcB = UUID.randomUUID();
    private final BlockPos pos = new BlockPos(10, 64, -5);

    @BeforeEach
    void setUp() {
        now = 1_000_000L;
        system = new GlobalReservationSystem(() -> now);
    }

    @Test
    void reservedPositionIsBlockedForOthersButNotForOwner() {
        assertTrue(system.tryReserve(pos, npcA, CHEST));
        assertFalse(system.tryReserve(pos, npcB, CHEST));
        assertTrue(system.tryReserve(pos, npcA, CHEST), "NPC giữ chỗ được đặt lại (gia hạn)");
        assertTrue(system.isReservedByOthers(pos, npcB, CHEST));
        assertFalse(system.isReservedByOthers(pos, npcA, CHEST));
        assertEquals(npcA, system.getReserver(pos, CHEST));
    }

    @Test
    void differentTypesAtSamePositionAreIndependent() {
        assertTrue(system.tryReserve(pos, npcA, CHEST));
        assertTrue(system.tryReserve(pos, npcB, "CROP"));
    }

    @Test
    void reservationStillHeldAtExactlyThirtySeconds() {
        assertTrue(system.tryReserve(pos, npcA, CHEST));
        now += GlobalReservationSystem.RESERVATION_TIMEOUT_MS;
        assertTrue(system.isReservedByOthers(pos, npcB, CHEST));
        assertFalse(system.tryReserve(pos, npcB, CHEST));
    }

    @Test
    void reservationExpiresAfterThirtySeconds() {
        assertTrue(system.tryReserve(pos, npcA, CHEST));
        now += GlobalReservationSystem.RESERVATION_TIMEOUT_MS + 1;
        assertFalse(system.isReservedByOthers(pos, npcB, CHEST));
        assertTrue(system.tryReserve(pos, npcB, CHEST), "Hết hạn thì NPC khác nhận được");
        assertEquals(npcB, system.getReserver(pos, CHEST));
    }

    @Test
    void reReservingRefreshesTimeout() {
        assertTrue(system.tryReserve(pos, npcA, CHEST));
        now += 20_000;
        assertTrue(system.tryReserve(pos, npcA, CHEST));
        now += 20_000; // 40s kể từ lần đầu, 20s kể từ lần gia hạn
        assertFalse(system.tryReserve(pos, npcB, CHEST));
    }

    @Test
    void releaseOnlyWorksForOwner() {
        assertTrue(system.tryReserve(pos, npcA, CHEST));
        system.release(pos, npcB, CHEST);
        assertFalse(system.tryReserve(pos, npcB, CHEST), "NPC khác không nhả được chỗ của người khác");
        system.release(pos, npcA, CHEST);
        assertTrue(system.tryReserve(pos, npcB, CHEST));
    }

    @Test
    void releaseAllFreesEveryReservationOfThatNpcOnly() {
        BlockPos other = pos.east(3);
        BlockPos third = pos.north(2);
        assertTrue(system.tryReserve(pos, npcA, CHEST));
        assertTrue(system.tryReserve(other, npcA, "CROP"));
        assertTrue(system.tryReserve(third, npcB, CHEST));

        system.releaseAll(npcA);

        assertNull(system.getReserver(pos, CHEST));
        assertNull(system.getReserver(other, "CROP"));
        assertEquals(npcB, system.getReserver(third, CHEST), "Chỗ của NPC khác giữ nguyên");
        assertTrue(system.tryReserve(pos, npcB, CHEST));
        system.releaseAll(UUID.randomUUID()); // NPC không có chỗ: không lỗi
    }

    @Test
    void findNearestAvailableSkipsPositionsReservedByOthers() {
        BlockPos origin = BlockPos.ORIGIN;
        BlockPos near = origin.east(1);
        BlockPos far = origin.east(5);
        assertTrue(system.tryReserve(near, npcB, CHEST));

        List<BlockPos> candidates = new ArrayList<>(List.of(far, near));
        assertEquals(far, system.findNearestAvailable(candidates, npcA, origin, CHEST));
        assertEquals(npcA, system.getReserver(far, CHEST), "Tìm được thì giữ chỗ luôn");
        assertNull(system.findNearestAvailable(new ArrayList<>(List.of(near)), npcA, origin, CHEST));
    }

    @Test
    void unknownTypeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> system.tryReserve(pos, npcA, "UNKNOWN"));
    }
}
