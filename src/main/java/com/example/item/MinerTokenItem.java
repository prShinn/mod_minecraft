package com.example.item;

import com.example.entity.MinerNpcEntity;
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

/**
 * Token Item để spawn MinerNpcEntity.
 * Cách dùng: cầm item và right-click vào block để spawn Miner NPC tại đó.
 * NPC sẽ tự động tìm block Mỏ Quặng trong bán kính 16 block và đào.
 */
public class MinerTokenItem extends Item implements WorkChestToken {

    public MinerTokenItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        return summon(world, player, hand, null);
    }

    @Override
    public TypedActionResult<ItemStack> summon(World world, PlayerEntity player, Hand hand, BlockPos chestPos) {
        // Chỉ xử lý phía server
        if (world.isClient) return TypedActionResult.success(player.getStackInHand(hand));

        ServerWorld sw = (ServerWorld) world;

        // Raycast để tìm vị trí spawn (nhắm vào block)
        HitResult hitResult = player.raycast(5.0, 0.0F, false);
        Vec3d spawnPos;

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            // Spawn ở mặt trên của block được click
            BlockPos targetPos = blockHit.getBlockPos().offset(blockHit.getSide());
            spawnPos = Vec3d.ofCenter(targetPos);
        } else {
            // Fallback: spawn trước mặt player
            spawnPos = player.getPos().add(player.getRotationVec(1.0F).multiply(2));
        }

        // Tạo và spawn Miner NPC
        BlockPos pos = chestPos == null ? BlockPos.ofFloored(spawnPos) : chestPos.up();
        MinerNpcEntity npc = ModEntities.MINER_NPC.create(sw);
        if (npc == null) return TypedActionResult.fail(player.getStackInHand(hand));

        ItemStack stack = player.getStackInHand(hand);

        // Khôi phục data nếu NPC đã được lưu vào NBT của token
        if (stack.hasNbt() && stack.getNbt().contains("MinerEntityTag")) {
            npc.readCustomDataFromNbt(stack.getNbt().getCompound("MinerEntityTag"));
            if (stack.getNbt().getCompound("MinerEntityTag").contains("Health")) {
                npc.setHealth(stack.getNbt().getCompound("MinerEntityTag").getFloat("Health"));
            }
        }

        // Đặt vị trí spawn
        WorkChestToken.restore(npc, stack);
        npc.setOwnerUUID(player.getUuid());
        npc.refreshPositionAndAngles(
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                player.getYaw(), 0
        );

        // Spawn vào world
        if (!WorkChestToken.spawn(sw, npc, player, chestPos)) return TypedActionResult.fail(stack);
        npc.relocateWorkChest(); // token có thể mang vị trí rương cũ ở xa

        // Tiêu thụ 1 item nếu không ở Creative mode
        if (!player.isCreative()) stack.decrement(1);

        return TypedActionResult.consume(stack);
    }
}
