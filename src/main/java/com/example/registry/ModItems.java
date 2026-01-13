package com.example.registry;

import com.example.ExampleMod;
import com.example.item.FarmerTokenItem;
import com.example.item.LumberJackTokenItem;
import com.example.item.SoldierTokenItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {
    public static Item SOLDIER_TOKEN;
    public static Item FARMER_TOKEN;
    public static Item LUMBERJACK_TOKEN;

    public static void register() {
        SOLDIER_TOKEN = Registry.register(Registries.ITEM, new Identifier(ExampleMod.MODID, "soldier_token"),
                new SoldierTokenItem(new Item.Settings().maxCount(1)));
        FARMER_TOKEN = Registry.register(Registries.ITEM, new Identifier(ExampleMod.MODID, "farmer_token"),
                new FarmerTokenItem(new Item.Settings().maxCount(1)));
        LUMBERJACK_TOKEN = Registry.register(Registries.ITEM, new Identifier(ExampleMod.MODID, "lumberjack_token"),
                new LumberJackTokenItem(new Item.Settings().maxCount(1)));
    }

}
