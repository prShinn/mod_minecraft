package com.example.registry;

import com.example.ai.soldier.ModeNpc;
import com.example.entity.SoldierNPCEntity;
import com.example.screen.SoldierScreenHandler;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.UUID;

public class ModPackets {
    public static final Identifier NPC_MOVE_MODE =
            new Identifier("datnc_mod", "npc_move_mode");
    public static final Identifier SYNC_MODE_PACKET =
            new Identifier("datnc_mod", "sync_mode");
    /** Client → server: mở GUI Binh lính (payload: network id của entity, varint). */
    public static final Identifier OPEN_SOLDIER_GUI =
            new Identifier("datnc_mod", "open_soldier_gui");

    public static void registerServer() {
        registerMoveMode();
        registerOpenSoldierGui();
    }

    private static void registerOpenSoldierGui() {
        ServerPlayNetworking.registerGlobalReceiver(
                OPEN_SOLDIER_GUI,
                (server, player, handler, buf, sender) -> {
                    if (buf.readableBytes() < 1 || buf.readableBytes() > 5) return;
                    int entityId = buf.readVarInt();
                    server.execute(() -> {
                        Entity e = player.getServerWorld().getEntityById(entityId);
                        if (!(e instanceof SoldierNPCEntity npc) || !SoldierScreenHandler.canManage(npc, player)) return;
                        player.openHandledScreen(new ExtendedScreenHandlerFactory() {
                            @Override
                            public void writeScreenOpeningData(ServerPlayerEntity target, PacketByteBuf out) {
                                out.writeVarInt(npc.getId());
                            }

                            @Override
                            public Text getDisplayName() {
                                String role = npc.getNameNpc();
                                return Text.literal(role == null || role.isEmpty() ? "Binh lính" : role);
                            }

                            @Override
                            public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity p) {
                                return new SoldierScreenHandler(syncId, inventory, npc);
                            }
                        });
                    });
                }
        );
    }
    private static void registerMoveMode() {
        ServerPlayNetworking.registerGlobalReceiver(
                NPC_MOVE_MODE,
                (server, player, handler, buf, sender) -> {
                    if (buf.readableBytes() != 16) return;
                    UUID npcId = buf.readUuid();
                    server.execute(() -> {
                        Entity e = player.getServerWorld().getEntity(npcId);
                        if (!(e instanceof SoldierNPCEntity npc) || npc.getOwner() != player
                                || npc.squaredDistanceTo(player) > 64) return;
                        // ✅ Đổi mode
                        ModeNpc.ModeMove newMode =
                                npc.getMoveMode() == ModeNpc.ModeMove.FOLLOW
                                        ? ModeNpc.ModeMove.WANDER
                                        : ModeNpc.ModeMove.FOLLOW;

                        npc.setMoveMode(newMode);
                        // ✅ Gửi feedback cho player
                        player.sendMessage(
                                Text.literal("Chế độ binh lính: " + newMode.displayName())
                                        .formatted(Formatting.GREEN),
                                true
                        );
                        PacketByteBuf syncBuf = PacketByteBufs.create();
                        syncBuf.writeUuid(npcId);
                        syncBuf.writeInt(newMode.ordinal()); // Gửi mode mới

                        ServerPlayNetworking.send(player, SYNC_MODE_PACKET, syncBuf);
                    });
                }
        );
    }
}
