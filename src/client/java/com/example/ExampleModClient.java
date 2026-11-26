package com.example;

import com.example.gui.ModKeybinds;
import com.example.handle.ClientTickHandler;
import net.fabricmc.api.ClientModInitializer;

public class ExampleModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.
        ModEntityRenderers.register();
        // Rương nghề: biểu tượng dụng cụ vẽ đè lên mặt thùng có nền trong suốt → cần lớp cutout
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlocks(
                net.minecraft.client.render.RenderLayer.getCutout(),
                com.example.registry.ModBlocks.FARMER_CHEST, com.example.registry.ModBlocks.MINER_CHEST,
                com.example.registry.ModBlocks.LUMBERJACK_CHEST, com.example.registry.ModBlocks.FISHERMAN_CHEST,
                com.example.registry.ModBlocks.RANCHER_CHEST, com.example.registry.ModBlocks.COOK_CHEST,
                com.example.registry.ModBlocks.BUILDER_CHEST, com.example.registry.ModBlocks.FOOD_CHEST,
                com.example.registry.ModBlocks.SOLDIER_CHEST);

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