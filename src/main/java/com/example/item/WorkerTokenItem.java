package com.example.item;

import com.example.entity.base.WorkerNpcEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.function.Supplier;

/** Token triệu hồi NPC nghề kế thừa WorkerNpcEntity (Thợ chăn nuôi, Đầu bếp...). */
public class WorkerTokenItem extends Item implements WorkChestToken {
    private final Supplier<EntityType<? extends WorkerNpcEntity>> entityType;

    public WorkerTokenItem(Settings settings, Supplier<EntityType<? extends WorkerNpcEntity>> entityType) {
        super(settings);
        this.entityType = entityType;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return summon(world, user, hand, null);
    }

    @Override
    public TypedActionResult<ItemStack> summon(World world, PlayerEntity user, Hand hand, BlockPos chestPos) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack, true);

        ServerWorld serverWorld = (ServerWorld) world;
        WorkerNpcEntity npc = entityType.get().create(serverWorld);
        if (npc == null) return TypedActionResult.fail(stack);

        WorkChestToken.restore(npc, stack);
        BlockPos pos = chestPos == null ? user.getBlockPos() : chestPos.up();
        npc.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, user.getYaw(), 0.0F);
        npc.setOwnerUUID(user.getUuid());
        if (!WorkChestToken.spawn(serverWorld, npc, user, chestPos)) return TypedActionResult.fail(stack);
        npc.relocateWorkChest(); // token có thể mang vị trí rương cũ ở xa

        if (!user.isCreative()) stack.decrement(1);
        return TypedActionResult.success(stack, false);
    }
}
