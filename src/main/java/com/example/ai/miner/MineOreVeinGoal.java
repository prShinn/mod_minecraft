package com.example.ai.miner;

import com.example.entity.MinerNpcEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.example.block.OreVeinBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import java.util.EnumSet;
import java.util.Random;

/**
 * AI Goal cho Miner NPC — Tìm và đào block Mỏ Quặng.
 *
 * Hành vi:
 * - canStart(): thu thập TẤT CẢ ore vein trong bán kính 16, chọn RANDOM 1 cái
 * - Di chuyển đến gần block (1 lần duy nhất) → đứng im đào liên tục
 * - Mỗi DEPOSIT_INTERVAL ticks (10-15 giây) → dừng để nộp đồ (ưu tiên DepositOreToChestGoal)
 * - Nhặt drops vào inventory sau mỗi lần đào
 */
public class MineOreVeinGoal extends Goal {

    private final MinerNpcEntity npc;
    private final Random random = new Random();

    // ===== TARGET =====
    private BlockPos targetOre;

    // ===== STATE =====
    private boolean inMiningPosition = false; // Đã đứng vào vị trí đào chưa
    private int miningTicks = 0;              // Số tick đếm (20 tick = 1 phát đập)

    // ===== TIMER DEPOSIT =====
    // Mỗi 200-300 ticks (10-15 giây) thì dừng để đi cất đồ
    private int depositTimer = 0;
    private static final int DEPOSIT_INTERVAL_MIN = 200; // 10 giây
    private static final int DEPOSIT_INTERVAL_MAX = 300; // 15 giây
    private int nextDepositAt;

    // ===== TIMEOUT =====
    private int taskTicks = 0;
    private static final int MAX_TASK_TICKS = 1200; // 60 giây tối đa per block

    // ===== RANGES =====
    /**
     * NPC phải đứng trong vòng 3 blocks mới được đào.
     * Đảm bảo NPC luôn phải "chạy đến gần" mỏ trước khi đứng đào.
     */
    private static final double MINE_RANGE = 3.0;

    public MineOreVeinGoal(MinerNpcEntity npc) {
        this.npc = npc;
        // MOVE để di chuyển đến vị trí, LOOK để nhìn vào block
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
    }

    @Override
    public boolean canStart() {
        // Không bắt đầu khi đang ngủ
        if (npc.sleeping.isSleeping()) return false;
        if (!npc.hasEmptyOreSlot()) {
            npc.memory.needsToDeposit = true;
            return false;
        }

        // Thu thập tất cả ore vein trong bán kính → chọn ngẫu nhiên
        BlockPos ore = npc.findRandomOreVein();
        if (ore == null) return false;

        targetOre = ore;

        // Reset deposit timer khi bắt đầu goal mới
        nextDepositAt = randomDepositInterval();
        depositTimer = 0;
        return true;
    }

    @Override
    public void start() {
        taskTicks = 0;
        miningTicks = 0;
        inMiningPosition = false;
        // Di chuyển đến vị trí đào
        moveToBlock(targetOre);
    }

    @Override
    public void tick() {
        taskTicks++;

        // ===== PHASE 2: Kiểm tra block còn tồn tại =====
        if (!isOreVein(targetOre)) {
            return; // shouldContinue() sẽ trả về false → stop()
        }

        Vec3dDistance dist = getDistanceToBlock(targetOre);

        // ===== PHASE 4: Di chuyển đến vị trí =====
        if (!inMiningPosition) {
            if (dist.horizontal <= MINE_RANGE && dist.vertical <= MINE_RANGE) {
                // Đã đến vị trí → dừng di chuyển, bắt đầu đứng im đào
                npc.getNavigation().stop();
                inMiningPosition = true;
            } else {
                if (npc.getNavigation().isIdle()) {
                    taskTicks = MAX_TASK_TICKS;
                }
            }
            return;
        }

        // ===== PHASE 5: Đứng im đào =====
        // Nhìn vào block
        npc.getLookControl().lookAt(
                targetOre.getX() + 0.5,
                targetOre.getY() + 0.5,
                targetOre.getZ() + 0.5
        );

        // Nếu bị đẩy ra xa → quay lại vị trí
        if (dist.horizontal > MINE_RANGE || dist.vertical > MINE_RANGE) {
            inMiningPosition = false;
            moveToBlock(targetOre);
            return;
        }

        // Đếm tick đứng đào
        miningTicks++;
        depositTimer++;
        
        // Vung tay liên tục mỗi 6 ticks để tạo cảm giác đang đào thật
        if (miningTicks % 6 == 0) {
            npc.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
        }

        if (miningTicks % 20 == 0) { // Mỗi giây rớt 1 item
            mineBlock(targetOre);
        }
    }

