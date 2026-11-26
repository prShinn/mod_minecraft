package com.example.gui;

import com.example.block.QuarryConfig;
import com.example.block.QuarryNetworking;
import com.example.render.QuarryPreviewRenderer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Cấu hình vùng đào của Rương Thợ mỏ (Shift + chuột phải tay không vào rương).
 * Không làm tối nền để vẫn thấy khung vùng đào vẽ trong thế giới khi đang chỉnh.
 */
public class QuarryConfigScreen extends Screen {
    private static final int PANEL_X = 10, PANEL_Y = 30, ROW_H = 24, PANEL_W = 210;
    private static final String[] LABELS = {"Dài", "Rộng", "Sâu (dưới rương)"};

    private final BlockPos chest;
    private int length, width, height;
    private Direction direction;
    private boolean saved;

    public QuarryConfigScreen(BlockPos chest, QuarryConfig config) {
        super(Text.literal("Vùng đào Thợ mỏ"));
        this.chest = chest;
        this.length = config.length();
        this.width = config.width();
        this.height = config.height();
        this.direction = config.direction();
    }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(QuarryNetworking.OPEN, (client, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            QuarryConfig config = QuarryConfig.read(buf);
            client.execute(() -> client.setScreen(new QuarryConfigScreen(pos, config)));
        });
        ClientPlayNetworking.registerGlobalReceiver(QuarryNetworking.PREVIEW, (client, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            QuarryConfig config = QuarryConfig.read(buf);
            client.execute(() -> QuarryPreviewRenderer.showSaved(pos, config));
        });
    }

    private QuarryConfig current() {
        return new QuarryConfig(length, width, height, direction).clamp();
    }

    private void adjust(int field, int delta) {
        QuarryConfig c = new QuarryConfig(length + (field == 0 ? delta : 0), width + (field == 1 ? delta : 0),
                height + (field == 2 ? delta : 0), direction).clamp();
        length = c.length();
        width = c.width();
        height = c.height();
        QuarryPreviewRenderer.showEditing(chest, current());
    }

    @Override
    protected void init() {
        for (int i = 0; i < LABELS.length; i++) {
            final int field = i;
            int y = PANEL_Y + 22 + i * ROW_H;
            // Shift + bấm: ±5
            addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> adjust(field, hasShiftDown() ? -5 : -1))
                    .dimensions(PANEL_X + 90, y, 20, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> adjust(field, hasShiftDown() ? 5 : 1))
                    .dimensions(PANEL_X + 160, y, 20, 20).build());
        }
        int y = PANEL_Y + 22 + LABELS.length * ROW_H + 4;
        addDrawableChild(ButtonWidget.builder(Text.literal("Xoay hướng"), b -> {
            direction = direction.rotateYClockwise();
            QuarryPreviewRenderer.showEditing(chest, current());
        }).dimensions(PANEL_X + 8, y, 194, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Lưu"), b -> save())
                .dimensions(PANEL_X + 8, y + 24, 95, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Huỷ"), b -> close())
                .dimensions(PANEL_X + 107, y + 24, 95, 20).build());
        QuarryPreviewRenderer.showEditing(chest, current());
    }

    private void save() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(chest);
        current().write(buf);
        ClientPlayNetworking.send(QuarryNetworking.SAVE, buf);
        saved = true;
        QuarryPreviewRenderer.showSaved(chest, current()); // hiện ngay, server gửi lại bản đã kiểm tra
        close();
    }

    @Override
    public void removed() {
        if (!saved) QuarryPreviewRenderer.cancelEditing();
        super.removed();
    }

    private int panelHeight() {
        return 22 + LABELS.length * ROW_H + 4 + 48 + 28;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelH = panelHeight();
        context.fill(PANEL_X, PANEL_Y, PANEL_X + PANEL_W, PANEL_Y + panelH, 0xB0101010);
        context.drawText(textRenderer, title, PANEL_X + 8, PANEL_Y + 7, 0xFFD27F, false);
        int[] values = {length, width, height};
        for (int i = 0; i < LABELS.length; i++) {
            int y = PANEL_Y + 22 + i * ROW_H;
            context.drawText(textRenderer, Text.literal(LABELS[i]), PANEL_X + 8, y + 6, 0xFFFFFF, false);
            String v = String.valueOf(values[i]);
            context.drawText(textRenderer, Text.literal(v), PANEL_X + 135 - textRenderer.getWidth(v) / 2, y + 6,
                    0xFFFFFF, false);
        }
        QuarryConfig c = current();
        int infoY = PANEL_Y + 22 + LABELS.length * ROW_H + 4 + 50;
        context.drawText(textRenderer, Text.literal("Hướng: " + directionName(direction) + " · " + c.volume() + " block"),
                PANEL_X + 8, infoY, 0xC0C0C0, false);
        context.drawText(textRenderer, Text.literal("Shift + bấm: ±5 · Khung vàng = vùng đào"),
                PANEL_X + 8, infoY + 12, 0x909090, false);
        super.render(context, mouseX, mouseY, delta);
    }

    private static String directionName(Direction d) {
        return switch (d) {
            case NORTH -> "Bắc";
            case SOUTH -> "Nam";
            case EAST -> "Đông";
            default -> "Tây";
        };
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
