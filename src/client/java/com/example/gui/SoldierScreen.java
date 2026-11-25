package com.example.gui;

import com.example.ai.soldier.ModeNpc;
import com.example.entity.SoldierNPCEntity;
import com.example.registry.ModPackets;
import com.example.screen.SoldierScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import static com.example.screen.SoldierScreenHandler.*;

/**
 * Màn hình Binh lính: hình NPC, chỉ số (máu, đói, cấp, kinh nghiệm, chế độ), ô giáp/vũ khí, túi đồ ăn
 * và túi đồ người chơi — kéo thả hoặc shift-click để trang bị.
 */
public class SoldierScreen extends HandledScreen<SoldierScreenHandler> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int TEXT = 0xFF404040;
    private static final int STATS_X = 100;

    private ButtonWidget modeButton;
    private int shownMode = -1;

    public SoldierScreen(SoldierScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 222;
        this.playerInventoryTitleY = PLAYER_INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        modeButton = ButtonWidget.builder(Text.empty(), b -> sendToggleMode())
                .dimensions(x + STATS_X - 2, y + 70, 72, 20).build();
        addDrawableChild(modeButton);
        refreshModeButton();
    }

    @Nullable
    private SoldierNPCEntity npc() {
        if (client == null || client.world == null) return null;
        Entity e = client.world.getEntityById(handler.getNpcEntityId());
        return e instanceof SoldierNPCEntity soldier ? soldier : null;
    }

    private void sendToggleMode() {
        SoldierNPCEntity npc = npc();
        if (npc == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(npc.getUuid());
        ClientPlayNetworking.send(ModPackets.NPC_MOVE_MODE, buf);
    }

    private void refreshModeButton() {
        int mode = handler.getStat(PROP_MOVE_MODE);
        if (mode == shownMode || modeButton == null) return;
        shownMode = mode;
        ModeNpc.ModeMove[] modes = ModeNpc.ModeMove.values();
        modeButton.setMessage(Text.literal(modes[MathHelper.clamp(mode, 0, modes.length - 1)].displayName()));
        modeButton.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Bấm để đổi chế độ di chuyển")));
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        refreshModeButton();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // Khung kiểu vanilla
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, PANEL);
        context.fill(x, y, x + backgroundWidth - 1, y + 1, LIGHT);
        context.fill(x, y, x + 1, y + backgroundHeight - 1, LIGHT);
        context.fill(x + 1, y + backgroundHeight - 1, x + backgroundWidth, y + backgroundHeight, DARK);
        context.fill(x + backgroundWidth - 1, y + 1, x + backgroundWidth, y + backgroundHeight, DARK);

        for (Slot slot : handler.slots) {
            drawSlotFrame(context, x + slot.x, y + slot.y);
        }

        // Ô xem trước NPC
        int px = x + 26, py = y + 16;
        context.fill(px, py, px + 50, py + 74, SLOT_DARK);
        context.fill(px + 1, py + 1, px + 49, py + 73, 0xFF000000);
        SoldierNPCEntity npc = npc();
        if (npc != null) {
            InventoryScreen.drawEntity(context, px + 25, py + 68, 30,
                    (float) (px + 25 - mouseX), (float) (py + 20 - mouseY), npc);
        }

        drawStatBar(context, x + STATS_X, y + 26, handler.getStat(PROP_HEALTH_X10),
                Math.max(1, handler.getStat(PROP_MAX_HEALTH_X10)), 0xFFD03030);
        drawStatBar(context, x + STATS_X, y + 44, handler.getStat(PROP_HUNGER),
                SoldierNPCEntity.MAX_HUNGER, 0xFFC08030);
        drawStatBar(context, x + STATS_X, y + 62, handler.getStat(PROP_XP_PERMILLE), 1000, 0xFF60C040);
    }

    private static void drawSlotFrame(DrawContext context, int sx, int sy) {
        context.fill(sx - 1, sy - 1, sx + 17, sy, SLOT_DARK);
        context.fill(sx - 1, sy - 1, sx, sy + 17, SLOT_DARK);
        context.fill(sx, sy + 16, sx + 17, sy + 17, LIGHT);
        context.fill(sx + 16, sy, sx + 17, sy + 17, LIGHT);
        context.fill(sx, sy, sx + 16, sy + 16, SLOT_BG);
    }

    private static void drawStatBar(DrawContext context, int bx, int by, int value, int max, int color) {
        int width = 68;
        int filled = (int) (width * MathHelper.clamp(value / (float) max, 0f, 1f));
        context.fill(bx, by, bx + width, by + 4, SLOT_DARK);
        context.fill(bx, by, bx + filled, by + 4, color);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, titleX, titleY, TEXT, false);
        context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, TEXT, false);

        int hp10 = handler.getStat(PROP_HEALTH_X10);
        int maxHp10 = handler.getStat(PROP_MAX_HEALTH_X10);
        context.drawText(textRenderer, Text.literal("Cấp " + handler.getStat(PROP_LEVEL)), STATS_X, 6, TEXT, false);
        context.drawText(textRenderer, Text.literal("Máu " + formatTenth(hp10) + "/" + formatTenth(maxHp10)),
                STATS_X, 17, TEXT, false);
        context.drawText(textRenderer, Text.literal("Độ đói " + handler.getStat(PROP_HUNGER) + "/" + SoldierNPCEntity.MAX_HUNGER),
                STATS_X, 35, TEXT, false);
        context.drawText(textRenderer, Text.literal("Kinh nghiệm " + handler.getStat(PROP_XP_PERMILLE) / 10 + "%"),
                STATS_X, 53, TEXT, false);
        context.drawText(textRenderer, Text.literal("Túi đồ ăn"), FOOD_X, FOOD_Y - 10, TEXT, false);
    }

    private static String formatTenth(int valueX10) {
        return valueX10 % 10 == 0 ? String.valueOf(valueX10 / 10) : (valueX10 / 10) + "." + (valueX10 % 10);
    }
}
