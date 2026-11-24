package com.example.block;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
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
                boolean enabled = lumberChest.toggleCutting();
                player.sendMessage(net.minecraft.text.Text.literal(enabled
                        ? "Đã bật vùng chặt: 8 block mỗi phía, thấp hơn 6 và cao hơn 64 block tính từ rương."
                        : "Đã tắt vùng chặt."), true);
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
                || state.isOf(com.example.registry.ModBlocks.SOLDIER_CHEST) && held.isOf(com.example.registry.ModItems.SOLDIER_TOKEN);
    }

    /**
     * Khi block được đặt xuống: chuyển StoredTool từ ItemStack NBT sang BlockEntity.
     * Điều này đảm bảo khi phá rương, công cụ sẽ được trả lại cho người chơi.
     */
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         @Nullable net.minecraft.entity.LivingEntity placer,
                         ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient && !state.isOf(com.example.registry.ModBlocks.FOOD_CHEST)
                && placer instanceof PlayerEntity player
                && world.getBlockEntity(pos) instanceof ProfessionChestBlockEntity chest) {
            chest.setOwner(player);
        }
        if (!world.isClient && itemStack.hasNbt()) {
            NbtCompound nbt = itemStack.getNbt();
            if (nbt != null && nbt.contains("StoredTool")) {
                BlockEntity be = world.getBlockEntity(pos);
                if (be instanceof ProfessionChestBlockEntity chestBe) {
                    chestBe.setStoredTool(ItemStack.fromNbt(nbt.getCompound("StoredTool")));
                }
            }
        }
    }

    /**
     * Khi block bị thay thế (bị phá):
     * - Scatter toàn bộ items trong inventory ra đất.
     * - Drop lại công cụ đã dùng để craft (nếu có).
     */
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos,
                                BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity be = world.getBlockEntity(pos);
            if (!world.isClient && be instanceof ProfessionChestBlockEntity chestBe) {
                // Scatter inventory items
                ItemScatterer.spawn(world, pos, chestBe);
                // Drop lại công cụ craft
                ItemStack storedTool = chestBe.getStoredTool();
                if (!storedTool.isEmpty()) {
                    ItemScatterer.spawn(world, (double) pos.getX() + 0.5,
                            (double) pos.getY() + 0.5,
                            (double) pos.getZ() + 0.5,
                            storedTool);
                }
                world.updateComparators(pos, this);
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    @Override
    @Nullable
    public abstract BlockEntity createBlockEntity(BlockPos pos, BlockState state);
}
