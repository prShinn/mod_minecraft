package com.example.screen;

import com.example.entity.SoldierNPCEntity;
import com.example.registry.ModScreenHandlers;
import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * GUI Binh lính: 4 ô giáp + 1 ô vũ khí + 9 ô túi đồ ăn + túi đồ người chơi, kèm chỉ số đồng bộ
 * (máu, độ đói, cấp, % kinh nghiệm, chế độ di chuyển).
 *
 * Phía server dùng view trực tiếp lên NPC (SoldierEquipmentInventory, foodInventory) nên GUI và NPC
 * luôn là một nguồn dữ liệu. canUse() (vanilla kiểm cả lúc click) chặn thao tác khi NPC chết/bị thu hồi/ở xa/khác chủ.
 */
public class SoldierScreenHandler extends ScreenHandler {
    public static final int EQUIPMENT_SLOTS = 5;
    public static final int FOOD_SLOTS = 9;
    /** Túi chiến lợi phẩm: chỉ cho lấy ra (khi chưa có Rương binh lính để lính tự cất). */
    public static final int LOOT_SLOTS = com.example.ai.soldier.SoldierLoot.BAG_SIZE;
    private static final int LOOT_START = EQUIPMENT_SLOTS + FOOD_SLOTS;
    private static final int NPC_SLOTS = LOOT_START + LOOT_SLOTS;
    private static final double MAX_DISTANCE_SQ = 8.0 * 8.0;

    // ===== Chỉ số đồng bộ (property được gửi dạng short → giữ giá trị trong [-32768, 32767]) =====
    public static final int PROP_HEALTH_X10 = 0;
    public static final int PROP_MAX_HEALTH_X10 = 1;
    public static final int PROP_HUNGER = 2;
    public static final int PROP_LEVEL = 3;
    public static final int PROP_XP_PERMILLE = 4;
    public static final int PROP_MOVE_MODE = 5;
    private static final int PROP_COUNT = 6;

    /** Vị trí ô trong GUI (dùng chung với SoldierScreen). */
    public static final int ARMOR_X = 8, ARMOR_Y = 18;
    public static final int WEAPON_X = 80, WEAPON_Y = 72;
    public static final int FOOD_X = 8, FOOD_Y = 104;
    public static final int LOOT_X = 8, LOOT_Y = 136;
    public static final int PLAYER_INV_Y = 186;

    private static final Identifier[] EMPTY_ARMOR_SPRITES = {
            PlayerScreenHandler.EMPTY_HELMET_SLOT_TEXTURE, PlayerScreenHandler.EMPTY_CHESTPLATE_SLOT_TEXTURE,
            PlayerScreenHandler.EMPTY_LEGGINGS_SLOT_TEXTURE, PlayerScreenHandler.EMPTY_BOOTS_SLOT_TEXTURE
    };

    /** Icon thanh kiếm mờ của vanilla (dùng cho bàn rèn 1.20) cho ô vũ khí trống. */
    private static final Identifier EMPTY_WEAPON_SPRITE = new Identifier("minecraft", "item/empty_slot_sword");

    @Nullable
    private final SoldierNPCEntity npc;
    private final int npcEntityId;
    private final PropertyDelegate properties;

