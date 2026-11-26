package com.example.ai.miner;

import com.example.ai.worker.DepositCargoGoal;
import com.example.block.MinerChestBlockEntity;
import com.example.entity.MinerNpcEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.Box;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * Thợ mỏ đứng cạnh rương neo và đào từ xa từng block trong vùng đào của rương (thứ tự trên xuống).
 * <ul>
 *     <li>Thời gian đào = độ cứng × 30 / tốc độ cúp (cúp tốt hơn + Hiệu suất → nhanh hơn), như người chơi.</li>
 *     <li>Dùng cúp tốt nhất trong rương; cúp KHÔNG mất độ bền (đồ nghề NPC không bao giờ hỏng).</li>
 *     <li>Đồ rơi cho thẳng vào rương; rương không đủ chỗ thì dừng chờ.</li>
 *     <li>Bỏ qua: không khí, chất lỏng, block không phá được, block có block entity (rương, lò...),
 *         block cần cúp tốt hơn (không mất đồ), chỗ Thợ mỏ đang đứng.</li>
 * </ul>
 */
public class MineQuarryGoal extends Goal {
    private static final double STATION_REACH_SQ = 3.0 * 3.0;
    private static final int RETRY_TICKS = 100;
    private static final int MAX_TRAVEL_TICKS = 400;

    private final MinerNpcEntity npc;
    @Nullable
    private BlockPos chest;
    @Nullable
    private BlockPos target;
    private int breakTicks;
    private int neededTicks;
    private int travelTicks;
    private long retryAt;
    private boolean done;
    /** Lần quét vừa rồi có block bị bỏ vì cúp chưa đủ tốt. */
    private boolean toolTooWeak;
    /** Cấu hình vùng lúc chọn ô hiện tại: người chơi lưu cấu hình mới → bỏ ô cũ. */
    private int revision;
    /** Trạng thái block lúc bắt đầu đào: bị thay giữa chừng → tính lại thời gian. */
    @Nullable
    private BlockState targetState;
    /** Chưa quét hết vùng (hết ngân sách) → thử lại gần như ngay. */
    private boolean scanPending;

    public MineQuarryGoal(MinerNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (npc.sleeping.isSleeping() || npc.getWorld().getTime() < retryAt) return false;
        retryAt = npc.getWorld().getTime() + RETRY_TICKS;
        MinerChestBlockEntity box = chestEntity();
        if (box == null) return false;
        if (!box.isConfigured()) {
            npc.display.report(npc, "Chưa cấu hình vùng đào (Shift + chuột phải tay không vào rương)");
            return false;
        }
        if (npc.isSharingChest()) {
            npc.display.report(npc, "Đang dùng chung rương — đặt thêm Rương Thợ mỏ để đào");
            return false;
        }
        ItemStack pick = bestPickaxe(box);
        if (pick == null) {
            npc.display.report(npc, "Cần cúp trong Rương Thợ mỏ");
            return false;
        }
        if (!hasEmptySlot(box)) {
            npc.display.report(npc, "Rương thợ mỏ đã đầy");
            return false;
        }
        target = findTarget(box, pick);
        if (target == null) {
            if (box.isScanPending()) retryAt = npc.getWorld().getTime() + 2; // chưa quét hết vùng → tìm tiếp ngay
            else if (toolTooWeak) npc.display.report(npc, "Cần cúp tốt hơn để đào phần còn lại");
            return false;
        }
        revision = box.getRevision();
        return true;
    }

    @Override
    public void start() {
        done = false;
        travelTicks = 0;
        beginBreaking();
        moveToStation();
    }

