package com.example.block;

import com.example.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * BlockEntity cho Rương Thợ mỏ: giữ cấu hình vùng đào ({@link QuarryConfig}) và con trỏ tiến độ
 * (lưu NBT, nạp lại thế giới vẫn đào tiếp chỗ cũ).
 */
public class MinerChestBlockEntity extends ProfessionChestBlockEntity {
    /** Số ô CÓ BLOCK (không phải không khí) tối đa xét trong một lần tìm; ô không khí rẻ nên không tính. */
    private static final int SCAN_BUDGET = 2048;
    /** Đào hết vùng → chờ bấy nhiêu tick mới quét lại từ đầu (block rơi xuống, người chơi đặt thêm...). */
    private static final int RESCAN_TICKS = 200;

    private QuarryConfig quarry = QuarryConfig.DEFAULT;
    /** Đã có hướng đào (đặt từ hướng nhìn của người đặt rương). Rương cũ chưa có thì lấy hướng người mở cấu hình. */
    private boolean directionSet;
    /** Người chơi đã lưu cấu hình ít nhất một lần — chưa cấu hình thì Thợ mỏ KHÔNG đào (tránh đào nhầm nhà). */
    private boolean configured;
    /** Tăng mỗi lần đổi cấu hình: goal đang đào ô cũ biết mà bỏ. Không lưu NBT. */
    private int revision;
    private int cursor;
    /** Thời điểm được quét lại từ đầu sau khi đã đi hết vùng; -1 = chưa đi hết. */
    private long rescanAt = -1;
    /** Lần tìm vừa rồi dừng vì hết ngân sách (chưa đi hết vùng) → nên tìm tiếp ngay. */
    private boolean scanPending;

    public MinerChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MINER_CHEST_BE, pos, state);
    }

    public QuarryConfig getQuarry() {
        return quarry;
    }

    public boolean hasDirection() {
        return directionSet;
    }

    public void setQuarry(QuarryConfig config) {
        quarry = config.clamp();
        directionSet = true;
        configured = true;
        revision++;
        cursor = 0;
        rescanAt = -1;
        markDirty();
        syncToClients();
    }

    /** Gửi cấu hình vùng xuống client để vẽ khung khi người chơi nhìn vào rương. */
    private void syncToClients() {
        if (world != null && !world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    /** Chỉ gửi cấu hình vùng đào (không gửi đồ trong rương) cho client. */
    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound nbt = new NbtCompound();
        if (configured) {
            nbt.put("Quarry", quarry.toNbt());
            nbt.putBoolean("QuarryConfigured", true);
        }
        return nbt;
    }

    @Nullable
    @Override
    public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> toUpdatePacket() {
        return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this);
    }

    public boolean isConfigured() {
        return configured;
    }

    public int getRevision() {
        return revision;
    }

    public boolean isScanPending() {
        return scanPending;
    }

    /** Gán hướng đào nếu rương chưa có (lúc đặt rương / rương cũ lần đầu mở cấu hình). */
    public void ensureDirection(Direction facing) {
        if (directionSet || !facing.getAxis().isHorizontal()) return;
        quarry = quarry.withDirection(facing);
        directionSet = true;
        markDirty();
        syncToClients();
    }

    /**
     * Ô kế tiếp đào được trong vùng, bắt đầu từ con trỏ; null nếu chưa thấy (hết vùng → chờ RESCAN_TICKS,
     * hoặc hết ngân sách quét lần này → lần sau quét tiếp). Không tiến con trỏ qua ô trả về: gọi {@link #advance()}
     * sau khi đào xong.
     */
    @Nullable
    public BlockPos nextTarget(World world, Predicate<BlockPos> canMine) {
        scanPending = false;
        if (!configured) return null;
        int volume = quarry.volume();
        int solid = 0;
        for (int visited = 0; visited <= volume; visited++) {
            if (cursor >= volume) {
                // Đi hết vùng: chờ RESCAN_TICKS kể từ lúc hết rồi mới quét lại từ đầu
                long now = world.getTime();
                if (rescanAt < 0) rescanAt = now + RESCAN_TICKS;
                if (now < rescanAt) return null;
                cursor = 0;
                rescanAt = -1;
                markDirty();
            }
            BlockPos target = quarry.positionAt(pos, cursor);
            if (world.isChunkLoaded(target) && !world.getBlockState(target).isAir()) {
                if (canMine.test(target)) return target;
                if (++solid >= SCAN_BUDGET) {
                    cursor++;
                    scanPending = true;
                    return null;
                }
            }
            cursor++;
        }
        return null;
    }

    /** Ô hiện tại đã đào xong (hoặc không đào được) → sang ô kế. */
    public void advance() {
        cursor++;
        markDirty();
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains("Quarry")) {
            quarry = QuarryConfig.fromNbt(nbt.getCompound("Quarry"));
            directionSet = true;
        }
        configured = nbt.getBoolean("QuarryConfigured");
        cursor = Math.max(0, nbt.getInt("QuarryCursor"));
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (directionSet) nbt.put("Quarry", quarry.toNbt());
        if (configured) nbt.putBoolean("QuarryConfigured", true);
        nbt.putInt("QuarryCursor", cursor);
    }
}
