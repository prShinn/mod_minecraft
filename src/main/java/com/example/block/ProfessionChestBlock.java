package com.example.block;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Base class cho Rương Nghề Nghiệp (Profession Chest).
 * - Mở GUI 3×9 (27 slot) giống Chest thường khi player click.
 * - Khi bị phá, drop toàn bộ nội dung inventory + công cụ đã dùng craft.
 * - Không có animation nắp mở (đơn giản hóa rendering).
 */
public abstract class ProfessionChestBlock extends BlockWithEntity {

    protected ProfessionChestBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        // Dùng model JSON bình thường (không phải entity renderer)
        return BlockRenderType.MODEL;
    }

    /**
     * Player right-click → mở GUI inventory 3×9.
     */
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        ItemStack held = player.getStackInHand(hand);
        boolean foodChest = state.isOf(com.example.registry.ModBlocks.FOOD_CHEST);
        if (!world.isClient) {
            if (!(world.getBlockEntity(pos) instanceof ProfessionChestBlockEntity chest)
                    || !chest.canOpen(player)) return ActionResult.FAIL;
            if (chest instanceof LumberjackChestBlockEntity lumberChest && player.isSneaking() && held.isEmpty()) {
                chest.setOwner(player);
                int radius = lumberChest.cycleCutRadius(); // 3 → 5 → 8 → tắt
                int side = radius * 2 + 1;
                player.sendMessage(net.minecraft.text.Text.literal(radius > 0
                        ? "Vùng chặt: " + radius + " block mỗi phía (" + side + "×" + side
                          + "). Shift + chuột phải để đổi: 3 → 5 → 8 → tắt."
                        : "Đã tắt vùng chặt. Shift + chuột phải để bật lại (3 block)."), true);
                return ActionResult.CONSUME;
            }
            if (chest instanceof MinerChestBlockEntity minerChest && player.isSneaking() && held.isEmpty()
                    && player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                chest.setOwner(player);
                QuarryNetworking.sendOpen(serverPlayer, minerChest); // Shift + chuột phải tay không: cấu hình vùng đào
                return ActionResult.CONSUME;
            }
            if (!foodChest && held.getItem() instanceof com.example.item.WorkChestToken token) {
                if (!matchesToken(state, held)) {
                    player.sendMessage(net.minecraft.text.Text.translatable("message.datnc_mod.chest_unavailable"), true);
                    return ActionResult.FAIL;
                }
                ActionResult result = token.summon(world, player, hand, pos).getResult();
                if (result == ActionResult.FAIL) {
                    player.sendMessage(net.minecraft.text.Text.translatable("message.datnc_mod.chest_spawn_failed"), true);
                }
                return result;
            }
            // Rương thức ăn đặt từ bản cũ chưa có trong sổ chung: mở ra là ghi sổ
            if (foodChest && world instanceof net.minecraft.server.world.ServerWorld server) {
                com.example.ai.foodChest.FoodChestRegistry.get(server).register(pos);
            }
            NamedScreenHandlerFactory factory = state.createScreenHandlerFactory(world, pos);
            if (factory != null) {
                player.openHandledScreen(factory);
            }
        }
        return ActionResult.SUCCESS;
    }

    private boolean matchesToken(BlockState state, ItemStack held) {
        return state.isOf(com.example.registry.ModBlocks.FARMER_CHEST) && held.isOf(com.example.registry.ModItems.FARMER_TOKEN)
                || state.isOf(com.example.registry.ModBlocks.LUMBERJACK_CHEST) && held.isOf(com.example.registry.ModItems.LUMBERJACK_TOKEN)
                || state.isOf(com.example.registry.ModBlocks.MINER_CHEST) && held.isOf(com.example.registry.ModItems.MINER_TOKEN)
                || state.isOf(com.example.registry.ModBlocks.FISHERMAN_CHEST) && held.isOf(com.example.registry.ModItems.FISHERMAN_TOKEN)
                || state.isOf(com.example.registry.ModBlocks.RANCHER_CHEST) && held.isOf(com.example.registry.ModItems.RANCHER_TOKEN)
                || state.isOf(com.example.registry.ModBlocks.COOK_CHEST) && held.isOf(com.example.registry.ModItems.COOK_TOKEN)
                || state.isOf(com.example.registry.ModBlocks.BUILDER_CHEST) && held.isOf(com.example.registry.ModItems.BUILDER_TOKEN)
                || state.isOf(com.example.registry.ModBlocks.SOLDIER_CHEST) && held.isOf(com.example.registry.ModItems.SOLDIER_TOKEN);
    }

    /** Khi đặt rương nghề: ghi người đặt làm chủ rương. */
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         @Nullable net.minecraft.entity.LivingEntity placer,
                         ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        // Rương thức ăn: ghi vào sổ chung của làng để NPC ở xa (tới 96 block) tìm được
        if (world instanceof net.minecraft.server.world.ServerWorld server && state.isOf(com.example.registry.ModBlocks.FOOD_CHEST)) {
            com.example.ai.foodChest.FoodChestRegistry.get(server).register(pos);
        }
        if (!world.isClient && !state.isOf(com.example.registry.ModBlocks.FOOD_CHEST)
                && placer instanceof PlayerEntity player
                && world.getBlockEntity(pos) instanceof ProfessionChestBlockEntity chest) {
            chest.setOwner(player);
            // Vùng đào nằm phía trước rương theo hướng người chơi nhìn lúc đặt
            if (chest instanceof MinerChestBlockEntity minerChest) minerChest.ensureDirection(player.getHorizontalFacing());
        }
    }

    /**
     * Khi block bị thay thế (bị phá):
     * - Scatter toàn bộ items trong inventory ra đất (giống rương thường).
     * - Bản thân rương rơi theo loot table. KHÔNG trả lại công cụ đã dùng để chế tạo
     *   (công cụ bị tiêu hao vào rương; tránh craft-phá lặp để lấy lại cúp/cuốc).
     */
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos,
                                BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity be = world.getBlockEntity(pos);
            if (world instanceof net.minecraft.server.world.ServerWorld server && state.isOf(com.example.registry.ModBlocks.FOOD_CHEST)) {
                com.example.ai.foodChest.FoodChestRegistry.get(server).remove(pos);
            }
            if (!world.isClient && be instanceof ProfessionChestBlockEntity chestBe) {
                // Scatter inventory items
                ItemScatterer.spawn(world, pos, chestBe);
                world.updateComparators(pos, this);
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    @Override
    @Nullable
    public abstract BlockEntity createBlockEntity(BlockPos pos, BlockState state);
}