    @Override
    public void tick() {
        MinerChestBlockEntity box = chestEntity();
        if (box == null || target == null) {
            done = true;
            return;
        }
        if (npc.squaredDistanceTo(Vec3d.ofCenter(chest)) > STATION_REACH_SQ) {
            // Chưa tới chỗ đứng cạnh rương
            if (++travelTicks > MAX_TRAVEL_TICKS) {
                npc.display.report(npc, "Không có đường tới rương thợ mỏ");
                done = true;
            } else if (travelTicks % 20 == 0 && npc.getNavigation().isIdle()) {
                moveToStation();
            }
            return;
        }
        npc.getNavigation().stop();
        npc.getLookControl().lookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

        if (box.getRevision() != revision) {
            // Người chơi vừa lưu vùng đào mới → bỏ ô của vùng cũ
            clearCrack();
            nextTarget(box, bestPickaxe(box));
            return;
        }
        ItemStack pick = bestPickaxe(box);
        if (pick == null) {
            clearCrack(); // cúp bị lấy ra giữa chừng: dừng, KHÔNG bỏ qua ô này
            done = true;
            return;
        }
        BlockState state = npc.getWorld().getBlockState(target);
        if (!canMine(target, state, pick)) {
            // Block đổi (người chơi đào mất, nước chảy vào...), có người/vật đứng đó → ô kế
            clearCrack();
            box.advance();
            nextTarget(box, pick);
            return;
        }
        if (state != targetState) {
            targetState = state; // block bị thay giữa chừng → tính lại thời gian đào
            breakTicks = 0;
            neededTicks = breakTime(state, pick);
        }
        if (breakTicks % 5 == 0) npc.swingHand(Hand.MAIN_HAND);
        breakTicks++;
        int stage = MathHelper.clamp(breakTicks * 10 / Math.max(1, neededTicks), 0, 9);
        npc.getWorld().setBlockBreakingInfo(npc.getId(), target, stage);
        if (breakTicks < neededTicks) return;

        if (!(npc.getWorld() instanceof ServerWorld world)) return;
        List<ItemStack> drops = Block.getDroppedStacks(state, world, target, null, npc, pick);
        if (!fits(box, drops)) {
            npc.display.report(npc, "Rương thợ mỏ đã đầy");
            clearCrack();
            done = true;
            return;
        }
        clearCrack();
        world.breakBlock(target, false, npc);
        state.onStacksDropped(world, target, pick, true); // kinh nghiệm từ quặng như người chơi đào
        for (ItemStack drop : drops) {
            ItemStack rest = DepositCargoGoal.insert(box, drop);
            if (!rest.isEmpty()) Block.dropStack(world, chest.up(), rest); // không xảy ra vì đã kiểm tra fits
        }
        // Đồ nghề của NPC không bao giờ hỏng: cúp chỉ quyết định tốc độ/cấp đào, không mất độ bền
        box.markDirty();
        npc.display.clearReport();
        box.advance();
        nextTarget(box, bestPickaxe(box));
    }

    @Override
    public boolean shouldContinue() {
        return !done && target != null && !npc.sleeping.isSleeping() && !npc.isSharingChest();
    }

    @Override
    public void stop() {
        clearCrack();
        npc.getNavigation().stop();
        target = null;
        chest = null;
        retryAt = npc.getWorld().getTime() + (scanPending ? 2 : done ? RETRY_TICKS : 20);
        scanPending = false;
        targetState = null;
    }

    // ===== Nội bộ =====

    @Nullable
    private MinerChestBlockEntity chestEntity() {
        BlockPos pos = npc.findWorkChest();
        if (pos == null) return null;
        chest = pos;
        return npc.getWorld().getBlockEntity(pos) instanceof MinerChestBlockEntity box ? box : null;
    }

    private void nextTarget(MinerChestBlockEntity box, @Nullable ItemStack pick) {
        target = pick == null || !hasEmptySlot(box) ? null : findTarget(box, pick);
        if (target == null) {
            done = true;
            if (box.isScanPending()) scanPending = true;
            else if (toolTooWeak) npc.display.report(npc, "Cần cúp tốt hơn để đào phần còn lại");
            return;
        }
        revision = box.getRevision();
        beginBreaking();
    }

    @Nullable
    private BlockPos findTarget(MinerChestBlockEntity box, ItemStack pick) {
        toolTooWeak = false;
        return box.nextTarget(npc.getWorld(), pos -> canMine(pos, npc.getWorld().getBlockState(pos), pick));
    }

    private void beginBreaking() {
        breakTicks = 0;
        if (target == null) return;
        MinerChestBlockEntity box = chestEntity();
        ItemStack pick = box == null ? null : bestPickaxe(box);
        targetState = npc.getWorld().getBlockState(target);
        neededTicks = pick == null ? 20 : breakTime(targetState, pick);
    }

