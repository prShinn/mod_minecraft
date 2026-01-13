package com.example.item;

import com.example.entity.SoldierNPCEntity;
import com.example.registry.ModEntities;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.entity.player.PlayerEntity;

//public class SoldierTokenItem extends Item {
//
//    public SoldierTokenItem(Settings settings) {
//        super(settings);
//    }
//
//    @Override
//    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
//        if (world.isClient) {
//            return TypedActionResult.success(player.getStackInHand(hand));
//        }
//
//        ServerWorld sw = (ServerWorld) world;
//
//        Vec3d look = player.getRotationVec(1.0F);
//        Vec3d spawnPos = player.getPos().add(look.multiply(2));
//
//        BlockPos pos = BlockPos.ofFloored(spawnPos);
//
//        SoldierNPCEntity npc = ModEntities.SOLDIER_NPC.create(sw);
//        if (npc == null) {
//            return TypedActionResult.fail(player.getStackInHand(hand));
//        }
//
//        npc.refreshPositionAndAngles(
//                pos.getX() + 0.5,
//                pos.getY(),
//                pos.getZ() + 0.5,
//                player.getYaw(),
//                0
//        );
//        npc.initEquipment(sw.getRandom(), sw.getLocalDifficulty(pos));
//        // ===== OWNER =====
//        npc.setOwner(player);
//
//        // SPAWN
//        sw.spawnEntity(npc);
//
//        if (!player.isCreative()) {
//            player.getStackInHand(hand).decrement(1);
//        }
//
//        return TypedActionResult.consume(player.getStackInHand(hand));
//    }
//    @Override
//    public ActionResult useOnBlock(ItemUsageContext context) {
//        World world = context.getWorld();
//        if (world.isClient) return ActionResult.SUCCESS;
//
//        ItemStack stack = context.getStack();
//        BlockPos pos = context.getBlockPos().offset(context.getSide());
//
//        SoldierNPCEntity npc = ModEntities.SOLDIER_NPC.create(world);
//        if (npc == null) return ActionResult.FAIL;
//
//        // đọc NBT
//        if (stack.hasNbt() && stack.getNbt().contains("EntityTag")) {
//            npc.readCustomDataFromNbt(stack.getNbt().getCompound("EntityTag"));
//
//            if (stack.getNbt().getCompound("EntityTag").contains("Health")) {
//                npc.setHealth(stack.getNbt().getCompound("EntityTag").getFloat("Health"));
//            }
//        }
//
//        npc.refreshPositionAndAngles(
//                pos.getX() + 0.5,
//                pos.getY(),
//                pos.getZ() + 0.5,
//                world.random.nextFloat() * 360F,
//                0
//        );
//
//        world.spawnEntity(npc);
//
//        if (context.getPlayer() == null || !context.getPlayer().isCreative()) {
//            stack.decrement(1);
//        }
//
//        return ActionResult.CONSUME;
//    }
//
//}
public class SoldierTokenItem extends Item {

    public SoldierTokenItem(Settings settings) {
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

        // Tạo NPC
        SoldierNPCEntity npc = ModEntities.SOLDIER_NPC.create(sw);
        if (npc == null) return TypedActionResult.fail(player.getStackInHand(hand));
        // Copy NBT nếu có
        ItemStack stack = player.getStackInHand(hand);
        if (stack.hasNbt() && stack.getNbt().contains("SoldierEntityTag")) {
            npc.readCustomDataFromNbt(stack.getNbt().getCompound("SoldierEntityTag"));

            if (stack.getNbt().getCompound("SoldierEntityTag").contains("Health")) {
                npc.setHealth(stack.getNbt().getCompound("SoldierEntityTag").getFloat("Health"));
            }
        }

        // Set vị trí, owner, equipment
        npc.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYaw(), 0);
        npc.setOwner(player);
        NbtCompound nbt = new NbtCompound();
        if (!nbt.containsUuid("OwnerUUID")) {
            nbt.putUuid("OwnerUUID", player.getUuid());
        }
        if (!nbt.containsUuid("FollowPlayer")) {
            nbt.putUuid("FollowPlayer", player.getUuid());
        }


        // Spawn
        sw.spawnEntity(npc);

        // Trừ item nếu không phải creative
        if (!player.isCreative()) stack.decrement(1);

        return TypedActionResult.consume(stack);
    }
}
