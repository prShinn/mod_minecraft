package com.example;

import com.example.gui.ModKeybinds;
import com.example.handle.ClientTickHandler;
import net.fabricmc.api.ClientModInitializer;

public class ExampleModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.
        ModEntityRenderers.register();
        // Rương nghề là khối đặc (che mặt block bên cạnh) nên texture phải đặc 100% và vẽ ở lớp solid mặc định.
        // KHÔNG đưa vào lớp cutout: pixel trong suốt sẽ thành lỗ nhìn xuyên ra khoảng trống phía sau (x-ray).

        // handle GUI thao tác
        ModKeybinds.register();
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(
                com.example.registry.ModScreenHandlers.SOLDIER, com.example.gui.SoldierScreen::new);
        ClientTickHandler.register();
        com.example.gui.VillageOverviewScreen.registerClient();
        com.example.gui.QuarryConfigScreen.registerClient();
        com.example.render.QuarryPreviewRenderer.register();

    }
}