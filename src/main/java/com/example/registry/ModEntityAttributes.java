package com.example.registry;

import com.example.entity.SoldierNPCEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

public class ModEntityAttributes {
    public static void register() {
        FabricDefaultAttributeRegistry.register(
                ModEntities.SOLDIER_NPC,
                SoldierNPCEntity.createAttributes()
        );
    }

}
