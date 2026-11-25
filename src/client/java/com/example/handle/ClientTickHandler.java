package com.example.handle;

import com.example.ai.soldier.ModeNpc;
import com.example.entity.SoldierNPCEntity;
import com.example.gui.ModKeybinds;
import com.example.registry.ModPackets;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
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
                    // Server kiểm chủ/khoảng cách rồi mở GUI (ScreenHandler) để trang bị đồng bộ an toàn
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeVarInt(npc.getId());
                    ClientPlayNetworking.send(ModPackets.OPEN_SOLDIER_GUI, buf);
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

                        // getEntityById cần network id (int), không phải UUID.hashCode() → tìm theo UUID
                        SoldierNPCEntity npc = null;
                        for (Entity e : client.world.getEntities()) {
                            if (e instanceof SoldierNPCEntity soldier && soldier.getUuid().equals(npcId)) {
                                npc = soldier;
                                break;
                            }
                        }
                        if (npc != null) {
                            ModeNpc.ModeMove newMode = ModeNpc.ModeMove.values()[modeOrdinal];
                            npc.setMoveMode(newMode);
                            System.out.println("CLIENT: Updated NPC mode to " + newMode);
                        }
                    });
                }
        );
    }


}

