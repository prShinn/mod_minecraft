package com.example.item;

import com.example.entity.FishermanNpcEntity;
import com.example.registry.ModEntities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;

public class FishermanTokenItem extends Item implements WorkChestToken {

    public FishermanTokenItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return summon(world, user, hand, null);
    }

    @Override
    public TypedActionResult<ItemStack> summon(World world, PlayerEntity user, Hand hand, BlockPos chestPos) {
        ItemStack itemStack = user.getStackInHand(hand);

        if (!world.isClient) {
            ServerWorld sw = (ServerWorld) world;
            FishermanNpcEntity npc = ModEntities.FISHERMAN_NPC.create(sw);
            if (npc != null) {
                WorkChestToken.restore(npc, itemStack);
                BlockPos pos = chestPos == null ? user.getBlockPos() : chestPos.up();
                npc.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, user.getYaw(), 0.0F);
                npc.setOwnerUUID(user.getUuid());
                if (!WorkChestToken.spawn(sw, npc, user, chestPos)) return TypedActionResult.fail(itemStack);
                npc.relocateWorkChest(); // token có thể mang vị trí rương cũ ở xa
                
                if (!user.isCreative()) {
                    itemStack.decrement(1);
                }
            }
        }
        return TypedActionResult.success(itemStack, world.isClient());
    }
}
