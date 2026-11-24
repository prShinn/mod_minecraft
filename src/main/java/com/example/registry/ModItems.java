package com.example.registry;

import com.example.ExampleMod;
import com.example.item.FarmerTokenItem;
import com.example.item.LumberJackTokenItem;
import com.example.item.FishermanTokenItem;
import com.example.item.MinerTokenItem;
import com.example.item.SoldierTokenItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {
    public static Item SOLDIER_TOKEN;
    public static Item FARMER_TOKEN;
    public static Item LUMBERJACK_TOKEN;
    public static Item MINER_TOKEN;
    public static Item FISHERMAN_TOKEN;

    public static void register() {
        SOLDIER_TOKEN = Registry.register(Registries.ITEM,
                new Identifier(ExampleMod.MODID, "soldier_token"),
                new SoldierTokenItem(new Item.Settings().maxCount(1)));

        FARMER_TOKEN = Registry.register(Registries.ITEM,
                new Identifier(ExampleMod.MODID, "farmer_token"),
                new FarmerTokenItem(new Item.Settings().maxCount(1)));

        LUMBERJACK_TOKEN = Registry.register(Registries.ITEM,
                new Identifier(ExampleMod.MODID, "lumberjack_token"),
                new LumberJackTokenItem(new Item.Settings().maxCount(1)));

        // ===== MINER TOKEN =====
        MINER_TOKEN = Registry.register(Registries.ITEM,
                new Identifier(ExampleMod.MODID, "miner_token"),
                new MinerTokenItem(new Item.Settings().maxCount(1)));

        // ===== FISHERMAN TOKEN =====
        FISHERMAN_TOKEN = Registry.register(Registries.ITEM,
                new Identifier(ExampleMod.MODID, "fisherman_token"),
                new FishermanTokenItem(new Item.Settings().maxCount(1)));
    }
}