    private boolean canMine(BlockPos pos, BlockState state, ItemStack pick) {
        if (state.isAir() || !state.getFluidState().isEmpty()) return false; // chất lỏng (cả block ngập nước)
        if (state.getHardness(npc.getWorld(), pos) < 0) return false;          // bedrock, block lệnh...
        if (state.hasBlockEntity()) return false;                               // rương, lò, giường... của người chơi
        if (pos.equals(chest)) return false;
        // Nửa trên cửa / cây cao: đào nửa dưới mới rơi đồ (đào nửa trên trước thì mất vật phẩm)
        if (state.contains(Properties.DOUBLE_BLOCK_HALF) && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return false;
        }
        if (state.isToolRequired() && !pick.isSuitableFor(state)) {
            // Chỉ báo "cần cúp tốt hơn" khi đúng là block của cúp; block cần công cụ khác (mạng nhện...) bỏ qua lặng lẽ
            if (state.isIn(BlockTags.PICKAXE_MINEABLE)) toolTooWeak = true;
            return false;
        }
        // Không đào chỗ đang có người/vật đứng (kể cả Thợ mỏ) hoặc ngay dưới chân họ → không ai rơi xuống hố
        return npc.getWorld().getEntitiesByClass(LivingEntity.class, new Box(pos).stretch(0, 2, 0), e -> true).isEmpty();
    }

    /** Số tick đào như người chơi: độ cứng × 30 / tốc độ (cúp + Hiệu suất). */
    static int breakTime(float hardness, float toolSpeed, int efficiency) {
        float speed = toolSpeed;
        if (speed > 1.0F && efficiency > 0) speed += efficiency * efficiency + 1;
        return Math.max(2, MathHelper.ceil(hardness * 30.0F / speed));
    }

    private int breakTime(BlockState state, ItemStack pick) {
        return breakTime(state.getHardness(npc.getWorld(), target), pick.getMiningSpeedMultiplier(state),
                EnchantmentHelper.getLevel(Enchantments.EFFICIENCY, pick));
    }

    /** Cúp tốt nhất trong rương (cấp vật liệu cao nhất, rồi Hiệu suất). */
    @Nullable
    private static ItemStack bestPickaxe(Inventory box) {
        ItemStack best = null;
        float bestScore = -1;
        for (int i = 0; i < box.size(); i++) {
            ItemStack stack = box.getStack(i);
            if (!(stack.getItem() instanceof PickaxeItem pickaxe)) continue;
            float score = pickaxe.getMaterial().getMiningLevel() * 100
                    + pickaxe.getMaterial().getMiningSpeedMultiplier()
                    + EnchantmentHelper.getLevel(Enchantments.EFFICIENCY, stack) * 10;
            if (score > bestScore) {
                bestScore = score;
                best = stack;
            }
        }
        return best;
    }

    private static boolean hasEmptySlot(Inventory box) {
        for (int i = 0; i < box.size(); i++) if (box.getStack(i).isEmpty()) return true;
        return false;
    }

    /** Thử cho toàn bộ đồ rơi vào bản sao rương: chỉ đào khi chứa được hết (không rơi đồ ra đất). */
    private static boolean fits(Inventory box, List<ItemStack> drops) {
        SimpleInventory copy = new SimpleInventory(box.size());
        for (int i = 0; i < box.size(); i++) copy.setStack(i, box.getStack(i).copy());
        for (ItemStack drop : drops) {
            if (!DepositCargoGoal.insert(copy, drop.copy()).isEmpty()) return false;
        }
        return true;
    }

    /** Đi tới ô đứng cạnh rương, ưu tiên phía sau rương (ngược hướng vùng đào). */
    private void moveToStation() {
        if (chest == null) return;
        MinerChestBlockEntity box = chestEntity();
        Direction away = box != null ? box.getQuarry().direction().getOpposite() : Direction.NORTH;
        BlockBox area = box != null ? box.getQuarry().box(chest) : null;
        for (Direction side : new Direction[]{away, away.rotateYClockwise(), away.rotateYCounterclockwise()}) {
            BlockPos stand = chest.offset(side);
            if (area != null && (area.contains(stand) || area.contains(stand.down()))) continue;
            if (npc.getNavigation().startMovingTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 1.0)) return;
        }
        // Dự phòng: điểm đứng bất kỳ cạnh rương, nhưng không nằm trong vùng đào (tránh rơi xuống hố)
        var path = npc.findReachablePath(chest, STATION_REACH_SQ);
        if (path == null || path.getEnd() == null) return;
        BlockPos end = path.getEnd().getBlockPos();
        if (area == null || !area.contains(end) && !area.contains(end.down())) npc.getNavigation().startMovingAlong(path, 1.0);
    }

    private void clearCrack() {
        if (target != null) npc.getWorld().setBlockBreakingInfo(npc.getId(), target, -1);
    }
}
