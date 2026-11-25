package com.example.registry;

import com.example.ExampleMod;
import com.example.block.*;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {

    public static BlockEntityType<OreVeinBlockEntity> ORE_VEIN_BLOCK_ENTITY;

    // ===== PROFESSION CHEST BLOCK ENTITIES =====
    public static BlockEntityType<FarmerChestBlockEntity> FARMER_CHEST_BE;
    public static BlockEntityType<MinerChestBlockEntity> MINER_CHEST_BE;
    public static BlockEntityType<LumberjackChestBlockEntity> LUMBERJACK_CHEST_BE;
    public static BlockEntityType<FishermanChestBlockEntity> FISHERMAN_CHEST_BE;
    public static BlockEntityType<RancherChestBlockEntity> RANCHER_CHEST_BE;
    public static BlockEntityType<CookChestBlockEntity> COOK_CHEST_BE;

    public static BlockEntityType<BuilderChestBlockEntity> BUILDER_CHEST_BE;
    public static BlockEntityType<BlueprintBlockEntity> BLUEPRINT_BE;

    public static BlockEntityType<ProfessionChestBlockEntity> UTILITY_CHEST;

    public static void register() {
        UTILITY_CHEST = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                new Identifier(ExampleMod.MODID, "utility_chest"),
                FabricBlockEntityTypeBuilder.create((pos, state) -> new ProfessionChestBlockEntity(UTILITY_CHEST, pos, state),
                        ModBlocks.FOOD_CHEST, ModBlocks.SOLDIER_CHEST).build());
        ORE_VEIN_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(ExampleMod.MODID, "ore_vein_block_entity"),
                FabricBlockEntityTypeBuilder.create(OreVeinBlockEntity::new, ModBlocks.ORE_VEIN).build()
        );

        FARMER_CHEST_BE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(ExampleMod.MODID, "farmer_chest_be"),
                FabricBlockEntityTypeBuilder.create(FarmerChestBlockEntity::new, ModBlocks.FARMER_CHEST).build()
        );

        MINER_CHEST_BE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(ExampleMod.MODID, "miner_chest_be"),
                FabricBlockEntityTypeBuilder.create(MinerChestBlockEntity::new, ModBlocks.MINER_CHEST).build()
        );

        LUMBERJACK_CHEST_BE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(ExampleMod.MODID, "lumberjack_chest_be"),
                FabricBlockEntityTypeBuilder.create(LumberjackChestBlockEntity::new, ModBlocks.LUMBERJACK_CHEST).build()
        );

        FISHERMAN_CHEST_BE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(ExampleMod.MODID, "fisherman_chest_be"),
                FabricBlockEntityTypeBuilder.create(FishermanChestBlockEntity::new, ModBlocks.FISHERMAN_CHEST).build()
        );

        RANCHER_CHEST_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                new Identifier(ExampleMod.MODID, "rancher_chest_be"),
                FabricBlockEntityTypeBuilder.create(RancherChestBlockEntity::new, ModBlocks.RANCHER_CHEST).build());

        COOK_CHEST_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                new Identifier(ExampleMod.MODID, "cook_chest_be"),
                FabricBlockEntityTypeBuilder.create(CookChestBlockEntity::new, ModBlocks.COOK_CHEST).build());

        BUILDER_CHEST_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                new Identifier(ExampleMod.MODID, "builder_chest_be"),
                FabricBlockEntityTypeBuilder.create(BuilderChestBlockEntity::new, ModBlocks.BUILDER_CHEST).build());

        BLUEPRINT_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                new Identifier(ExampleMod.MODID, "blueprint_be"),
                FabricBlockEntityTypeBuilder.create(BlueprintBlockEntity::new,
                        ModBlocks.BLUEPRINTS.toArray(new net.minecraft.block.Block[0])).build());
    }
}
