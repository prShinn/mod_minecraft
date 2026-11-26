package com.example.helper;

import com.example.ai.worker.DepositCargoGoal;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Thoát kẹt khi thân NPC bị block đè (cây mọc trùm lên người, lá mọc kín...).
 * - NPC được phép chặt cây (Tiều phu): chặt ngay gỗ/lá đang đè, đồ rơi vào túi.
 * - NPC khác: dịch sang ô trống gần nhất; chỉ phá LÁ nếu không còn chỗ nào
 *   (không bao giờ phá gỗ/block người chơi xây).
 */
public final class NpcStuckHelper {
    private static final int SEARCH_RADIUS = 4;

    private NpcStuckHelper() {
    }

    /**
     * Gọi định kỳ ở server tick. Trả về true nếu NPC đang kẹt và đã xử lý.
     *
     * @param canChopTree cho phép chặt gỗ/lá đang đè (chỉ Tiều phu)
     * @param sink        nơi nhận đồ rơi khi chặt; null → rơi ra đất
     */
    public static boolean escapeIfStuck(PathAwareEntity npc, boolean canChopTree, @Nullable Inventory sink) {
        if (!(npc.getWorld() instanceof ServerWorld world) || npc.isSleeping()
                || npc.getPose() == net.minecraft.entity.EntityPose.SLEEPING || npc.hasVehicle()) return false;
        List<BlockPos> blocking = blockingBlocks(npc, world);
        if (blocking.isEmpty()) return false;

        if (canChopTree) {
            boolean choppedAll = true;
            for (BlockPos pos : blocking) {
                BlockState state = world.getBlockState(pos);
                if (state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES)) breakInto(world, npc, pos, state, sink);
                else choppedAll = false;
            }
            if (choppedAll) {
                npc.getNavigation().stop();
                return true;
            }
        }

        BlockPos free = findFreeSpot(npc, world);
        if (free != null) {
            npc.getNavigation().stop();
            npc.requestTeleport(free.getX() + 0.5, free.getY(), free.getZ() + 0.5);
            return true;
        }

        // Không còn chỗ trống: phá lá (vô hại với công trình) để NPC không bị kẹt vĩnh viễn
        boolean cleared = false;
        for (BlockPos pos : blocking) {
            BlockState state = world.getBlockState(pos);
            if (state.isIn(BlockTags.LEAVES)) {
                breakInto(world, npc, pos, state, sink);
                cleared = true;
            }
        }
        return cleared;
    }

    /** Các block có va chạm đang lấn vào thân NPC (thu nhỏ hộp một chút để bỏ qua block chỉ chạm mặt). */
    private static List<BlockPos> blockingBlocks(PathAwareEntity npc, World world) {
        Box box = npc.getBoundingBox().contract(0.05);
        List<BlockPos> result = new ArrayList<>();
        BlockPos.stream(box).forEach(pos -> {
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) return;
            var shape = state.getCollisionShape(world, pos);
            // So từng hộp con: hộp bao của cầu thang/hàng rào góc là nguyên khối → báo kẹt sai khi đứng trên bậc
            if (!shape.isEmpty() && shape.getBoundingBoxes().stream().anyMatch(b -> b.offset(pos).intersects(box))) {
                result.add(pos.toImmutable());
            }
        });
        return result;
    }

    /** Lá mọc tự nhiên (không phải lá người chơi đặt) — chỉ loại này NPC được phá. */
    public static boolean isNaturalLeaves(BlockState state) {
        return state.isIn(BlockTags.LEAVES)
                && !(state.contains(net.minecraft.block.LeavesBlock.PERSISTENT) && state.get(net.minecraft.block.LeavesBlock.PERSISTENT));
    }

    /** Phá block, đồ rơi cho vào sink (null → rơi ra đất). */
    public static void breakInto(ServerWorld world, PathAwareEntity npc, BlockPos pos, BlockState state,
                                 @Nullable Inventory sink) {
        if (sink == null) {
            world.breakBlock(pos, true, npc);
            return;
        }
        List<ItemStack> drops = Block.getDroppedStacks(state, world, pos, world.getBlockEntity(pos), npc, npc.getMainHandStack());
        world.breakBlock(pos, false, npc);
        for (ItemStack drop : drops) {
            ItemStack rest = DepositCargoGoal.insert(sink, drop);
            if (!rest.isEmpty()) Block.dropStack(world, pos, rest);
        }
    }

    /** Ô đứng được gần nhất: chân + đầu trống, dưới chân là mặt đứng vững. */
    @Nullable
    private static BlockPos findFreeSpot(PathAwareEntity npc, World world) {
        BlockPos origin = npc.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.iterate(origin.add(-SEARCH_RADIUS, -2, -SEARCH_RADIUS),
                origin.add(SEARCH_RADIUS, 3, SEARCH_RADIUS))) {
            double dist = origin.getSquaredDistance(pos);
            if (dist >= bestDist || !world.isChunkLoaded(pos)) continue;
            if (!isFree(world, pos) || !isFree(world, pos.up())) continue;
            BlockPos below = pos.down();
            if (!world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP)) continue;
            if (!world.getFluidState(pos).isEmpty()) continue;
            best = pos.toImmutable();
            bestDist = dist;
        }
        return best;
    }

    private static boolean isFree(World world, BlockPos pos) {
        return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }
}
