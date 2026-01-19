package com.example.ai;

import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalReservationSystem {

    // Singleton instance
    private static GlobalReservationSystem INSTANCE;

    public static synchronized GlobalReservationSystem getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GlobalReservationSystem();
        }
        return INSTANCE;
    }

    // ========== RESERVATION STORAGE ==========
    private final Map<String, ReservationEntry> reservations = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> npcReservations = new ConcurrentHashMap<>();

    private static class ReservationEntry {
        final UUID npcId;
        final long timestamp;
        final BlockPos pos;
        final ReservationType type;

        ReservationEntry(UUID npcId, BlockPos pos, ReservationType type) {
            this.npcId = npcId;
            this.pos = pos;
            this.type = type;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 30000; // 30 seconds timeout
        }
    }

    private enum ReservationType {
        WORK_CHEST, CROP, FARMLAND, BED, TREE
    }

    // ========== PUBLIC API ==========

    /**
     * Try to reserve a position. Returns true if successful (not reserved by others)
     */
    public synchronized boolean tryReserve(BlockPos pos, UUID npcId, String type) {
        cleanupExpired();

        String key = createKey(pos, type);
        ReservationEntry existing = reservations.get(key);

        // If already reserved by someone else
        if (existing != null && !existing.npcId.equals(npcId)) {
            return false;
        }

        // Reserve it
        reservations.put(key, new ReservationEntry(npcId, pos, ReservationType.valueOf(type)));
        npcReservations.computeIfAbsent(npcId, k -> new HashSet<>()).add(key);
        return true;
    }

    /**
     * Release a reservation
     */
    public synchronized void release(BlockPos pos, UUID npcId, String type) {
        String key = createKey(pos, type);
        ReservationEntry entry = reservations.get(key);

        if (entry != null && entry.npcId.equals(npcId)) {
            reservations.remove(key);
            Set<String> npcRes = npcReservations.get(npcId);
            if (npcRes != null) {
                npcRes.remove(key);
                if (npcRes.isEmpty()) {
                    npcReservations.remove(npcId);
                }
            }
        }
    }

    /**
     * Release ALL reservations of an NPC (called when NPC dies or finishes task)
     */
    public synchronized void releaseAll(UUID npcId) {
        Set<String> npcRes = npcReservations.remove(npcId);
        if (npcRes != null) {
            for (String key : npcRes) {
                reservations.remove(key);
            }
        }
    }

    /**
     * Find nearest available position that's not reserved
     */
    public synchronized BlockPos findNearestAvailable(
            List<BlockPos> candidates,
            UUID npcId,
            BlockPos npcPos,
            String type) {

        cleanupExpired();

        // Sort by distance
        candidates.sort(Comparator.comparingDouble(
                pos -> npcPos.getSquaredDistance(pos.getX(), pos.getY(), pos.getZ())
        ));

        // Find first available
        for (BlockPos pos : candidates) {
            String key = createKey(pos, type);
            ReservationEntry existing = reservations.get(key);

            if (existing == null || existing.npcId.equals(npcId)) {
                // Reserve it immediately
                if (tryReserve(pos, npcId, type)) {
                    return pos;
                }
            }
        }

        return null;
    }

    /**
     * Check if position is reserved by someone else
     */
    public synchronized boolean isReservedByOthers(BlockPos pos, UUID npcId, String type) {
        String key = createKey(pos, type);
        ReservationEntry existing = reservations.get(key);
        return existing != null && !existing.npcId.equals(npcId);
    }

    /**
     * Get the NPC who reserved a position
     */
    public synchronized UUID getReserver(BlockPos pos, String type) {
        String key = createKey(pos, type);
        ReservationEntry entry = reservations.get(key);
        return entry != null ? entry.npcId : null;
    }

    // ========== PRIVATE METHODS ==========

    private String createKey(BlockPos pos, String type) {
        return type + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private void cleanupExpired() {
        Iterator<Map.Entry<String, ReservationEntry>> it = reservations.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ReservationEntry> entry = it.next();
            if (entry.getValue().isExpired()) {
                String key = entry.getKey();
                UUID npcId = entry.getValue().npcId;

                it.remove();

                // Remove from NPC's set
                Set<String> npcRes = npcReservations.get(npcId);
                if (npcRes != null) {
                    npcRes.remove(key);
                    if (npcRes.isEmpty()) {
                        npcReservations.remove(npcId);
                    }
                }
            }
        }
    }
}