package com.example.entity;

import com.example.ai.builder.BuildStructureGoal;
import com.example.ai.worker.DepositCargoGoal;
import com.example.ai.worker.WorkerWanderGoal;
import com.example.block.BuilderChestBlockEntity;
import com.example.block.ProfessionChestBlockEntity;
import com.example.entity.base.WorkerNpcEntity;
import com.example.registry.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;

/**
 * Thợ xây: lấy vật liệu từ Rương Thợ xây và xây các công trình (block bản vẽ) của chủ.
 * Hết vật liệu thì đứng chờ; xong thì trả vật liệu thừa về rương.
 */
public class BuilderNpcEntity extends WorkerNpcEntity {

    public BuilderNpcEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void addWorkGoals() {
        this.goalSelector.add(5, new DepositCargoGoal(this));
        this.goalSelector.add(6, new BuildStructureGoal(this));
        this.goalSelector.add(9, new WorkerWanderGoal(this));
    }

    @Override
    protected Class<? extends ProfessionChestBlockEntity> chestType() {
        return BuilderChestBlockEntity.class;
    }

    @Override
    public Item tokenItem() {
        return ModItems.BUILDER_TOKEN;
    }

    @Override
    public String professionName() {
        return "Thợ xây";
    }

    @Override
    protected ItemStack workTool() {
        return new ItemStack(Items.BRICK);
    }
}
