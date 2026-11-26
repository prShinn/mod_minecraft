package com.example.block;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

/**
 * Vùng đào của Rương Thợ mỏ: hộp Dài × Rộng × Cao nằm phía trước rương theo hướng người chơi
 * nhìn khi đặt rương ({@link #direction}).
 * <ul>
 *     <li>Dài: tính từ ô ngay trước rương, kéo xa dần theo hướng.</li>
 *     <li>Rộng: lấy rương làm giữa (lẻ thì đều hai bên, chẵn thì lệch phải 1 ô).</li>
 *     <li>Cao (sâu): từ tầng ngay dưới rương (rương − 1) đào xuống {@code height} tầng.</li>
 * </ul>
 * Thứ tự đào: tầng trên cùng xuống dưới; trong một tầng, hàng gần rương trước.
 */
public record QuarryConfig(int length, int width, int height, Direction direction) {
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 32;
    public static final int MAX_DEPTH = 64;
    public static final QuarryConfig DEFAULT = new QuarryConfig(8, 5, 4, Direction.NORTH);

    /** Giới hạn mọi giá trị về khoảng hợp lệ (dữ liệu từ client / NBT không tin được). */
    public QuarryConfig clamp() {
        Direction dir = direction != null && direction.getAxis().isHorizontal() ? direction : Direction.NORTH;
        return new QuarryConfig(
                MathHelper.clamp(length, MIN_SIZE, MAX_SIZE),
                MathHelper.clamp(width, MIN_SIZE, MAX_SIZE),
                MathHelper.clamp(height, MIN_SIZE, MAX_DEPTH),
                dir);
    }

    public QuarryConfig withDirection(Direction dir) {
        return new QuarryConfig(length, width, height, dir).clamp();
    }

    public int volume() {
        return length * width * height;
    }

    /** Ô thứ {@code index} (0 ≤ index < volume) theo thứ tự đào. */
    public BlockPos positionAt(BlockPos chest, int index) {
        int perLayer = length * width;
        int layer = index / perLayer;
        int inLayer = index % perLayer;
        int row = inLayer / width;   // khoảng cách theo hướng đào, gần rương trước
        int col = inLayer % width;   // ngang
        Direction right = direction.rotateYClockwise();
        int across = col - (width - 1) / 2;
        return new BlockPos(
                chest.getX() + direction.getOffsetX() * (row + 1) + right.getOffsetX() * across,
                chest.getY() - 1 - layer,
                chest.getZ() + direction.getOffsetZ() * (row + 1) + right.getOffsetZ() * across);
    }

    /** Hộp bao toàn vùng (để vẽ khung, kiểm tra nằm trong vùng). */
    public BlockBox box(BlockPos chest) {
        BlockPos first = positionAt(chest, 0);
        BlockPos last = positionAt(chest, volume() - 1);
        BlockPos farCorner = positionAt(chest, length * width - 1);
        BlockPos nearOther = positionAt(chest, width - 1);
        return BlockBox.encompassPositions(java.util.List.of(first, last, farCorner, nearOther)).orElseThrow();
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("Length", length);
        nbt.putInt("Width", width);
        nbt.putInt("Height", height);
        nbt.putInt("Direction", direction.getHorizontal());
        return nbt;
    }

    public static QuarryConfig fromNbt(NbtCompound nbt) {
        return new QuarryConfig(nbt.getInt("Length"), nbt.getInt("Width"), nbt.getInt("Height"),
                Direction.fromHorizontal(nbt.getInt("Direction"))).clamp();
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(length);
        buf.writeVarInt(width);
        buf.writeVarInt(height);
        buf.writeByte(direction.getHorizontal());
    }

    public static QuarryConfig read(PacketByteBuf buf) {
        return new QuarryConfig(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                Direction.fromHorizontal(buf.readByte())).clamp();
    }
}
