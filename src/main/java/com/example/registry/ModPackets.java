package com.example.registry;

import com.example.entity.SoldierNPCEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.UUID;

public class ModPackets {
    public static final Identifier NPC_EQUIP =
            new Identifier("soldier_npc", "npc_equip");

    public static final Identifier NPC_MOVE_MODE =
            new Identifier("soldier_npc", "npc_move_mode");

    public static void registerServer() {
        registerEquip();
        registerMoveMode();
    }
    private static void registerEquip() {
        ServerPlayNetworking.registerGlobalReceiver(
                NPC_EQUIP,
                (server, player, handler, buf, sender) -> {

                    UUID npcId = buf.readUuid();
                    String slot = buf.readString();

                    server.execute(() -> {
                        Entity e = player.getWorld().getEntity(npcId);
                        if (!(e instanceof SoldierNPCEntity npc)) return;
                        if (!npc.isOwner(player)) return;

                        ItemStack held = player.getMainHandStack();
                        if (held.isEmpty()) return;

                        switch (slot) {
                            case "main" -> npc.equipStack(EquipmentSlot.MAINHAND, held.copy());
                            case "off" -> npc.equipStack(EquipmentSlot.OFFHAND, held.copy());
                            case "head" -> npc.equipStack(EquipmentSlot.HEAD, held.copy());
                            case "chest" -> npc.equipStack(EquipmentSlot.CHEST, held.copy());
                            case "legs" -> npc.equipStack(EquipmentSlot.LEGS, held.copy());
                            case "feet" -> npc.equipStack(EquipmentSlot.FEET, held.copy());
                        }
                    });
                }
        );
    }

    private static void registerMoveMode() {
        ServerPlayNetworking.registerGlobalReceiver(
                NPC_MOVE_MODE,
                (server, player, handler, buf, sender) -> {

                    UUID npcId = buf.readUuid();

                    server.execute(() -> {
                        Entity e = player.getWorld().getEntity(npcId);
                        if (!(e instanceof SoldierNPCEntity npc)) return;
                        if (!npc.isOwner(player)) return;

                        npc.toggleMoveMode();
                    });
                }
        );
    }
}
