package com.example.registry;

import com.example.ExampleMod;
import com.example.block.*;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import com.example.build.StructurePlan;
import com.example.build.StructurePlans;
import com.example.item.BlueprintItem;
import com.example.item.PatrolFlagItem;
import net.minecraft.item.BlockItem;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlocks {

    /**
     * Block "Mỏ Quặng" — tương tự coal ore về độ cứng và âm thanh.
     * Texture lấy từ coal_ore của vanilla.
     */
    public static Block ORE_VEIN;

    // ===== PROFESSION CHESTS =====
    public static Block FARMER_CHEST;
    public static Block MINER_CHEST;
    public static Block LUMBERJACK_CHEST;
    public static Block FISHERMAN_CHEST;
    public static Block RANCHER_CHEST;
    public static Block COOK_CHEST;

    private static Block registerUtilityChest(String name) {
        Identifier id = new Identifier(ExampleMod.MODID, name);
        Block block = Registry.register(Registries.BLOCK, id,
                new UtilityChestBlock(FabricBlockSettings.copyOf(Blocks.CHEST)));
        Registry.register(Registries.ITEM, id, new BlockItem(block, new FabricItemSettings()));
        return block;
    }

    private static Block registerProfessionChest(String name, Block block) {
        Identifier id = new Identifier(ExampleMod.MODID, name);
        Registry.register(Registries.BLOCK, id, block);
        Registry.register(Registries.ITEM, id, new BlockItem(block, new FabricItemSettings()));
        return block;
    }

    public static Block FOOD_CHEST;
    public static Block SOLDIER_CHEST;
    public static Block PATROL_FLAG;
    public static Block BUILDER_CHEST;
    /** Block bản vẽ theo thứ tự StructurePlans.ALL. */
    public static final java.util.List<Block> BLUEPRINTS = new java.util.ArrayList<>();

    public static void register() {
        FOOD_CHEST = registerUtilityChest("food_chest");
        SOLDIER_CHEST = registerUtilityChest("soldier_chest");
        // ===== ORE VEIN =====
        ORE_VEIN = Registry.register(
                Registries.BLOCK,
                new Identifier(ExampleMod.MODID, "ore_vein"),
                new OreVeinBlock(
                        FabricBlockSettings.copyOf(Blocks.COAL_ORE)
                                .hardness(3.0f)
                                .resistance(3.0f)
                                .requiresTool()
                )
        );
        Registry.register(
                Registries.ITEM,
                new Identifier(ExampleMod.MODID, "ore_vein"),
                new BlockItem(ORE_VEIN, new FabricItemSettings())
        );

        // ===== FARMER CHEST =====
        FARMER_CHEST = Registry.register(
                Registries.BLOCK,
                new Identifier(ExampleMod.MODID, "farmer_chest"),
                new FarmerChestBlock(FabricBlockSettings.copyOf(Blocks.CHEST))
        );
        Registry.register(
                Registries.ITEM,
                new Identifier(ExampleMod.MODID, "farmer_chest"),
                new BlockItem(FARMER_CHEST, new FabricItemSettings())
        );

        // ===== MINER CHEST =====
        MINER_CHEST = Registry.register(
                Registries.BLOCK,
                new Identifier(ExampleMod.MODID, "miner_chest"),
                new MinerChestBlock(FabricBlockSettings.copyOf(Blocks.CHEST))
        );
        Registry.register(
                Registries.ITEM,
                new Identifier(ExampleMod.MODID, "miner_chest"),
                new BlockItem(MINER_CHEST, new FabricItemSettings())
        );

        // ===== LUMBERJACK CHEST =====
        LUMBERJACK_CHEST = Registry.register(
                Registries.BLOCK,
                new Identifier(ExampleMod.MODID, "lumberjack_chest"),
                new LumberjackChestBlock(FabricBlockSettings.copyOf(Blocks.CHEST))
        );
        Registry.register(
                Registries.ITEM,
                new Identifier(ExampleMod.MODID, "lumberjack_chest"),
                new BlockItem(LUMBERJACK_CHEST, new FabricItemSettings())
        );

        // ===== FISHERMAN CHEST =====
        FISHERMAN_CHEST = Registry.register(
                Registries.BLOCK,
                new Identifier(ExampleMod.MODID, "fisherman_chest"),
                new FishermanChestBlock(FabricBlockSettings.copyOf(Blocks.CHEST))
        );
        Registry.register(
                Registries.ITEM,
                new Identifier(ExampleMod.MODID, "fisherman_chest"),
                new BlockItem(FISHERMAN_CHEST, new FabricItemSettings())
        );

        // ===== RANCHER & COOK CHEST =====
        RANCHER_CHEST = registerProfessionChest("rancher_chest", new RancherChestBlock(FabricBlockSettings.copyOf(Blocks.CHEST)));
        COOK_CHEST = registerProfessionChest("cook_chest", new CookChestBlock(FabricBlockSettings.copyOf(Blocks.CHEST)));

        // ===== BUILDER CHEST + BLUEPRINTS (Thợ xây) =====
        BUILDER_CHEST = registerProfessionChest("builder_chest", new BuilderChestBlock(FabricBlockSettings.copyOf(Blocks.CHEST)));
        for (StructurePlan plan : StructurePlans.ALL) {
            Identifier id = new Identifier(ExampleMod.MODID, "blueprint_" + plan.id());
            BlueprintBlock block = Registry.register(Registries.BLOCK, id, new BlueprintBlock(
                    FabricBlockSettings.create().noCollision().nonOpaque().strength(0.5f).sounds(BlockSoundGroup.WOOD), plan));
            Registry.register(Registries.ITEM, id, new BlueprintItem(block, new FabricItemSettings()));
            BLUEPRINTS.add(block);
        }

        // ===== PATROL FLAG (Soldier đi tuần giữa các cờ) =====
        Identifier flagId = new Identifier(ExampleMod.MODID, "patrol_flag");
        PATROL_FLAG = Registry.register(Registries.BLOCK, flagId,
                new PatrolFlagBlock(FabricBlockSettings.create()
                        .noCollision()
                        .nonOpaque()
                        .strength(0.5f)
                        .sounds(BlockSoundGroup.WOOD)));
        Registry.register(Registries.ITEM, flagId, new PatrolFlagItem(PATROL_FLAG, new FabricItemSettings()));
    }
}
