package com.example;

import com.example.gui.ModKeybinds;
import com.example.handle.ClientTickHandler;
import net.fabricmc.api.ClientModInitializer;

public class ExampleModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.
        ModEntityRenderers.register();

        // handle GUI thao tác
        ModKeybinds.register();
        ClientTickHandler.register();

    }
}