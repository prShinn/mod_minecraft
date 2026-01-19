package com.example.handle;

import com.example.ai.soldier.ModeNpc;
import com.example.entity.SoldierNPCEntity;
import com.example.gui.ModKeybinds;
import com.example.gui.SoldierNPCEquipmentScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.Entity;

import java.util.UUID;

import static com.example.registry.ModPackets.SYNC_MODE_PACKET;

public class ClientTickHandler {
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ModKeybinds.OPEN_NPC_GUI.wasPressed()) {
                if (client.targetedEntity instanceof SoldierNPCEntity npc) {
                    client.setScreen(new SoldierNPCEquipmentScreen(npc));
                }
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(
                SYNC_MODE_PACKET,
                (client, handler, buf, responseSender) -> {
                    UUID npcId = buf.readUuid();
                    int modeOrdinal = buf.readInt();

                    client.execute(() -> {
                        if (client.world == null) return;

                        Entity e = client.world.getEntityById(npcId.hashCode()); // hoặc dùng cách khác
                        if (e instanceof SoldierNPCEntity npc) {
                            ModeNpc.ModeMove newMode = ModeNpc.ModeMove.values()[modeOrdinal];
                            npc.setMoveMode(newMode);
                            System.out.println("CLIENT: Updated NPC mode to " + newMode);
                        }
                    });
                }
        );
    }


}

