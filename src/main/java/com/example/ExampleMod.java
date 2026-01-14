package com.example;

import com.example.registry.ModEntities;
import com.example.registry.ModEntityAttributes;
import com.example.registry.ModItems;
import com.example.registry.ModPackets;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod implements ModInitializer {
	public static final String MODID = "datnc_mod";

	@Override
	public void onInitialize() {
		ModEntities.register();
		ModItems.register();
		ModEntityAttributes.register();
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS)
				.register(entries -> {
					entries.add(ModItems.SOLDIER_TOKEN);
				});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS)
				.register(entries -> {
					entries.add(ModItems.FARMER_TOKEN);
				});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS)
				.register(entries -> {
					entries.add(ModItems.LUMBERJACK_TOKEN);
				});
		ModPackets.registerServer();
	}
}