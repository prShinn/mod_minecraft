package com.example.block;

import com.example.ExampleMod;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Gói tin cấu hình vùng đào của Rương Thợ mỏ. */
public final class QuarryNetworking {
    /** S2C: BlockPos rương + QuarryConfig → mở màn hình cấu hình. */
    public static final Identifier OPEN = new Identifier(ExampleMod.MODID, "quarry_open");
    /** C2S: BlockPos rương + QuarryConfig → lưu cấu hình. */
    public static final Identifier SAVE = new Identifier(ExampleMod.MODID, "quarry_save");
    /** S2C: BlockPos rương + QuarryConfig → hiện khung vùng đào một lúc sau khi lưu. */
    public static final Identifier PREVIEW = new Identifier(ExampleMod.MODID, "quarry_preview");

    private static final double MAX_DISTANCE_SQ = 8.0 * 8.0;

    private QuarryNetworking() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(SAVE, (server, player, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            QuarryConfig config = QuarryConfig.read(buf); // đã clamp
            server.execute(() -> save(player, pos, config));
        });
    }

    public static void sendOpen(ServerPlayerEntity player, MinerChestBlockEntity chest) {
        chest.ensureDirection(player.getHorizontalFacing());
        ServerPlayNetworking.send(player, OPEN, encode(chest.getPos(), chest.getQuarry()));
    }

    private static void save(ServerPlayerEntity player, BlockPos pos, QuarryConfig config) {
        if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_DISTANCE_SQ
                || !player.getServerWorld().isChunkLoaded(pos)
                || !(player.getServerWorld().getBlockEntity(pos) instanceof MinerChestBlockEntity chest)
                || !chest.canOpen(player)) {
            return;
        }
        chest.setQuarry(config);
        QuarryConfig saved = chest.getQuarry();
        player.sendMessage(Text.literal("Đã lưu vùng đào dài " + saved.length() + " × rộng " + saved.width()
                + " × sâu " + saved.height() + " (từ dưới rương xuống)").formatted(Formatting.GOLD), true);
        ServerPlayNetworking.send(player, PREVIEW, encode(pos, saved));
    }

    private static PacketByteBuf encode(BlockPos pos, QuarryConfig config) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        config.write(buf);
        return buf;
    }
}
