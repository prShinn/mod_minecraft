package com.example.registry;

import com.example.ExampleMod;
import com.example.entity.FarmerNpcEntity;
import com.example.entity.LumberjackNpcEntity;
import com.example.entity.FishermanNpcEntity;
import com.example.entity.MinerNpcEntity;
import com.example.entity.SoldierNPCEntity;
import com.example.entity.RancherNpcEntity;
import com.example.entity.CookNpcEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEntities {

    public static EntityType<SoldierNPCEntity> SOLDIER_NPC;
    public static EntityType<FarmerNpcEntity> FARMER_NPC;
    public static EntityType<LumberjackNpcEntity> LUMBERJACK_NPC;
    public static EntityType<MinerNpcEntity> MINER_NPC;
    public static EntityType<FishermanNpcEntity> FISHERMAN_NPC;
    public static EntityType<RancherNpcEntity> RANCHER_NPC;
    public static EntityType<CookNpcEntity> COOK_NPC;
    public static EntityType<com.example.entity.BuilderNpcEntity> BUILDER_NPC;

    public static void register() {
        SOLDIER_NPC = Registry.register(
                Registries.ENTITY_TYPE, new Identifier(ExampleMod.MODID, "soldier_npc"),
                FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, SoldierNPCEntity::new)
                        .dimensions(EntityDimensions.fixed(0.6F, 1.95F)).build());

        FARMER_NPC = Registry.register(
                Registries.ENTITY_TYPE, new Identifier(ExampleMod.MODID, "farmer_npc"),
                FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, FarmerNpcEntity::new)
                        .dimensions(EntityDimensions.fixed(0.6F, 1.95F)).build());

        LUMBERJACK_NPC = Registry.register(
                Registries.ENTITY_TYPE, new Identifier(ExampleMod.MODID, "lumberjack_npc"),
                FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, LumberjackNpcEntity::new)
                        .dimensions(EntityDimensions.fixed(0.6F, 1.95F)).build());

        // ===== MINER NPC =====
        MINER_NPC = Registry.register(
                Registries.ENTITY_TYPE, new Identifier(ExampleMod.MODID, "miner_npc"),
                FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, MinerNpcEntity::new)
                        .dimensions(EntityDimensions.fixed(0.6F, 1.95F)).build());

        // ===== FISHERMAN NPC =====
        FISHERMAN_NPC = Registry.register(
                Registries.ENTITY_TYPE, new Identifier(ExampleMod.MODID, "fisherman_npc"),
                FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, FishermanNpcEntity::new)
                        .dimensions(EntityDimensions.fixed(0.6F, 1.95F)).build());

        // ===== RANCHER & COOK NPC =====
        RANCHER_NPC = Registry.register(
                Registries.ENTITY_TYPE, new Identifier(ExampleMod.MODID, "rancher_npc"),
                FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, RancherNpcEntity::new)
                        .dimensions(EntityDimensions.fixed(0.6F, 1.95F)).build());

        COOK_NPC = Registry.register(
                Registries.ENTITY_TYPE, new Identifier(ExampleMod.MODID, "cook_npc"),
                FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, CookNpcEntity::new)
                        .dimensions(EntityDimensions.fixed(0.6F, 1.95F)).build());

        BUILDER_NPC = Registry.register(
                Registries.ENTITY_TYPE, new Identifier(ExampleMod.MODID, "builder_npc"),
                FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, com.example.entity.BuilderNpcEntity::new)
                        .dimensions(EntityDimensions.fixed(0.6F, 1.95F)).build());
    }
}
