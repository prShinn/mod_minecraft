package com.example.item;

import com.example.block.BlueprintBlock;
import com.example.build.BuildSiteState;
import com.example.build.StructurePlan;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Item bản vẽ: tooltip nêu kích thước + vật liệu; giới hạn số công trình mỗi chủ. */
public class BlueprintItem extends BlockItem {
    private final StructurePlan plan;

    public BlueprintItem(BlueprintBlock block, Settings settings) {
        super(block, settings);
        this.plan = block.getPlan();
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal(plan.width() + "×" + plan.depth() + "×" + plan.height() + " block").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Cần: " + plan.costText()).formatted(Formatting.GOLD));
        tooltip.add(Text.literal("Đặt xuống đất, Thợ xây sẽ lấy vật liệu từ Rương Thợ xây.").formatted(Formatting.DARK_GRAY));
    }

    @Override
    public ActionResult place(ItemPlacementContext context) {
        PlayerEntity player = context.getPlayer();
        if (!context.getWorld().isClient && player != null
                && BuildSiteState.get((ServerWorld) context.getWorld())
                        .countFor((ServerWorld) context.getWorld(), player.getUuid())
                >= BuildSiteState.MAX_SITES_PER_OWNER) {
            player.sendMessage(Text.literal("Đã có tối đa " + BuildSiteState.MAX_SITES_PER_OWNER
                    + " công trình đang chờ xây. Hãy hoàn thành hoặc phá bớt."), true);
            return ActionResult.FAIL;
        }
        return super.place(context);
    }
}
