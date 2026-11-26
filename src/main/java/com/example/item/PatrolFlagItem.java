package com.example.item;

import com.example.block.PatrolFlagState;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

/** Item Cờ Tuần Tra — chặn đặt quá {@link PatrolFlagState#MAX_FLAGS} cờ trong cùng 1 khu vực. */
public class PatrolFlagItem extends BlockItem {
    public PatrolFlagItem(Block block, Settings settings) {
        super(block, settings);
    }

    /**
     * Cầm cờ trên tay: thanh thông báo hiện khoảng cách tới cờ gần nhất của mình, đặt ở đây có nối vào tuyến
     * không (≤ {@link PatrolFlagState#RANGE} block), là cờ thứ mấy, và khu này còn đặt được không.
     * Nửa giây một lần, chỉ khi đang cầm cờ — gần như không tốn gì.
     */
    @Override
    public void inventoryTick(net.minecraft.item.ItemStack stack, net.minecraft.world.World world,
                              net.minecraft.entity.Entity entity, int slot, boolean selected) {
        if (world.isClient || !(entity instanceof net.minecraft.server.network.ServerPlayerEntity player)
                || player.age % 10 != 0) return;
        if (!selected && player.getOffHandStack() != stack) return;
        player.sendMessage(hint((ServerWorld) world, player), true);
    }

    private static Text hint(ServerWorld world, PlayerEntity player) {
        PatrolFlagState state = PatrolFlagState.get(world);
        net.minecraft.util.math.BlockPos here = player.getBlockPos();
        int range = (int) PatrolFlagState.RANGE;
        int inArea = state.countNear(player.getUuid(), here);
        if (inArea >= PatrolFlagState.MAX_FLAGS) {
            return Text.literal("Khu này đã đủ " + PatrolFlagState.MAX_FLAGS + " cờ (trong " + range
                    + " block) — đi xa hơn để đặt cờ tiếp").formatted(net.minecraft.util.Formatting.RED);
        }
        net.minecraft.util.math.BlockPos nearest = state.nearestOwned(player.getUuid(), here);
        if (nearest == null) {
            return Text.literal("Chưa có cờ nào — đặt ở đây sẽ là cờ số 1 của tuyến tuần tra")
                    .formatted(net.minecraft.util.Formatting.WHITE);
        }
        int dist = (int) Math.round(Math.sqrt(nearest.getSquaredDistance(here)));
        if (dist <= range) {
            int next = state.routeFor(world, player.getUuid(), nearest).size() + 1;
            return Text.literal("Cờ gần nhất: " + dist + "/" + range + " block ✔ nối tuyến — đặt ở đây là cờ số " + next
                    + " · khu này " + inArea + "/" + PatrolFlagState.MAX_FLAGS + " cờ")
                    .formatted(net.minecraft.util.Formatting.GREEN);
        }
        return Text.literal("Cờ gần nhất: " + dist + " block ✘ xa quá " + range
                + " → đặt ở đây sẽ thành tuyến MỚI (lùi lại " + (dist - range) + " block để nối tuyến)")
                .formatted(net.minecraft.util.Formatting.YELLOW);
    }

    @Override
    public ActionResult place(ItemPlacementContext context) {
        PlayerEntity player = context.getPlayer();
        if (!context.getWorld().isClient && player != null) {
            int existing = PatrolFlagState.get((ServerWorld) context.getWorld())
                    .countNear(player.getUuid(), context.getBlockPos());
            if (existing >= PatrolFlagState.MAX_FLAGS) {
                player.sendMessage(Text.translatable("message.datnc_mod.patrol_flag_limit", PatrolFlagState.MAX_FLAGS), true);
                return ActionResult.FAIL;
            }
        }
        return super.place(context);
    }
}
