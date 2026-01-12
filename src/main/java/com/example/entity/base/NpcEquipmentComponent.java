package com.example.entity.base;

import com.example.registry.ModItems;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import java.util.UUID;

public class NpcEquipmentComponent {

    public boolean tryEquip(PathAwareEntity npc, PlayerEntity player, ItemStack stack) {

        if (stack.getItem() instanceof ArmorItem armor) {
            EquipmentSlot slot = armor.getSlotType();
            npc.equipStack(slot, stack.copyWithCount(1));
            if (!player.isCreative()) stack.decrement(1);
            return true;
        }

        if (stack.getItem() instanceof ToolItem || stack.getItem() instanceof SwordItem) {
            npc.equipStack(EquipmentSlot.MAINHAND, stack.copyWithCount(1));
            if (!player.isCreative()) stack.decrement(1);
            return true;
        }
        return false;
    }

    // ===== INTERACTION =====
    public ActionResult interactMob(PathAwareEntity npc, PlayerEntity player, UUID ownerUUID, Hand hand, SimpleInventory foodInventory, Item npc_token) {
        if (npc.getWorld().isClient) return ActionResult.SUCCESS;
        // ===== SHIFT + RIGHT CLICK → THU HỒI NPC =====
        ActionResult recallResult = tryRecallNpc(npc, player, ownerUUID, npc_token);
        if (recallResult != null) return recallResult;
        ItemStack held = player.getStackInHand(hand);
        // ===== ADD FOOD =====
        if (held.isFood()) {
            boolean added = addFoodToInventory(held, foodInventory);
            if (added) {
                npc.getWorld().playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                        SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 0.5F, 1.0F);
                if (!player.isCreative()) {
                    held.decrement(1);
                }
                return ActionResult.CONSUME;
            }
        }
        ActionResult handleItems = tryHandleItems(npc, player, held);
        if (handleItems != null) return handleItems;

        return ActionResult.FAIL;
    }

    private ActionResult tryRecallNpc(PathAwareEntity npc, PlayerEntity player, UUID ownerUUID, Item npc_token) {
        if (player.isSneaking()) {
            // chỉ owner được thu hồi
            if (ownerUUID != null && !player.getUuid().equals(ownerUUID)) {
                return ActionResult.FAIL;
            }
            ItemStack token = new ItemStack(npc_token);

            NbtCompound entityNbt = new NbtCompound();
            this.writeCustomDataToNbt(entityNbt, npc);
            entityNbt.putFloat("Health", npc.getHealth());

            token.getOrCreateNbt().put("EntityTag", entityNbt);

            if (!player.getInventory().insertStack(token)) {
                npc.dropStack(token);
            }

            npc.discard(); // xoá NPC

            return ActionResult.CONSUME;
        }
        return null;
    }

    private ActionResult tryHandleItems(PathAwareEntity npc, PlayerEntity player, ItemStack held) {
        // Equip weapon
        if (!held.isEmpty()) {
            ItemStack oldWeapon = npc.getMainHandStack();
            if (!oldWeapon.isEmpty()) {
                npc.dropStack(oldWeapon);
            }
            npc.equipStack(EquipmentSlot.MAINHAND, held.copyWithCount(1));
            if (!player.isCreative()) held.decrement(1);
            return ActionResult.CONSUME;
        }
        // Equip armor
        if (held.getItem() instanceof ArmorItem armor) {
            EquipmentSlot slot = armor.getSlotType();
            ItemStack equipped = npc.getEquippedStack(slot);

            if (equipped.isEmpty()) {
                npc.equipStack(slot, held.copyWithCount(1));
                // Lấy âm thanh mặc giáp chuẩn theo loại giáp (da, sắt, kim cương...)
                npc.getWorld().playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                        armor.getEquipSound(), SoundCategory.NEUTRAL, 1.0F, 1.0F);
                if (!player.isCreative()) held.decrement(1);
                return ActionResult.CONSUME;
            }
        }
        // Remove armor (empty hand)
        if (held.isEmpty()) {

            ItemStack equippedHand = npc.getEquippedStack(EquipmentSlot.MAINHAND);
            if (!equippedHand.isEmpty()) {
                npc.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                npc.dropStack(equippedHand);
                return ActionResult.CONSUME;
            }

            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (!slot.isArmorSlot()) continue;

                ItemStack equipped = npc.getEquippedStack(slot);
                if (!equipped.isEmpty()) {
                    npc.equipStack(slot, ItemStack.EMPTY);
                    npc.dropStack(equipped);
                    return ActionResult.CONSUME;
                }
            }
        }
        return null;
    }

    private boolean addFoodToInventory(ItemStack foodStack, SimpleInventory foodInventory) {
        if (!foodStack.isFood()) return false;

        ItemStack oneFood = foodStack.copyWithCount(1);

        for (int i = 0; i < foodInventory.size(); i++) {
            ItemStack slot = foodInventory.getStack(i);

            if (slot.isEmpty()) {
                foodInventory.setStack(i, oneFood);
                return true;
            }

            if (ItemStack.canCombine(slot, oneFood) && slot.getCount() < slot.getMaxCount()) {
                slot.increment(1);
                return true;
            }
        }
        return false; // full
    }

    // ===== NBT SAVE/LOAD =====
    public void writeCustomDataToNbt(NbtCompound nbt, PathAwareEntity npc) {
        // Equipment
        saveEquipment(nbt, npc);
    }

    private void saveEquipment(NbtCompound nbt, PathAwareEntity npc) {
        // Weapon
        ItemStack weapon = npc.getMainHandStack();
        if (!weapon.isEmpty()) {
            nbt.put("Weapon", weapon.writeNbt(new NbtCompound()));
        }

        // Armor
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = npc.getEquippedStack(slot);
            if (!stack.isEmpty()) {
                nbt.put(slot.getName(), stack.writeNbt(new NbtCompound()));
            }
        }
    }
}
