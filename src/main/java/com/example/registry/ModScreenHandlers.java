package com.example.registry;

import com.example.ExampleMod;
import com.example.screen.SoldierScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class ModScreenHandlers {
    public static ScreenHandlerType<SoldierScreenHandler> SOLDIER;

    public static void register() {
        SOLDIER = Registry.register(Registries.SCREEN_HANDLER, new Identifier(ExampleMod.MODID, "soldier"),
                new ExtendedScreenHandlerType<>(SoldierScreenHandler::new));
    }
}
