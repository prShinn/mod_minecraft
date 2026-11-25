package com.example.ai;

import com.example.block.ProfessionChestBlockEntity;
import com.example.entity.base.NpcDisplayComponent;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public final class DeliveryTarget {
    private static final double REACH_SQUARED = 6.25;
    private final Map<BlockPos, Long> blocked = new HashMap<>();
    private Path path;
    private long retryAt;

    public BlockPos find(PathAwareEntity npc, BlockPos preferred, Class<? extends ProfessionChestBlockEntity> type,
                         Inventory cargo, Predicate<ItemStack> accepts, int radius, int height,
                         NpcDisplayComponent display) {
        var world = npc.getWorld();
        long now = world.getTime();
        if (now < retryAt) return null;
        retryAt = now + 40;
        blocked.values().removeIf(until -> until <= now);
        BlockPos center = npc.getBlockPos();
        var alternatives = new ArrayList<BlockPos>();
        boolean foundChest = false;
        boolean foundSpace = false;
        if (preferred != null && world.isChunkLoaded(preferred) && type.isInstance(world.getBlockEntity(preferred))) {
            foundChest = true;
            foundSpace = hasSpace((Inventory) world.getBlockEntity(preferred), cargo, accepts);
            if (foundSpace && select(npc, preferred)) {
                display.clearReport();
                return preferred;
            }
        }
        for (BlockPos pos : BlockPos.iterate(center.add(-radius, -height, -radius), center.add(radius, height, radius))) {
            if (pos.equals(preferred) || !world.isChunkLoaded(pos)) continue;
            var be = world.getBlockEntity(pos);
            if (!type.isInstance(be)) continue;
            foundChest = true;
            if (hasSpace(type.cast(be), cargo, accepts)) {
                foundSpace = true;
                if (!blocked.containsKey(pos)) alternatives.add(pos.toImmutable());
            }
        }
        alternatives.sort(Comparator.comparingDouble(center::getSquaredDistance));
        for (int i = 0; i < Math.min(8, alternatives.size()); i++) {
            BlockPos pos = alternatives.get(i);
            if (select(npc, pos)) {
                display.clearReport();
                return pos;
            }
        }
        display.report(npc, !foundChest ? "Không tìm thấy rương nghề"
                : !foundSpace ? "Rương nghề đã đầy" : "Không có đường tới rương nghề");
        return null;
    }

    private boolean select(PathAwareEntity npc, BlockPos pos) {
        if (blocked.containsKey(pos)) return false;
        path = null;
        if (canReach(npc, pos)) return true;
        path = npc.getNavigation().findPathTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1);
        if (pathReachesChest(path, pos)) return true;
        markBlocked(npc, pos);
        return false;
    }

    /**
     * reachesTarget() đòi điểm cuối cách rương ≤ 1 theo Manhattan; đứng trên đường xúc/đất cày (cao 15/16)
     * thì điểm cuối bị tính thấp hơn rương 1 tầng → sai thành "không có đường". Chấp nhận khi điểm cuối
     * nằm trong tầm với REACH_SQUARED, cùng ngưỡng canReach dùng lúc cất đồ.
     */
    public static boolean pathReachesChest(@org.jetbrains.annotations.Nullable Path path, BlockPos pos) {
        if (path == null) return false;
        if (path.reachesTarget()) return true;
        var end = path.getEnd();
        return end != null && Vec3d.ofBottomCenter(end.getBlockPos()).squaredDistanceTo(Vec3d.ofCenter(pos)) <= REACH_SQUARED;
    }

    public static boolean canReach(PathAwareEntity npc, BlockPos pos) {
        Vec3d center = Vec3d.ofCenter(pos);
        if (npc.squaredDistanceTo(center) > REACH_SQUARED) return false;
        var hit = npc.getWorld().raycast(new net.minecraft.world.RaycastContext(
                npc.getEyePos(), center, net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, npc));
        return hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    public void start(PathAwareEntity npc) {
        if (path != null) npc.getNavigation().startMovingAlong(path, 1.3);
    }

    public void markBlocked(PathAwareEntity npc, BlockPos pos) {
        if (pos != null) blocked.put(pos, npc.getWorld().getTime() + 200);
    }

    public static boolean hasSpace(Inventory chest, Inventory cargo, Predicate<ItemStack> accepts) {
        for (int i = 0; i < cargo.size(); i++) {
            ItemStack stack = cargo.getStack(i);
            if (stack.isEmpty() || !accepts.test(stack)) continue;
            for (int slot = 0; slot < chest.size(); slot++) {
                if (!chest.isValid(slot, stack)) continue;
                ItemStack current = chest.getStack(slot);
                if (current.isEmpty() || (ItemStack.canCombine(current, stack)
                        && current.getCount() < Math.min(chest.getMaxCountPerStack(), stack.getMaxCount()))) return true;
            }
        }
        return false;
    }
}
