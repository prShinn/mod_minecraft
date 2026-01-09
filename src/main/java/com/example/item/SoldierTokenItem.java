package com.example.item;

import com.example.entity.SoldierNPCEntity;
import com.example.registry.ModEntities;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.entity.player.PlayerEntity;

public class SoldierTokenItem extends Item {

    public SoldierTokenItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient) {
            return TypedActionResult.success(player.getStackInHand(hand));
        }

        ServerWorld sw = (ServerWorld) world;

        Vec3d look = player.getRotationVec(1.0F);
        Vec3d spawnPos = player.getPos().add(look.multiply(2));

        BlockPos pos = BlockPos.ofFloored(spawnPos);

        SoldierNPCEntity npc = ModEntities.SOLDIER_NPC.create(sw);
        if (npc == null) {
            return TypedActionResult.fail(player.getStackInHand(hand));
        }

        npc.refreshPositionAndAngles(
                pos.getX() + 0.5,
                pos.getY(),
                pos.getZ() + 0.5,
                player.getYaw(),
                0
        );
        npc.initEquipment(sw.getRandom(), sw.getLocalDifficulty(pos));
        // ===== OWNER =====
        npc.setOwner(player);

        // SPAWN
        sw.spawnEntity(npc);

        if (!player.isCreative()) {
            player.getStackInHand(hand).decrement(1);
        }

        return TypedActionResult.consume(player.getStackInHand(hand));
    }

}