    /** Phía client: dữ liệu ô và chỉ số do server đồng bộ xuống. */
    public SoldierScreenHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf buf) {
        this(syncId, playerInventory, null, buf.readVarInt(),
                new SimpleInventory(EQUIPMENT_SLOTS), new SimpleInventory(FOOD_SLOTS), new SimpleInventory(LOOT_SLOTS),
                new ArrayPropertyDelegate(PROP_COUNT));
    }

    /** Phía server: view trực tiếp lên NPC. */
    public SoldierScreenHandler(int syncId, PlayerInventory playerInventory, SoldierNPCEntity npc) {
        this(syncId, playerInventory, npc, npc.getId(),
                new SoldierEquipmentInventory(npc), npc.foodInventory, npc.loot.bag, liveStats(npc));
    }

    private SoldierScreenHandler(int syncId, PlayerInventory playerInventory, @Nullable SoldierNPCEntity npc,
                                 int npcEntityId, Inventory equipment, Inventory food, Inventory loot,
                                 PropertyDelegate properties) {
        super(ModScreenHandlers.SOLDIER, syncId);
        checkSize(equipment, EQUIPMENT_SLOTS);
        checkSize(food, FOOD_SLOTS);
        checkSize(loot, LOOT_SLOTS);
        this.npc = npc;
        this.npcEntityId = npcEntityId;
        this.properties = properties;

        for (int i = 0; i < 4; i++) {
            addSlot(new EquipmentSlotView(equipment, i, ARMOR_X, ARMOR_Y + i * 18, SoldierEquipmentInventory.SLOTS[i]));
        }
        addSlot(new EquipmentSlotView(equipment, 4, WEAPON_X, WEAPON_Y, EquipmentSlot.MAINHAND));
        for (int i = 0; i < FOOD_SLOTS; i++) {
            addSlot(new Slot(food, i, FOOD_X + i * 18, FOOD_Y) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    return stack.isFood();
                }
            });
        }
        for (int i = 0; i < LOOT_SLOTS; i++) {
            addSlot(new Slot(loot, i, LOOT_X + (i % 9) * 18, LOOT_Y + (i / 9) * 18) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    return false; // chỉ lấy ra; lính tự nhặt vào
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, PLAYER_INV_Y + 58));
        }
        addProperties(properties);
    }

    private static PropertyDelegate liveStats(SoldierNPCEntity npc) {
        return new PropertyDelegate() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case PROP_HEALTH_X10 -> clampShort(Math.round(npc.getHealth() * 10));
                    case PROP_MAX_HEALTH_X10 -> clampShort(Math.round(npc.getMaxHealth() * 10));
                    case PROP_HUNGER -> npc.getHunger();
                    case PROP_LEVEL -> clampShort(npc.combat.getLevel());
                    case PROP_XP_PERMILLE -> npc.combat.getMaxExperience() <= 0 ? 0
                            : MathHelper.clamp((int) (npc.combat.getExperience() * 1000 / npc.combat.getMaxExperience()), 0, 1000);
                    case PROP_MOVE_MODE -> npc.getMoveMode().ordinal();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // chỉ đọc: chỉ số do NPC quyết định
            }

            @Override
            public int size() {
                return PROP_COUNT;
            }
        };
    }

    private static int clampShort(int value) {
        return MathHelper.clamp(value, 0, Short.MAX_VALUE);
    }

    /** Ai được mở/thao tác GUI: NPC còn sống, trong 8 block, người chơi là chủ (NPC chưa có chủ thì ai cũng được). */
    public static boolean canManage(SoldierNPCEntity npc, PlayerEntity player) {
        return npc.isAlive() && !npc.isRemoved() && npc.getWorld() == player.getWorld()
                && player.squaredDistanceTo(npc) <= MAX_DISTANCE_SQ
                && (npc.getOwnerUuid() == null || npc.getOwnerUuid().equals(player.getUuid()));
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return npc == null || canManage(npc, player); // client không tự quyết, server mới kiểm
    }

    public int getNpcEntityId() {
        return npcEntityId;
    }

    public int getStat(int index) {
        return properties.get(index);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        if (index < NPC_SLOTS) {
            // Từ NPC → túi người chơi
            if (!insertItem(stack, NPC_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!insertItem(stack, 0, EQUIPMENT_SLOTS, false)
                && !insertItem(stack, EQUIPMENT_SLOTS, LOOT_START, false)) {
            // Không bao giờ shift-click vào túi chiến lợi phẩm (chỉ lấy ra)
            // Không hợp ô nào của NPC → chuyển giữa túi chính và thanh nóng như rương thường
            int hotbarStart = slots.size() - 9;
            if (index < hotbarStart) {
                if (!insertItem(stack, hotbarStart, slots.size(), false)) return ItemStack.EMPTY;
            } else if (!insertItem(stack, NPC_SLOTS, hotbarStart, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
        else slot.markDirty();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTakeItem(player, stack);
        return original;
    }

    /** Ô trang bị: giáp đúng vị trí (chỉ ArmorItem, giống tương tác chuột phải cũ) hoặc vũ khí; mỗi ô 1 món. */
    private static class EquipmentSlotView extends Slot {
        private final EquipmentSlot equipmentSlot;

        EquipmentSlotView(Inventory inventory, int index, int x, int y, EquipmentSlot equipmentSlot) {
            super(inventory, index, x, y);
            this.equipmentSlot = equipmentSlot;
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            if (equipmentSlot == EquipmentSlot.MAINHAND) return SoldierNPCEntity.isWeapon(stack);
            return stack.getItem() instanceof ArmorItem armor && armor.getSlotType() == equipmentSlot;
        }

        @Override
        public int getMaxItemCount() {
            return 1;
        }

        @Nullable
        @Override
        public Pair<Identifier, Identifier> getBackgroundSprite() {
            if (equipmentSlot == EquipmentSlot.MAINHAND) {
                return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, EMPTY_WEAPON_SPRITE);
            }
            return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, EMPTY_ARMOR_SPRITES[3 - equipmentSlot.getEntitySlotId()]);
        }
    }
}
