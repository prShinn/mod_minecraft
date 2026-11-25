package com.example.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity chung cho tất cả Rương Nghề Nghiệp.
 * - Inventory 27 slot (3×9), serialize/deserialize NBT.
 * - Implement NamedScreenHandlerFactory để mở GUI chest 3×9.
 */
public class ProfessionChestBlockEntity extends BlockEntity
        implements net.minecraft.inventory.Inventory, NamedScreenHandlerFactory {

    /** Inventory 27 slot chứa vật phẩm NPC cất vào. */
    protected final DefaultedList<ItemStack> inventory =
            DefaultedList.ofSize(27, ItemStack.EMPTY);
    private java.util.UUID ownerId;

    public boolean canOpen(PlayerEntity player) {
        return ownerId == null || ownerId.equals(player.getUuid());
    }

    /** NPC thuộc chủ ownerUuid có được dùng rương này không (rương chưa có chủ thì ai cũng dùng được). */
    public boolean isAccessibleBy(@Nullable java.util.UUID ownerUuid) {
        return ownerId == null || ownerId.equals(ownerUuid);
    }

    public void setOwner(PlayerEntity player) {
        ownerId = player.getUuid();
        markDirty();
    }


    public ProfessionChestBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ===== NamedScreenHandlerFactory =====

    @Override
    public Text getDisplayName() {
        // Lấy tên từ translation key của block
        return Text.translatable(getCachedState().getBlock().getTranslationKey());
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, this);
    }

    // ===== Inventory interface =====

    @Override
    public int size() { return 27; }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getStack(int slot) { return inventory.get(slot); }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(inventory, slot, amount);
        if (!result.isEmpty()) markDirty();
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return Inventories.removeStack(inventory, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        inventory.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
        markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return world != null && player.getWorld() == world
                && world.getBlockEntity(pos) == this && canOpen(player)
                && player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }

    @Override
    public void clear() { inventory.clear(); }

    // ===== NBT =====

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        Inventories.readNbt(nbt, inventory);
        ownerId = nbt.containsUuid("ChestOwner") ? nbt.getUuid("ChestOwner") : null;
        // "StoredTool" của rương cũ bị bỏ qua: không còn trả công cụ khi phá rương
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, inventory);
        if (ownerId != null) nbt.putUuid("ChestOwner", ownerId);
    }
}
