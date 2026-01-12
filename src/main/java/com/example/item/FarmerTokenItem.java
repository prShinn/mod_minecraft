package com.example.item;

import com.example.entity.FarmerNpcEntity;
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

public class FarmerTokenItem extends Item {
    public FarmerTokenItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
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

        BlockPos pos = BlockPos.ofFloored(spawnPos);
        FarmerNpcEntity npc = ModEntities.FARMER_NPC.create(sw);
        if (npc == null) return TypedActionResult.fail(player.getStackInHand(hand));
        ItemStack stack = player.getStackInHand(hand);
        if (stack.hasNbt() && stack.getNbt().contains("FarmerEntityTag")) {
            npc.readCustomDataFromNbt(stack.getNbt().getCompound("FarmerEntityTag"));

            if (stack.getNbt().getCompound("FarmerEntityTag").contains("Health")) {
                npc.setHealth(stack.getNbt().getCompound("FarmerEntityTag").getFloat("Health"));
            }
        }

        // Set vị trí, owner, equipment
        npc.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYaw(), 0);


        // Spawn
        sw.spawnEntity(npc);

        // Trừ item nếu không phải creative
        if (!player.isCreative()) stack.decrement(1);
        return TypedActionResult.consume(stack);
    }
}
