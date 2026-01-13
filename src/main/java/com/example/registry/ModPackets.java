package com.example.registry;

import com.example.ai.soldỉier.ModeNpc;
import com.example.entity.SoldierNPCEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.UUID;

public class ModPackets {
    public static final Identifier NPC_EQUIP =
            new Identifier("datnc_mod", "npc_equip");
    public static final Identifier NPC_MOVE_MODE =
            new Identifier("datnc_mod", "npc_move_mode");
    public static final Identifier SYNC_MODE_PACKET =
            new Identifier("datnc_mod", "sync_mode");
    public static void registerServer() {
        registerMoveMode();
    }
    private static void registerMoveMode() {
        ServerPlayNetworking.registerGlobalReceiver(
                NPC_MOVE_MODE,
                (server, player, handler, buf, sender) -> {
                    System.out.println("SERVER RECEIVED PACKET");
                    UUID npcId = buf.readUuid();
                    server.execute(() -> {
                        Entity e = player.getServerWorld().getEntity(npcId);
                        if (!(e instanceof SoldierNPCEntity npc)) return;
                        // ✅ Đổi mode
                        ModeNpc.ModeMove newMode =
                                npc.getMoveMode() == ModeNpc.ModeMove.FOLLOW
                                        ? ModeNpc.ModeMove.WANDER
                                        : ModeNpc.ModeMove.FOLLOW;

                        npc.setMoveMode(newMode);
                        // ✅ Gửi feedback cho player
                        player.sendMessage(
                                Text.literal("NPC mode changed to: " + newMode)
                                        .formatted(Formatting.GREEN),
                                true
                        );
                        ServerPlayNetworking.send(player, SYNC_MODE_PACKET, buf);
                    });
                }
        );
    }
}
