package com.example.handle;

import com.example.entity.SoldierNPCEntity;
import com.example.gui.ModKeybinds;
import com.example.gui.SoldierNPCEquipmentScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class ClientTickHandler {
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ModKeybinds.OPEN_NPC_GUI.wasPressed()) {
                if (client.targetedEntity instanceof SoldierNPCEntity npc) {
                    client.setScreen(new SoldierNPCEquipmentScreen(npc));
                }
            }
        });
    }
}

