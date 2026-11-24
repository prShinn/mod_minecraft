package com.example.item;

import com.example.entity.LumberjackNpcEntity;
import com.example.registry.ModEntities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class LumberJackTokenItem extends Item implements WorkChestToken {
    public LumberJackTokenItem(Settings settings) {
        super(settings);
    }
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        return summon(world, player, hand, null);
    }

    @Override
    public TypedActionResult<ItemStack> summon(World world, PlayerEntity player, Hand hand, BlockPos chestPos) {
        if (world.isClient) return TypedActionResult.success(player.getStackInHand(hand));
        ServerWorld sw = (ServerWorld) world;
        HitResult hitResult = player.raycast(5.0, 0.0F, false);
        Vec3d spawnPos;
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            BlockPos targetPos = blockHit.getBlockPos().offset(blockHit.getSide());
            spawnPos = Vec3d.ofCenter(targetPos); // spawn ở giữa block
        } else {
            // Nếu không nhắm vào block thì spawn trước mặt player
            spawnPos = player.getPos().add(player.getRotationVec(1.0F).multiply(2));
        }

        BlockPos pos = chestPos == null ? BlockPos.ofFloored(spawnPos) : chestPos.up();
        LumberjackNpcEntity npc = ModEntities.LUMBERJACK_NPC.create(sw);
        if (npc == null) return TypedActionResult.fail(player.getStackInHand(hand));
        ItemStack stack = player.getStackInHand(hand);
        if (stack.hasNbt() && stack.getNbt().contains("LumberjackEntityTag")) {
            npc.readCustomDataFromNbt(stack.getNbt().getCompound("LumberjackEntityTag"));

            if (stack.getNbt().getCompound("LumberjackEntityTag").contains("Health")) {
                npc.setHealth(stack.getNbt().getCompound("LumberjackEntityTag").getFloat("Health"));
            }
        }
        // Set vị trí, owner, equipment
        WorkChestToken.restore(npc, stack);
        npc.setOwnerUUID(player.getUuid());
        npc.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYaw(), 0);
        // Spawn
        if (!WorkChestToken.spawn(sw, npc, player, chestPos)) return TypedActionResult.fail(stack);
        npc.relocateWorkChest(); // token có thể mang vị trí rương cũ ở xa
        // Trừ item nếu không phải creative
        if (!player.isCreative()) stack.decrement(1);
        return TypedActionResult.consume(stack);
    }
}
