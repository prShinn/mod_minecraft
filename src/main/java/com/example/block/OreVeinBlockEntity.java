package com.example.block;

import com.example.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.util.Identifier;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.math.Vec3d;

public class OreVeinBlockEntity extends BlockEntity {

    public int durability = 128;

    public OreVeinBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ORE_VEIN_BLOCK_ENTITY, pos, state);
    }

    public java.util.List<ItemStack> mine(ServerWorld world, BlockPos pos, MobEntity miner) {
        if (durability <= 0 || world.getBlockEntity(pos) != this) return java.util.List.of();

        // Giảm độ bền
        durability--;
        this.markDirty();

        // 1. Tạo hiệu ứng nứt block và âm thanh (đào đá)
        world.playSound(null, pos, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1.0F, 1.0F);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, this.getCachedState()),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                10, // số lượng particle
                0.25, 0.25, 0.25, // spread
                0.05 // tốc độ
        );

        // 2. Drop vật phẩm ngẫu nhiên từ Loot Table
        Identifier lootTableId = new Identifier("datnc_mod", "gameplay/ore_vein_mining");
        LootTable lootTable = world.getServer().getLootManager().getLootTable(lootTableId);

        LootContextParameterSet.Builder builder = new LootContextParameterSet.Builder(world)
                .add(LootContextParameters.ORIGIN, Vec3d.ofCenter(pos))
                .add(LootContextParameters.THIS_ENTITY, miner);
        
        ObjectArrayList<ItemStack> loot = lootTable.generateLoot(builder.build(LootContextTypes.COMMAND));

        if (durability <= 0) {
            world.breakBlock(pos, false, miner);
        }

        return loot;
    }



    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("Durability", durability);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains("Durability")) {
            this.durability = nbt.getInt("Durability");
        }
    }
}
