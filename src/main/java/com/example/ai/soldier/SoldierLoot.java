package com.example.ai.soldier;

import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chiến lợi phẩm của Binh lính:
 * - Túi riêng (tách khỏi túi đồ ăn), lưu NBT "LootBag", rơi ra khi lính chết.
 * - Danh sách chỗ quái do chính lính giết gần đây → chỉ nhặt đồ rơi quanh đó,
 *   không nhặt đồ người chơi vứt ra.
 */
public class SoldierLoot {
    public static final int BAG_SIZE = 18;
    /** Đồ rơi nằm trong bán kính này quanh chỗ quái chết thì tính là chiến lợi phẩm. */
    public static final double KILL_SITE_RADIUS = 3.0;
    /** Chỗ quái chết được nhớ trong 30 giây. */
    private static final int KILL_SITE_TICKS = 600;
    /** Món không nhặt được (kẹt, rơi xuống hố...) bị bỏ qua 30 giây. */
    private static final int SKIP_TICKS = 600;

    private record KillSite(Vec3d pos, long until) {}

    public final SimpleInventory bag = new SimpleInventory(BAG_SIZE);
    private final List<KillSite> killSites = new ArrayList<>();
    private final Map<UUID, Long> skippedItems = new HashMap<>();

    public void recordKill(Vec3d pos, long now) {
        killSites.add(new KillSite(pos, now + KILL_SITE_TICKS));
    }

    public boolean hasRecentKills(long now) {
        killSites.removeIf(site -> site.until <= now);
        return !killSites.isEmpty();
    }

    /** Món đồ rơi này có phải chiến lợi phẩm của lính không (gần chỗ quái do lính giết). */
    public boolean isLoot(ItemEntity item, long now) {
        if (skippedItems.getOrDefault(item.getUuid(), 0L) > now) return false;
        double radiusSq = KILL_SITE_RADIUS * KILL_SITE_RADIUS;
        for (KillSite site : killSites) {
            if (site.until > now && item.getPos().squaredDistanceTo(site.pos) <= radiusSq) return true;
        }
        return false;
    }

    public void skip(ItemEntity item, long now) {
        skippedItems.values().removeIf(until -> until <= now);
        skippedItems.put(item.getUuid(), now + SKIP_TICKS);
    }

    public void writeNbt(NbtCompound nbt) {
        nbt.put("LootBag", bag.toNbtList());
    }

    public void readNbt(NbtCompound nbt) {
        if (nbt.contains("LootBag")) bag.readNbtList(nbt.getList("LootBag", 10));
    }
}
