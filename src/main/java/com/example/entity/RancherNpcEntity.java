package com.example.entity;

import com.example.ai.rancher.BreedAnimalsGoal;
import com.example.ai.rancher.CollectAnimalDropsGoal;
import com.example.ai.rancher.CullAnimalsGoal;
import com.example.ai.rancher.ShearSheepGoal;
import com.example.ai.worker.DepositCargoGoal;
import com.example.ai.worker.WorkerWanderGoal;
import com.example.block.ProfessionChestBlockEntity;
import com.example.block.RancherChestBlockEntity;
import com.example.entity.base.WorkerNpcEntity;
import com.example.registry.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * Thợ chăn nuôi: làm việc quanh Rương Thợ chăn nuôi —
 * xén lông cừu, cho vật nuôi ăn để sinh sản, nhặt trứng/lông/da và nộp về rương.
 */
public class RancherNpcEntity extends WorkerNpcEntity {

    /** Túi thức ăn cho vật nuôi (lấy từ rương), tách riêng khỏi đồ ăn của NPC. */
    private final SimpleInventory feed = new SimpleInventory(9);

    public RancherNpcEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void addWorkGoals() {
        this.goalSelector.add(5, new DepositCargoGoal(this));
        this.goalSelector.add(6, new CollectAnimalDropsGoal(this));
        this.goalSelector.add(7, new ShearSheepGoal(this));
        this.goalSelector.add(8, new CullAnimalsGoal(this));
        this.goalSelector.add(8, new BreedAnimalsGoal(this));
        this.goalSelector.add(9, new WorkerWanderGoal(this));
    }

    @Override
    protected boolean usesAutoDeposit() {
        return true;
    }

    @Override
    protected Class<? extends ProfessionChestBlockEntity> chestType() {
        return RancherChestBlockEntity.class;
    }

    @Override
    public Item tokenItem() {
        return ModItems.RANCHER_TOKEN;
    }

    @Override
    public String professionName() {
        return "Thợ chăn nuôi";
    }

    @Override
    protected ItemStack workTool() {
        return new ItemStack(Items.SHEARS);
    }

    public SimpleInventory getFeed() {
        return feed;
    }

    @Override
    protected void writeWorkerNbt(NbtCompound nbt) {
        nbt.put("AnimalFeed", feed.toNbtList());
    }

    @Override
    protected void readWorkerNbt(NbtCompound nbt) {
        if (nbt.contains("AnimalFeed")) feed.readNbtList(nbt.getList("AnimalFeed", 10));
    }

    @Override
    protected void dropWorkerItems() {
        dropInventory(feed);
    }
}
