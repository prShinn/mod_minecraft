package com.example.block;

import com.example.build.BuildSiteState;
import com.example.build.BuildMaterial;
import com.example.build.StructurePlan;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/**
 * Block bản vẽ: đặt xuống là một "công trình" chờ Thợ xây. Ô của block này là góc trước-trái
 * của công trình; công trình nằm về phía trước và bên phải theo hướng người đặt nhìn.
 */
public class BlueprintBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 2, 16);

    private final StructurePlan plan;

    public BlueprintBlock(Settings settings, StructurePlan plan) {
        super(settings);
        this.plan = plan;
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    public StructurePlan getPlan() {
        return plan;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BlueprintBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState state = getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing());
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
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        // Mọi cách xuất hiện của block bản vẽ đều được đăng ký (chưa chủ); onPlaced sẽ gán chủ nếu do người chơi đặt
        if (!world.isClient && !oldState.isOf(this)) {
            BuildSiteState.get((ServerWorld) world).add(pos, null);
        }
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (world.isClient || !(placer instanceof PlayerEntity player)) return;
        BuildSiteState.get((ServerWorld) world).add(pos, player.getUuid());
        player.sendMessage(Text.literal(plan.name() + " " + plan.width() + "×" + plan.depth() + "×" + plan.height()
                + " — cần: " + plan.costText() + ". Bỏ vật liệu vào Rương Thợ xây."), false);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClient && !state.isOf(newState.getBlock())) {
            BuildSiteState.get((ServerWorld) world).remove(pos);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    /** Chuột phải: xem tiến độ + hiện viền móng công trình bằng hạt. */
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof BlueprintBlockEntity bp)) return ActionResult.PASS;
        int total = plan.steps().size();
        int[] need = bp.remainingNeed();
        StringBuilder missing = new StringBuilder();
        for (BuildMaterial m : BuildMaterial.values()) {
            if (need[m.ordinal()] > 0) {
                if (missing.length() > 0) missing.append(", ");
                missing.append(m.displayName()).append(" ×").append(need[m.ordinal()]);
            }
        }
        player.sendMessage(Text.literal(plan.name() + ": " + bp.doneCount() + "/" + total + " block"
                + (missing.length() > 0 ? " — còn cần: " + missing : "")), true);

        ServerWorld server = (ServerWorld) world;
        Direction facing = bp.facing();
        for (int x = 0; x < plan.width(); x++) {
            for (int z = 0; z < plan.depth(); z++) {
                if (x != 0 && z != 0 && x != plan.width() - 1 && z != plan.depth() - 1) continue;
                BlockPos p = StructurePlan.toWorld(pos, facing, x, 0, z);
                server.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 0.3, p.getZ() + 0.5,
                        1, 0, 0, 0, 0);
            }
        }
        return ActionResult.SUCCESS;
    }
}
