package com.example.village;

import com.example.ExampleMod;
import com.example.entity.SoldierNPCEntity;
import com.example.entity.base.WorkerNpcEntity;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Sổ quản lý làng — phía server.
 * Gom NPC (nghề + Binh lính) của người chơi trong bán kính, phân loại trạng thái, gửi về client;
 * nhận yêu cầu đánh dấu (phát sáng) một NPC.
 */
public final class VillageOverview {
    /** C2S: boolean open — true = mở màn hình khi nhận dữ liệu, false = chỉ làm mới. */
    public static final Identifier REQUEST = new Identifier(ExampleMod.MODID, "village_overview_request");
    /** S2C: boolean open, varint tổng NPC, varint số dòng, các dòng VillageNpcInfo. */
    public static final Identifier DATA = new Identifier(ExampleMod.MODID, "village_overview_data");
    /** C2S: UUID NPC cần đánh dấu. */
    public static final Identifier HIGHLIGHT = new Identifier(ExampleMod.MODID, "village_highlight");

    public static final int RANGE = 128;
    public static final int MAX_ENTRIES = 64;
    private static final int VERTICAL_RANGE = 64;
    /** Sự cố còn được tính trong 15s (NPC chờ 10s giữa các lần thử lại rương đầy). */
    private static final int ISSUE_WINDOW_TICKS = 300;
    private static final int REQUEST_COOLDOWN_TICKS = 10;
    private static final int GLOW_TICKS = 200;
    private static final int HUNGRY_THRESHOLD = 4;

    /** Chỉ truy cập trên server thread. */
    private static final Map<UUID, Long> lastRequest = new HashMap<>();