    @Override
    public boolean shouldContinue() {
        if (npc.sleeping.isSleeping()) return false;
        if (taskTicks >= MAX_TASK_TICKS) return false;
        if (targetOre == null || !npc.hasEmptyOreSlot()) return false;
        // Dừng nếu đến giờ cất đồ
        if (depositTimer >= nextDepositAt) return false;
        return isOreVein(targetOre);
    }

    @Override
    public void stop() {
        if (targetOre != null && npc.getWorld().isChunkLoaded(targetOre)) {
            if (!isOreVein(targetOre)) {
                npc.onOreDepleted(targetOre);
            } else if (!inMiningPosition && taskTicks >= MAX_TASK_TICKS) {
                npc.skipUnreachableOre(targetOre);
            }
        }
        npc.getNavigation().stop();
        targetOre = null;
        taskTicks = 0;
        miningTicks = 0;
        inMiningPosition = false;
        
        // Báo hiệu đã xong đợt đào, có thể đi cất đồ nếu có đồ trong inventory
        npc.memory.needsToDeposit = java.util.stream.IntStream.range(0, npc.getInventory().size())
                .mapToObj(npc.getInventory()::getStack)
                .anyMatch(stack -> !stack.isEmpty() && !stack.isFood());
        
        npc.memory.resetIdle();
    }

    // ==================== PRIVATE HELPERS ====================

    /**
     * Đào block tại vị trí chỉ định, đứng nguyên tại chỗ
     */
    private boolean isOreVein(BlockPos pos) {
        return pos != null && npc.getWorld().isChunkLoaded(pos)
                && npc.getWorld().getBlockState(pos).isOf(com.example.registry.ModBlocks.ORE_VEIN);
    }

    private void mineBlock(BlockPos pos) {
        World world = npc.getWorld();
        if (world == null || world.isClient) return;

        BlockEntity be = world.getBlockEntity(pos);
        if (npc.findNearestChest() == null) {
            taskTicks = MAX_TASK_TICKS;
            return;
        }
        if (!(be instanceof OreVeinBlockEntity oreVein) || !isOreVein(pos)) return;
        for (ItemStack stack : oreVein.mine((net.minecraft.server.world.ServerWorld) world, pos, npc)) {
            ItemStack remaining = npc.getInventory().addStack(stack);
            if (!remaining.isEmpty()) npc.dropStack(remaining);
        }

        npc.memory.lastOrePos = pos;
        npc.memory.resetIdle();
    }

    /**
     * Di chuyển NPC đến đúng sát cạnh block mỏ (đủ để nằm trong MINE_RANGE = 3 blocks).
     * Offset nhỏ (±1 block) để NPC đứng cạnh mỏ, không đứng trên mỏ.
     */
    private void moveToBlock(BlockPos block) {
        var navigation = npc.getNavigation();
        var path = navigation.findPathTo(block.getX() + 0.5, block.getY(), block.getZ() + 0.5, 2);
        navigation.startMovingAlong(path, 1.2);
    }

    /**
     * Tính khoảng cách ngang + dọc đến block target
     */
    private Vec3dDistance getDistanceToBlock(BlockPos block) {
        net.minecraft.util.math.Vec3d npcPos = npc.getPos();
        double horiz = Math.sqrt(
                Math.pow(block.getX() + 0.5 - npcPos.x, 2) +
                Math.pow(block.getZ() + 0.5 - npcPos.z, 2)
        );
        double vert = Math.abs(block.getY() - npcPos.y);
        return new Vec3dDistance(horiz, vert);
    }

    /**
     * Random thời gian cất đồ: 200-300 ticks (10-15 giây)
     */
    private int randomDepositInterval() {
        return DEPOSIT_INTERVAL_MIN + random.nextInt(DEPOSIT_INTERVAL_MAX - DEPOSIT_INTERVAL_MIN + 1);
    }

    /**
     * Helper record khoảng cách
     */
    private record Vec3dDistance(double horizontal, double vertical) {}
}
