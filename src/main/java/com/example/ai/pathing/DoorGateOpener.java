package com.example.ai.pathing;

import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Mở cửa gỗ / cổng rào nằm trên đường đi của NPC, đi qua rồi đóng lại (vật nuôi không chạy ra).
 * Chỉ đóng những cái chính NPC đã mở. Cửa sắt / cổng không mở bằng tay thì bỏ qua.
 */
public class DoorGateOpener {
    private static final double OPEN_RANGE_SQ = 2.5 * 2.5;
    private static final double CLOSE_DISTANCE_SQ = 2.0 * 2.0;
    private static final int MIN_OPEN_TICKS = 10;
    private static final int MAX_OPEN_TICKS = 200;

    private record Opened(BlockPos pos, long at) {
    }

    private final List<Opened> opened = new ArrayList<>();

    /** Gọi ở server tick (mỗi 2 tick là đủ). */
    public void tick(PathAwareEntity npc, boolean gates) {
        World world = npc.getWorld();
        Path path = npc.getNavigation().getCurrentPath();
        if (path != null && !path.isFinished()) {
            int from = path.getCurrentNodeIndex();
            for (int i = from; i < Math.min(from + 2, path.getLength()); i++) {
                BlockPos node = path.getNodePos(i);
                tryOpen(npc, world, node, gates);
                tryOpen(npc, world, node.up(), gates);
            }
        }
        long now = world.getTime();
        opened.removeIf(o -> {
            long age = now - o.at();
            if (age < MIN_OPEN_TICKS) return false;
            boolean away = npc.squaredDistanceTo(Vec3d.ofBottomCenter(o.pos())) > CLOSE_DISTANCE_SQ;
            if (!away && age < MAX_OPEN_TICKS) return false;
            if (isOccupied(world, o.pos())) return false; // có con gì đứng giữa cửa → chờ nó đi rồi mới đóng
            setOpen(npc, world, o.pos(), false);
            return true;
        });
    }

    /** NPC chết / bị thu hồi: đóng hết cửa đang mở dở. */
    public void closeAll(PathAwareEntity npc) {
        for (Opened o : opened) {
            if (!isOccupied(npc.getWorld(), o.pos())) setOpen(npc, npc.getWorld(), o.pos(), false);
        }
        opened.clear();
    }

    private void tryOpen(PathAwareEntity npc, World world, BlockPos pos, boolean gates) {
        if (!world.isChunkLoaded(pos) || npc.squaredDistanceTo(Vec3d.ofBottomCenter(pos)) > OPEN_RANGE_SQ) return;
        BlockState state = world.getBlockState(pos);
        BlockPos key = pos;
        if (state.getBlock() instanceof DoorBlock) {
            if (state.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
                key = pos.down();
                state = world.getBlockState(key);
                if (!(state.getBlock() instanceof DoorBlock)) return;
            }
            if (!DoorBlock.canOpenByHand(world, key) || state.get(DoorBlock.OPEN)) return;
        } else if (state.getBlock() instanceof FenceGateBlock) {
            if (!gates || state.get(FenceGateBlock.OPEN)) return;
        } else {
            return;
        }
        BlockPos k = key.toImmutable();
        if (opened.stream().anyMatch(o -> o.pos().equals(k))) return;
        setOpen(npc, world, k, true);
        opened.add(new Opened(k, world.getTime()));
    }

    private static boolean isOccupied(World world, BlockPos pos) {
        return !world.getEntitiesByClass(LivingEntity.class, new Box(pos), e -> true).isEmpty();
    }

    private static void setOpen(PathAwareEntity npc, World world, BlockPos pos, boolean open) {
        if (!world.isChunkLoaded(pos)) return;
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock door) {
            if (state.get(DoorBlock.OPEN) != open) door.setOpen(npc, world, state, pos, open);
        } else if (state.getBlock() instanceof FenceGateBlock && state.get(FenceGateBlock.OPEN) != open) {
            world.setBlockState(pos, state.with(FenceGateBlock.OPEN, open), 10);
            world.playSound(null, pos, open ? SoundEvents.BLOCK_FENCE_GATE_OPEN : SoundEvents.BLOCK_FENCE_GATE_CLOSE,
                    SoundCategory.BLOCKS, 1.0F, world.getRandom().nextFloat() * 0.1F + 0.9F);
            world.emitGameEvent(npc, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        }
    }
}