    private VillageOverview() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST, (server, player, handler, buf, sender) -> {
            if (buf.readableBytes() != 1) return;
            boolean open = buf.readBoolean();
            server.execute(() -> sendTo(player, open));
        });
        ServerPlayNetworking.registerGlobalReceiver(HIGHLIGHT, (server, player, handler, buf, sender) -> {
            if (buf.readableBytes() != 16) return;
            UUID target = buf.readUuid();
            server.execute(() -> highlight(player, target));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> lastRequest.remove(handler.player.getUuid()));
    }

    /** Gửi danh sách NPC cho người chơi; bỏ qua nếu vừa gửi trong 0.5s (chống spam). */
    public static void sendTo(ServerPlayerEntity player, boolean open) {
        long now = player.getServerWorld().getTime();
        Long last = lastRequest.get(player.getUuid());
        if (last != null && now >= last && now - last < REQUEST_COOLDOWN_TICKS) return;
        lastRequest.put(player.getUuid(), now);

        List<PathAwareEntity> npcs = ownedNpcs(player);
        List<VillageNpcInfo> rows = new ArrayList<>(npcs.size());
        for (PathAwareEntity npc : npcs) rows.add(describe(npc));
        BlockPos center = player.getBlockPos();
        rows.sort(Comparator.comparingInt((VillageNpcInfo r) -> r.status().ordinal())
                .thenComparingDouble(r -> r.pos().getSquaredDistance(center)));

        int count = Math.min(rows.size(), MAX_ENTRIES);
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBoolean(open);
        out.writeVarInt(rows.size());
        out.writeVarInt(count);
        for (int i = 0; i < count; i++) rows.get(i).write(out);
        ServerPlayNetworking.send(player, DATA, out);
    }

    private static List<PathAwareEntity> ownedNpcs(ServerPlayerEntity player) {
        UUID me = player.getUuid();
        return player.getServerWorld().getEntitiesByClass(PathAwareEntity.class,
                player.getBoundingBox().expand(RANGE, VERTICAL_RANGE, RANGE),
                e -> e.isAlive() && me.equals(ownerOf(e)));
    }

    @Nullable
    private static UUID ownerOf(Entity e) {
        if (e instanceof WorkerNpcEntity worker) return worker.getOwnerUUID();
        if (e instanceof SoldierNPCEntity soldier) return soldier.getOwnerUuid();
        return null;
    }

    private static VillageNpcInfo describe(PathAwareEntity npc) {
        String name;
        String issue = null;
        String label;
        int hunger;
        int food;
        if (npc instanceof WorkerNpcEntity worker) {
            name = worker.professionName();
            issue = worker.display.recentIssue(npc.getWorld(), ISSUE_WINDOW_TICKS);
            label = worker.overviewLabel(issue);
            hunger = worker.display.getHunger();
            food = worker.suppliesFoodCount();
        } else {
            SoldierNPCEntity soldier = (SoldierNPCEntity) npc;
            String role = soldier.getNameNpc();
            name = role == null || role.isEmpty() ? "Binh lính" : role;
            label = soldier.overviewLabel();
            hunger = soldier.getHunger();
            food = soldier.getTotalFoodCount();
        }

        VillageNpcStatus status = classify(npc, issue, label);
        String detail = label;
        if (status == VillageNpcStatus.WORKING || status == VillageNpcStatus.IDLE) {
            if (hunger <= HUNGRY_THRESHOLD && food == 0) {
                status = VillageNpcStatus.MISSING;
                detail = "Sắp đói và hết đồ ăn — cho NPC ăn hoặc bổ sung rương thức ăn";
            }
        }
        if (npc instanceof WorkerNpcEntity worker && status != VillageNpcStatus.MISSING) {
            detail = detail + " · " + chestLabel(worker);
        }
        return new VillageNpcInfo(npc.getUuid(), name, status, detail,
                npc.getHealth(), npc.getMaxHealth(), hunger, food, npc.getBlockPos());
    }

    /** Rương neo của NPC nghề: tọa độ, hoặc báo đang dùng chung / chưa có. */
    private static String chestLabel(WorkerNpcEntity worker) {
        BlockPos chest = worker.getChestPos();
        if (chest == null) return "Chưa có rương";
        String pos = chest.getX() + " " + chest.getY() + " " + chest.getZ();
        return worker.isSharingChest() ? "Dùng chung rương " + pos + " (đặt thêm rương)" : "Rương " + pos;
    }

    /** Phân loại theo cùng quy tắc với nhãn trên đầu NPC: sự cố chỉ tính khi không có việc nào đang chạy. */
    static VillageNpcStatus classify(PathAwareEntity npc, @Nullable String issue, String label) {
        if (npc.getPose() == EntityPose.SLEEPING) return VillageNpcStatus.SLEEPING;
        if (npc.getTarget() != null) return VillageNpcStatus.COMBAT;
        if (issue != null && label.equals(issue)) return classifyIssue(issue);
        if (label.startsWith("Thiếu") || label.equals("Đang chờ vật liệu")) return VillageNpcStatus.MISSING; // Thợ xây
        if (label.equals("Đang chờ việc")) return VillageNpcStatus.IDLE;
        return VillageNpcStatus.WORKING;
    }

    static VillageNpcStatus classifyIssue(String issue) {
        String s = issue.toLowerCase(Locale.ROOT);
        if (s.contains("đầy")) return VillageNpcStatus.CHEST_FULL;
        if (s.contains("thiếu") || s.contains("rương thức ăn")) return VillageNpcStatus.MISSING;
        if (s.contains("đường") || s.contains("không tới được")) return VillageNpcStatus.STUCK;
        return VillageNpcStatus.ATTENTION;
    }

    private static void highlight(ServerPlayerEntity player, UUID target) {
        ServerWorld world = player.getServerWorld();
        Entity e = world.getEntity(target);
        if (!(e instanceof PathAwareEntity npc) || !npc.isAlive() || !player.getUuid().equals(ownerOf(npc))
                || Math.abs(npc.getX() - player.getX()) > RANGE || Math.abs(npc.getZ() - player.getZ()) > RANGE) {
            player.sendMessage(Text.literal("NPC không còn trong phạm vi Sổ quản lý làng.").formatted(Formatting.RED), true);
            return;
        }
        npc.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, GLOW_TICKS, 0, false, false));
        BlockPos p = npc.getBlockPos();
        int dist = (int) Math.round(Math.sqrt(npc.squaredDistanceTo(player)));
        String name = npc instanceof WorkerNpcEntity w ? w.professionName() : "Binh lính";
        String chest = npc instanceof WorkerNpcEntity w ? " · " + chestLabel(w) : "";
        player.sendMessage(Text.literal("Đã đánh dấu " + name + " tại " + p.getX() + " " + p.getY() + " " + p.getZ()
                + " (cách " + dist + " block)" + chest).formatted(Formatting.GOLD), true);
    }
}
