package com.example.item;

import com.example.village.VillageOverview;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Sổ quản lý làng: chuột phải để xem trạng thái mọi NPC của mình trong bán kính 128 block. */
public class VillageLedgerItem extends Item {
    public VillageLedgerItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            VillageOverview.sendTo(player, true);
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Chuột phải: xem trạng thái NPC trong " + VillageOverview.RANGE + " block")
                .formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Phím tắt mặc định: K").formatted(Formatting.DARK_GRAY));
    }
}
