package com.example.gui;

import com.example.ai.ModeNpc;
import com.example.entity.SoldierNPCEntity;
import com.example.registry.ModPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

// SoldierNPCEquipmentScreen.java
public class SoldierNPCEquipmentScreen extends Screen {

    private final SoldierNPCEntity npc;
    private ButtonWidget modeButton;
    private ModeNpc.ModeMove lastMode;
    private ModeNpc.ModeMove currentMode;

    public SoldierNPCEquipmentScreen(SoldierNPCEntity npc) {
        super(Text.literal("Đệ tử" + npc.getUuid().toString()));
        this.npc = npc;
    }

    private Text getModeText() {
        // ✅ LẤY TRỰC TIẾP TỪ NPC thay vì dùng biến local
        currentMode = npc.getMoveMode();
        return Text.literal("Mode: " + currentMode.toString())
                .formatted(currentMode == ModeNpc.ModeMove.FOLLOW
                        ? Formatting.GREEN
                        : Formatting.YELLOW);
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 80;
        // ===== MOVE MODE =====
        modeButton = ButtonWidget.builder(
                getModeText(),
                b -> {
                    sendMoveMode();
//                    this.close(); // đóng luôn
                }
        ).dimensions(x, y, 200, 20).build();

        addDrawableChild(modeButton);
    }

    @Override
    public void tick() {
        super.tick();

        if (npc != null && modeButton != null && currentMode != lastMode) {
            lastMode = currentMode;
            modeButton.setMessage(getModeText());
        }
    }

    private void sendMoveMode() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(npc.getUuid());
        ClientPlayNetworking.send(ModPackets.NPC_MOVE_MODE, buf);
    }
}

