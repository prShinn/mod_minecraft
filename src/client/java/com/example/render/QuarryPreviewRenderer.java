package com.example.render;

import com.example.block.QuarryConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Vẽ khung vùng đào của Rương Thợ mỏ (nhìn xuyên block): khi đang mở màn hình cấu hình
 * và 15 giây sau khi lưu. Vàng = vùng đào, xanh = ô rương.
 */
public final class QuarryPreviewRenderer {
    private static final int SHOW_AFTER_SAVE_TICKS = 300;

    @Nullable
    private static BlockPos chest;
    @Nullable
    private static QuarryConfig config;
    private static boolean editing;
    private static long hideAt;
    /** Thế giới lúc bật khung: đổi thế giới / server thì bỏ khung cũ. */
    @Nullable
    private static net.minecraft.client.world.ClientWorld shownIn;

    private QuarryPreviewRenderer() {
    }

    /** Màn hình cấu hình đang mở: vẽ theo giá trị đang chỉnh. */
    public static void showEditing(BlockPos chestPos, QuarryConfig cfg) {
        chest = chestPos;
        config = cfg;
        editing = true;
        shownIn = MinecraftClient.getInstance().world;
    }

    /** Đóng màn hình mà không lưu: tắt khung ngay. */
    public static void cancelEditing() {
        editing = false;
        chest = null;
        config = null;
    }

    /** Sau khi lưu: vẽ thêm một lúc rồi tắt. */
    public static void showSaved(BlockPos chestPos, QuarryConfig cfg) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        editing = false;
        chest = chestPos;
        config = cfg;
        shownIn = client.world;
        hideAt = client.world.getTime() + SHOW_AFTER_SAVE_TICKS;
    }

    public static void register() {
        WorldRenderEvents.LAST.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null) return;
            BlockPos chestPos = chest;
            QuarryConfig cfg = config;
            if (chestPos != null && (client.world != shownIn || !editing && client.world.getTime() > hideAt)) {
                chest = null;
                config = null;
                chestPos = null;
                cfg = null;
            }
            if (chestPos == null) {
                // Không có khung đang chỉnh/vừa lưu: nhìn vào Rương Thợ mỏ đã cấu hình thì hiện vùng đào của nó
                if (!(client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit)
                        || hit.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK
                        || !(client.world.getBlockEntity(hit.getBlockPos()) instanceof com.example.block.MinerChestBlockEntity box)
                        || !box.isConfigured()) {
                    return;
                }
                chestPos = hit.getBlockPos();
                cfg = box.getQuarry();
            }
            Vec3d cam = context.camera().getPos();
            Matrix4f matrix = context.matrixStack().peek().getPositionMatrix();
            BlockBox area = cfg.box(chestPos);

            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            outline(buffer, matrix, cam, area.getMinX(), area.getMinY(), area.getMinZ(),
                    area.getMaxX() + 1, area.getMaxY() + 1, area.getMaxZ() + 1, 1.0F, 0.8F, 0.1F);
            outline(buffer, matrix, cam, chestPos.getX(), chestPos.getY(), chestPos.getZ(),
                    chestPos.getX() + 1, chestPos.getY() + 1, chestPos.getZ() + 1, 0.2F, 0.9F, 1.0F);
            tessellator.draw();
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
        });
    }

    private static void outline(BufferBuilder b, Matrix4f m, Vec3d cam, double x0, double y0, double z0,
                                double x1, double y1, double z1, float r, float g, float bl) {
        float ax = (float) (x0 - cam.x), ay = (float) (y0 - cam.y), az = (float) (z0 - cam.z);
        float bx = (float) (x1 - cam.x), by = (float) (y1 - cam.y), bz = (float) (z1 - cam.z);
        float[][] edges = {
                {ax, ay, az, bx, ay, az}, {ax, ay, bz, bx, ay, bz}, {ax, by, az, bx, by, az}, {ax, by, bz, bx, by, bz},
                {ax, ay, az, ax, by, az}, {bx, ay, az, bx, by, az}, {ax, ay, bz, ax, by, bz}, {bx, ay, bz, bx, by, bz},
                {ax, ay, az, ax, ay, bz}, {bx, ay, az, bx, ay, bz}, {ax, by, az, ax, by, bz}, {bx, by, az, bx, by, bz}};
        for (float[] e : edges) {
            b.vertex(m, e[0], e[1], e[2]).color(r, g, bl, 1.0F).next();
            b.vertex(m, e[3], e[4], e[5]).color(r, g, bl, 1.0F).next();
        }
    }
}
