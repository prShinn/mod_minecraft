package com.example;

import com.example.registry.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod implements ModInitializer {
	public static final String MODID = "datnc_mod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

	@Override
	public void onInitialize() {
		// 1. Register blocks (phải trước items vì BlockItem phụ thuộc vào Block)
		// 0. Đọc cấu hình config/datnc_mod.json
		com.example.config.ModConfig.load();

		ModBlocks.register();
		ModBlockEntities.register();

		// 2. Register entities
		ModEntities.register();

		// 3. Register items (bao gồm token items)
		ModItems.register();

		// 4. Register entity attributes
		ModEntityAttributes.register();

		// 5. Register recipes (custom serializers)
		ModRecipes.register();

		// 6. Thêm items vào Creative Tab (INGREDIENTS)
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
			entries.add(ModItems.SOLDIER_TOKEN);
			entries.add(ModItems.FARMER_TOKEN);
			entries.add(ModItems.LUMBERJACK_TOKEN);
			entries.add(ModItems.MINER_TOKEN);
			entries.add(ModItems.FISHERMAN_TOKEN);
			entries.add(ModItems.RANCHER_TOKEN);
			entries.add(ModItems.COOK_TOKEN);
			entries.add(ModItems.BUILDER_TOKEN);
		});

		// 7. Thêm block vào Creative Tab (BUILDING_BLOCKS)
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> {
			entries.add(ModBlocks.ORE_VEIN.asItem());
			entries.add(ModBlocks.FOOD_CHEST.asItem());
			entries.add(ModBlocks.SOLDIER_CHEST.asItem());
			entries.add(ModBlocks.FARMER_CHEST.asItem());
			entries.add(ModBlocks.MINER_CHEST.asItem());
			entries.add(ModBlocks.LUMBERJACK_CHEST.asItem());
			entries.add(ModBlocks.FISHERMAN_CHEST.asItem());
			entries.add(ModBlocks.RANCHER_CHEST.asItem());
			entries.add(ModBlocks.COOK_CHEST.asItem());
			entries.add(ModBlocks.PATROL_FLAG.asItem());
			entries.add(ModBlocks.BUILDER_CHEST.asItem());
			ModBlocks.BLUEPRINTS.forEach(block -> entries.add(block.asItem()));
		});

		// 8. GUI (screen handler) + packets
		ModScreenHandlers.register();
		ModPackets.registerServer();

		LOGGER.info("datnc_mod initialized!");
	}
}