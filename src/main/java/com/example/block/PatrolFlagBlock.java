package com.example.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/**
 * Cờ Tuần Tra — đặt 2–3 cây để Soldier của người đặt tự đi tuần giữa các điểm.
 * Không có va chạm (Soldier đi xuyên qua điểm cờ), cần mặt đất đặc bên dưới.
 */
public class PatrolFlagBlock extends Block {
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;

    private static final VoxelShape POLE = Block.createCuboidShape(7, 0, 7, 9, 16, 9);
    // Lá cờ hướng ra theo FACING (model gốc hướng EAST)
    private static final VoxelShape SHAPE_EAST = VoxelShapes.union(POLE, Block.createCuboidShape(9, 7, 7.5, 16, 15, 8.5));
    private static final VoxelShape SHAPE_SOUTH = VoxelShapes.union(POLE, Block.createCuboidShape(7.5, 7, 9, 8.5, 15, 16));
    private static final VoxelShape SHAPE_WEST = VoxelShapes.union(POLE, Block.createCuboidShape(0, 7, 7.5, 7, 15, 8.5));
    private static final VoxelShape SHAPE_NORTH = VoxelShapes.union(POLE, Block.createCuboidShape(7.5, 7, 0, 8.5, 15, 7));

    public PatrolFlagBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.EAST));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case NORTH -> SHAPE_NORTH;
            default -> SHAPE_EAST;
        };
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // Lá cờ hướng sang phải người đặt để nhìn thấy trọn mặt cờ
        BlockState state = getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().rotateYClockwise());
        return state.canPlaceAt(ctx.getWorld(), ctx.getBlockPos()) ? state : null;
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        BlockPos below = pos.down();
        return world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canPlaceAt(world, pos)) {
            // Đường này không chạy loot table → tự rơi item để người chơi không mất block
            if (world instanceof World w && !w.isClient) Block.dropStacks(state, w, pos);
            return Blocks.AIR.getDefaultState();
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (world.isClient || !(placer instanceof PlayerEntity player)) return;
        PatrolFlagState registry = PatrolFlagState.get((ServerWorld) world);
        registry.add(pos, player.getUuid());
        player.sendMessage(Text.translatable("message.datnc_mod.patrol_flag_placed",
                registry.countNear(player.getUuid(), pos), PatrolFlagState.MAX_FLAGS), true);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClient && !state.isOf(newState.getBlock())) {
            PatrolFlagState.get((ServerWorld) world).remove(pos);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
