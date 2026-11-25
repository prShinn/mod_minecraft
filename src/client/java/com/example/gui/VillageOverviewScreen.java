package com.example.gui;

import com.example.village.VillageNpcInfo;
import com.example.village.VillageNpcStatus;
import com.example.village.VillageOverview;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Sổ quản lý làng: danh sách NPC của người chơi, trạng thái hiện tại, nút đánh dấu (phát sáng 10s).
 * Dữ liệu do server gửi (VillageOverview), tự làm mới mỗi 2 giây khi đang mở.
 */
public class VillageOverviewScreen extends Screen {
    private static final int PANEL_W = 360;
    private static final int ROW_H = 26;
    private static final int HEADER_H = 52;
    private static final int MARGIN = 12;
    private static final int REFRESH_TICKS = 40;
    private static final int MARK_W = 56;

    private List<VillageNpcInfo> rows = List.of();
    private int total;
    private boolean problemsOnly;
    private int scroll;
    private int refreshTicks;

    private int left;
    private int top;
    private int bottom;

    public VillageOverviewScreen() {
        super(Text.literal("Sổ quản lý làng"));
    }

    // ===== Mạng =====

    public static void requestData(boolean open) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(open);
        ClientPlayNetworking.send(VillageOverview.REQUEST, buf);
    }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(VillageOverview.DATA, (client, handler, buf, sender) -> {
            boolean open = buf.readBoolean();
            int total = buf.readVarInt();
            int count = Math.min(buf.readVarInt(), VillageOverview.MAX_ENTRIES);
            List<VillageNpcInfo> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) list.add(VillageNpcInfo.read(buf));
            client.execute(() -> {
                if (client.currentScreen instanceof VillageOverviewScreen screen) {
                    screen.setData(list, total);
                } else if (open && client.currentScreen == null) {
                    VillageOverviewScreen screen = new VillageOverviewScreen();
                    screen.setData(list, total);
                    client.setScreen(screen);
                }
            });
        });
    }

    private void setData(List<VillageNpcInfo> list, int total) {
        this.rows = list;
        this.total = total;
        if (client != null) clearAndInit(); // dựng lại nút theo danh sách mới
    }

    // ===== Bố cục =====

    private List<VillageNpcInfo> visibleList() {
        if (!problemsOnly) return rows;
        List<VillageNpcInfo> out = new ArrayList<>();
        for (VillageNpcInfo r : rows) if (r.status().isProblem()) out.add(r);
        return out;
    }

    private int visibleRows() {
        return Math.max(1, (bottom - top - HEADER_H - MARGIN) / ROW_H);
    }

    private int listTop() {
        return top + HEADER_H;
    }

    @Override
    protected void init() {
        int panelW = Math.min(PANEL_W, width - 16);
        left = (width - panelW) / 2;
        top = 12;
        bottom = height - 12;

        List<VillageNpcInfo> list = visibleList();
        scroll = MathHelper.clamp(scroll, 0, Math.max(0, list.size() - visibleRows()));

        int right = left + panelW;
        addDrawableChild(ButtonWidget.builder(Text.literal("Làm mới"), b -> requestData(false))
                .dimensions(right - MARGIN - 60, top + 6, 60, 16).build());
        addDrawableChild(ButtonWidget.builder(
                        Text.literal(problemsOnly ? "Chỉ sự cố: Bật" : "Chỉ sự cố: Tắt"),
                        b -> {
                            problemsOnly = !problemsOnly;
                            scroll = 0;
                            clearAndInit();
                        })
                .dimensions(right - MARGIN - 60 - 4 - 96, top + 6, 96, 16).build());

        int end = Math.min(list.size(), scroll + visibleRows());
        for (int i = scroll; i < end; i++) {
            VillageNpcInfo info = list.get(i);
            int y = listTop() + (i - scroll) * ROW_H;
            addDrawableChild(ButtonWidget.builder(Text.literal("Đánh dấu"), b -> mark(info))
                    .dimensions(right - MARGIN - MARK_W, y + 4, MARK_W, 18).build());
        }
    }

    private void mark(VillageNpcInfo info) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(info.uuid());
        ClientPlayNetworking.send(VillageOverview.HIGHLIGHT, buf);
        close(); // đóng sổ để người chơi nhìn thấy NPC đang phát sáng
    }

    // ===== Vẽ =====

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        int panelW = Math.min(PANEL_W, width - 16);
        int right = left + panelW;
        ctx.fill(left, top, right, bottom, 0xE0101010);
        ctx.drawBorder(left, top, panelW, bottom - top, 0xFF8B6B3E);

        ctx.drawTextWithShadow(textRenderer, title, left + MARGIN, top + 10, 0xFFE0B060);
        drawSummary(ctx, left + MARGIN, top + 30);
        ctx.fill(left + MARGIN, listTop() - 4, right - MARGIN, listTop() - 3, 0x60FFFFFF);

        List<VillageNpcInfo> list = visibleList();
        if (list.isEmpty()) {
            String msg = rows.isEmpty()
                    ? "Không có NPC nào của bạn trong bán kính " + VillageOverview.RANGE + " block."
                    : "Không có NPC nào gặp sự cố.";
            ctx.drawCenteredTextWithShadow(textRenderer, msg, (left + right) / 2, listTop() + 20, 0xAAAAAA);
        } else {
            int end = Math.min(list.size(), scroll + visibleRows());
            for (int i = scroll; i < end; i++) {
                drawRow(ctx, list.get(i), listTop() + (i - scroll) * ROW_H, right, i);
            }
            if (list.size() > visibleRows()) {
                String page = (scroll + 1) + "-" + end + " / " + list.size() + "  (lăn chuột để cuộn)";
                ctx.drawTextWithShadow(textRenderer, page, left + MARGIN, bottom - 10, 0x808080);
            }
        }
        if (total > rows.size()) {
            String more = "Chỉ hiện " + rows.size() + "/" + total + " NPC gần nhất";
            ctx.drawTextWithShadow(textRenderer, more, right - MARGIN - textRenderer.getWidth(more), bottom - 10, 0xFFAA00);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawSummary(DrawContext ctx, int x, int y) {
        Map<VillageNpcStatus, Integer> counts = new EnumMap<>(VillageNpcStatus.class);
        for (VillageNpcInfo r : rows) counts.merge(r.status(), 1, Integer::sum);
        String head = "Tổng " + rows.size() + " NPC";
        ctx.drawTextWithShadow(textRenderer, head, x, y, 0xFFFFFF);
        x += textRenderer.getWidth(head) + 8;
        for (VillageNpcStatus s : VillageNpcStatus.values()) {
            Integer n = counts.get(s);
            if (n == null) continue;
            String part = s.label() + ": " + n;
            ctx.drawTextWithShadow(textRenderer, part, x, y, s.color());
            x += textRenderer.getWidth(part) + 8;
        }
    }

    private void drawRow(DrawContext ctx, VillageNpcInfo info, int y, int right, int index) {
        int x = left + MARGIN;
        int rowRight = right - MARGIN;
        if (index % 2 == 0) ctx.fill(x, y, rowRight, y + ROW_H - 1, 0x20FFFFFF);
        ctx.fill(x, y, x + 3, y + ROW_H - 1, 0xFF000000 | info.status().color());

        int textX = x + 7;
        int textRight = rowRight - MARK_W - 6;
        // Dòng 1: tên · trạng thái · máu/đói · khoảng cách
        ctx.drawTextWithShadow(textRenderer, info.name(), textX, y + 3, 0xFFFFFF);
        int sx = textX + textRenderer.getWidth(info.name()) + 6;
        ctx.drawTextWithShadow(textRenderer, "[" + info.status().label() + "]", sx, y + 3, info.status().color());

        String stats = "❤" + Math.round(info.health()) + "/" + Math.round(info.maxHealth())
                + "  🍖" + info.hunger() + "  " + distanceText(info.pos());
        ctx.drawTextWithShadow(textRenderer, stats, textRight - textRenderer.getWidth(stats), y + 3, 0xC0C0C0);

        // Dòng 2: chi tiết + toạ độ
        BlockPos p = info.pos();
        String coords = p.getX() + " " + p.getY() + " " + p.getZ();
        int coordW = textRenderer.getWidth(coords);
        String detail = textRenderer.trimToWidth(info.detail(), Math.max(0, textRight - textX - coordW - 8));
        ctx.drawTextWithShadow(textRenderer, detail, textX, y + 14, info.status().isProblem() ? info.status().color() : 0x909090);
        ctx.drawTextWithShadow(textRenderer, coords, textRight - coordW, y + 14, 0x707070);
    }

    private String distanceText(BlockPos pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return "";
        double d = Math.sqrt(mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
        return Math.round(d) + "m";
    }

    // ===== Tương tác =====

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int max = Math.max(0, visibleList().size() - visibleRows());
        int next = MathHelper.clamp(scroll - (int) Math.signum(amount), 0, max);
        if (next != scroll) {
            scroll = next;
            clearAndInit();
        }
        return true;
    }

    @Override
    public void tick() {
        if (++refreshTicks >= REFRESH_TICKS) {
            refreshTicks = 0;
            requestData(false);
        }
    }

    @Override
    public boolean shouldPause() {
        return false; // trạng thái NPC phải tiếp tục chạy để làm mới
    }
}
