package com.example.registry;

import com.example.entity.FarmerNpcEntity;
import com.example.entity.LumberjackNpcEntity;
import com.example.entity.FishermanNpcEntity;
import com.example.entity.MinerNpcEntity;
import com.example.entity.SoldierNPCEntity;
import com.example.entity.base.WorkerNpcEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

public class ModEntityAttributes {
    public static void register() {
        FabricDefaultAttributeRegistry.register(
                ModEntities.SOLDIER_NPC,
                SoldierNPCEntity.createAttributes()
        );
        FabricDefaultAttributeRegistry.register(
                ModEntities.FARMER_NPC,
                SoldierNPCEntity.createAttributes()
        );
        FabricDefaultAttributeRegistry.register(
                ModEntities.LUMBERJACK_NPC,
                SoldierNPCEntity.createAttributes()
        );
        // ===== MINER NPC — dùng attributes riêng của MinerNpcEntity =====
        FabricDefaultAttributeRegistry.register(
                ModEntities.MINER_NPC,
                WorkerNpcEntity.createWorkerAttributes()
        );

        FabricDefaultAttributeRegistry.register(
                ModEntities.FISHERMAN_NPC,
                WorkerNpcEntity.createWorkerAttributes()
        );
        FabricDefaultAttributeRegistry.register(ModEntities.RANCHER_NPC, WorkerNpcEntity.createWorkerAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.COOK_NPC, WorkerNpcEntity.createWorkerAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.BUILDER_NPC, WorkerNpcEntity.createWorkerAttributes());
    }
}
