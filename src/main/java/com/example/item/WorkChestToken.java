package com.example.item;

import com.example.block.ProfessionChestBlockEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public interface WorkChestToken {
    TypedActionResult<ItemStack> summon(World world, PlayerEntity player, Hand hand, BlockPos chestPos);

    static void restore(PathAwareEntity npc, ItemStack stack) {
        if (stack.hasNbt() && stack.getNbt().contains("RecalledNpc")) {
            npc.readNbt(stack.getNbt().getCompound("RecalledNpc"));
        }
    }

    static void save(PathAwareEntity npc, ItemStack token) {
        NbtCompound data = npc.writeNbt(new NbtCompound());
        data.remove("UUID");
        token.getOrCreateNbt().put("RecalledNpc", data);
    }

    static boolean spawn(ServerWorld world, PathAwareEntity npc, PlayerEntity player, BlockPos chestPos) {
        if (chestPos == null) return world.spawnEntity(npc);
        if (!(world.getBlockEntity(chestPos) instanceof ProfessionChestBlockEntity chest)
                || !chest.canOpen(player) || !world.isSpaceEmpty(npc)) return false;
        if (!world.spawnEntity(npc)) return false;
        return true;
    }
}
