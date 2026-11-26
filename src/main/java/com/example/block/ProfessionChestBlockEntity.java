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

    // ===== Neo NPC: mỗi rương chỉ một NPC nghề =====
    /** NPC đã neo vào rương này (null = rương trống, NPC nào đúng nghề cũng nhận được). */
    @Nullable
    private java.util.UUID boundNpc;
    /** Lần đầu thấy NPC neo không còn trong thế giới (không lưu NBT); -1 = đang thấy. */
    private long boundMissingSince = -1;
    /** NPC neo vắng mặt quá lâu (chết khi chunk chưa nạp, bị xoá bằng lệnh...) thì coi rương là trống. */
    private static final long STALE_BINDING_TICKS = 20 * 60;

    @Nullable
    public java.util.UUID getBoundNpc() {
        return boundNpc;
    }

    /** Rương còn trống cho NPC này: chưa ai neo, chính NPC này neo, hoặc NPC neo cũ đã mất. */
    public boolean isFreeFor(java.util.UUID npc) {
        if (boundNpc == null) return true;
        if (boundNpc.equals(npc)) {
            boundMissingSince = -1; // chính chủ đang dùng → không bao giờ bị coi là vắng mặt
            return true;
        }
        if (!(world instanceof net.minecraft.server.world.ServerWorld server)) return false;
        var bound = server.getEntity(boundNpc);
        if (bound != null) {
            boundMissingSince = -1;
            // NPC neo còn sống nhưng đã chuyển sang rương khác (bị dắt đi xa khi chunk này chưa nạp) → rương trống
            return bound instanceof com.example.entity.base.WorkerNpcEntity worker && !pos.equals(worker.getChestPos());
        }
        long now = server.getTime();
        if (boundMissingSince < 0) boundMissingSince = now;
        return now - boundMissingSince > STALE_BINDING_TICKS;
    }

    public void bind(java.util.UUID npc) {
        if (npc.equals(boundNpc)) return;
        boundNpc = npc;
        boundMissingSince = -1;
        markDirty();
    }

    /** Gỡ neo — chỉ khi đúng NPC đang neo. */
    public void unbind(java.util.UUID npc) {
        if (npc.equals(boundNpc)) {
            boundNpc = null;
            boundMissingSince = -1;
            markDirty();
        }
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
        boundNpc = nbt.containsUuid("BoundNpc") ? nbt.getUuid("BoundNpc") : null;
        // "StoredTool" của rương cũ bị bỏ qua: không còn trả công cụ khi phá rương
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, inventory);
        if (ownerId != null) nbt.putUuid("ChestOwner", ownerId);
        if (boundNpc != null) nbt.putUuid("BoundNpc", boundNpc);
    }
}
