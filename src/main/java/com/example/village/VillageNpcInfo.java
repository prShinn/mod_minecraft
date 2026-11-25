package com.example.village;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/** Một dòng trong Sổ quản lý làng (server → client). */
public record VillageNpcInfo(UUID uuid, String name, VillageNpcStatus status, String detail,
                             float health, float maxHealth, int hunger, int food, BlockPos pos) {
    public static final int MAX_TEXT = 96;

    public void write(PacketByteBuf buf) {
        buf.writeUuid(uuid);
        buf.writeString(clip(name), MAX_TEXT);
        buf.writeByte(status.ordinal());
        buf.writeString(clip(detail), MAX_TEXT);
        buf.writeFloat(health);
        buf.writeFloat(maxHealth);
        buf.writeVarInt(hunger);
        buf.writeVarInt(food);
        buf.writeBlockPos(pos);
    }

    public static VillageNpcInfo read(PacketByteBuf buf) {
        return new VillageNpcInfo(buf.readUuid(), buf.readString(MAX_TEXT), VillageNpcStatus.byId(buf.readByte()),
                buf.readString(MAX_TEXT), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
                buf.readBlockPos());
    }

    private static String clip(String s) {
        if (s == null) return "";
        return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
    }
}
