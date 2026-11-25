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
