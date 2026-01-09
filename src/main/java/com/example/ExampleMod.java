package com.example;

import com.example.registry.ModEntities;
import com.example.registry.ModEntityAttributes;
import com.example.registry.ModItems;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger("modid");
	public static final String MODID = "datnc_mod";

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		ModEntities.register();        // PHẢI CÓ
		ModItems.register();
		ModEntityAttributes.register();
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS)
				.register(entries -> {
					entries.add(ModItems.SOLDIER_TOKEN);
				});

		LOGGER.info("Hello Fabric world!");
	}
}