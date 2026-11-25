package com.example.screen;

import com.example.entity.SoldierNPCEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

/**
 * View trực tiếp lên trang bị của Binh lính (không sao chép): đọc/ghi thẳng vào equipment của entity,
 * nên không thể lệch hay nhân đôi đồ giữa GUI và NPC. Chỉ dùng phía server.
 */
public class SoldierEquipmentInventory implements Inventory {
    /** Thứ tự ô trong GUI: mũ, áo, quần, giày, vũ khí. */
    public static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND
    };

    private final SoldierNPCEntity npc;

    public SoldierEquipmentInventory(SoldierNPCEntity npc) {
        this.npc = npc;
    }

    @Override
    public int size() {
        return SLOTS.length;
    }

    @Override
    public boolean isEmpty() {
        for (EquipmentSlot slot : SLOTS) {
            if (!npc.getEquippedStack(slot).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getStack(int index) {
        return npc.getEquippedStack(SLOTS[index]);
    }

    @Override
    public ItemStack removeStack(int index, int amount) {
        ItemStack equipped = getStack(index);
        if (equipped.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        ItemStack taken = equipped.split(amount);
        npc.equipStack(SLOTS[index], equipped.isEmpty() ? ItemStack.EMPTY : equipped);
        return taken;
    }

    @Override
    public ItemStack removeStack(int index) {
        ItemStack equipped = getStack(index);
        npc.equipStack(SLOTS[index], ItemStack.EMPTY);
        return equipped;
    }

    @Override
    public void setStack(int index, ItemStack stack) {
        npc.equipStack(SLOTS[index], stack);
    }

    @Override
    public void markDirty() {
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return SoldierScreenHandler.canManage(npc, player);
    }

    @Override
    public void clear() {
        for (EquipmentSlot slot : SLOTS) npc.equipStack(slot, ItemStack.EMPTY);
    }
}
