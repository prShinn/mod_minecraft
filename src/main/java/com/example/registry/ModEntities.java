package com.example.registry;

import com.example.ExampleMod;
import com.example.entity.FarmerNpcEntity;
import com.example.entity.LumberjackNpcEntity;
import com.example.entity.SoldierNPCEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEntities {

    public static EntityType<SoldierNPCEntity> SOLDIER_NPC;
    public static EntityType<FarmerNpcEntity> FARMER_NPC;
    public static EntityType<LumberjackNpcEntity> LUMBERJACK_NPC;


    public static void register() {

        SOLDIER_NPC = Registry.register(
                Registries.ENTITY_TYPE, new Identifier(ExampleMod.MODID, "soldier_npc"),
                FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, SoldierNPCEntity::new).
                        dimensions(EntityDimensions.fixed(0.6F, 1.95F)).build());
        FARMER_NPC = Registry.register(
                Registries.ENTITY_TYPE, new Identifier(ExampleMod.MODID, "farmer_npc"),
                FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, FarmerNpcEntity::new).
                        dimensions(EntityDimensions.fixed(0.6F, 1.95F)).build());
        LUMBERJACK_NPC = Registry.register(
                Registries.ENTITY_TYPE, new Identifier(ExampleMod.MODID, "lumberjack_npc"),
                FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, LumberjackNpcEntity::new).
                        dimensions(EntityDimensions.fixed(0.6F, 1.95F)).build());
    }
}



