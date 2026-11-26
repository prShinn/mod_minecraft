package com.example.ai.farmer;

import com.example.entity.FarmerNpcEntity;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class PlantSeedGoal extends Goal {
    private final FarmerNpcEntity npc;
    private final List<BlockPos> plots = new ArrayList<>();
    private BlockPos farmland;
    private BlockPos chest;
    private int travelTicks;
    /** Tầm gieo hạt / tầm với rương (bình phương), khớp điều kiện trong tick(). */
    private static final double PLANT_REACH_SQ = 4.0;
    private static final double CHEST_REACH_SQ = 6.25;
    private static final int MAX_PATH_CHECKS = 3; // tầm 64 → mỗi lần tính đường tới 1024 nút
    private long retryAt;
    private boolean finished;

    public PlantSeedGoal(FarmerNpcEntity npc) {
        this.npc = npc;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (npc.sleeping.isSleeping() || npc.getWorld().getTime() < retryAt) return false;
        retryAt = npc.getWorld().getTime() + 10; // kiểm tra (hạt giống, truy vấn entity) tối đa 2 lần/giây
        if (!npc.plantingSeeds.isEmpty() && npc.getSeedChest(npc.seedChestPos) != null) {
            chest = npc.seedChestPos;
            return true;
        }
        if (npc.findNearestChest() == null) return false;
        chest = npc.getChestPos();
        return chest != null && (!npc.plantingSeeds.isEmpty()
                || (npc.cachedEmptyFarmland != null && npc.isNearFarmChest(npc.cachedEmptyFarmland)
                    && isPlantable(npc.cachedEmptyFarmland) && npc.hasSeeds()));
    }

    @Override
    public void start() {
        finished = false;
        if (npc.plantingSeeds.isEmpty() || npc.returningSeeds) {
            moveToChest();
        } else {
            beginPlanting();
        }
    }

    @Override
    public void tick() {
        if (finished) return;
        travelTicks++;
        if (farmland == null) {
            if (npc.squaredDistanceTo(Vec3d.ofCenter(chest)) <= 6.25) {
                if (npc.returningSeeds) {
                    npc.returnSeedBatch(chest);
                    finished = true;
                } else if (npc.takeSeedBatch(chest)) {
                    beginPlanting();
                } else {
                    finished = true;
                }
            } else if (travelFailed()) {
                finished = true;
            }
            return;
        }

        if (!isPlantable(farmland)) {
            nextPlot();
        } else if (npc.squaredDistanceTo(Vec3d.ofCenter(farmland.up())) <= 4.0) {
            npc.activeFarmPlot = farmland;
            ItemStack seed = npc.plantingSeeds.get(0);
            if (npc.plantSeedAt(farmland, seed)) {
                npc.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
                seed.decrement(1);
                if (seed.isEmpty()) npc.plantingSeeds.remove(0);
                npc.memory.lastFarmPos = farmland;
                npc.cachedEmptyFarmland = null;
            }
            nextPlot();
        } else if (travelFailed()) {
            npc.skipPlot(farmland); // đi mãi không tới (rào, vật cản) → để ô khác lên trước
            nextPlot();
        }
    }

    private void beginPlanting() {
        plots.clear();
        // Cả mảnh ruộng liền quanh rương neo (ruộng rộng, bậc thang), không chỉ ô 25×25 sát rương
        for (BlockPos pos : npc.farmPlots()) {
            if (isEmptyFarmland(pos)) plots.add(pos);
        }
        nextPlot();
    }

    private void nextPlot() {
        npc.activeFarmPlot = null;
        farmland = null;
        if (npc.plantingSeeds.isEmpty()) {
            npc.seedChestPos = null;
            npc.returningSeeds = false;
            finished = true;
            return;
        }
        plots.removeIf(plot -> !isEmptyFarmland(plot) || npc.isPlotSkipped(plot));
        plots.sort(Comparator.comparingDouble(npc.getBlockPos()::getSquaredDistance));
        // Ưu tiên ô có đường đi thật (vòng qua cửa rào); giới hạn số lần tính đường mỗi lượt để không lag
        for (int checks = 0; checks < MAX_PATH_CHECKS && !plots.isEmpty(); checks++) {
            BlockPos candidate = plots.remove(0);
            if (npc.isWorkedByAnother(candidate)) { // chỉ hỏi entity cho ô sắp chọn (cả nghìn ô thì quá đắt)
                checks--;
                continue;
            }
            if (moveTo(candidate.up(), PLANT_REACH_SQ)) {
                farmland = candidate;
                return;
            }
            npc.skipPlot(candidate);
        }
        // Không còn ô nào tới được → mang hạt về rương
        npc.returningSeeds = true;
        moveToChest();
    }

    private void moveToChest() {
        if (!moveTo(chest, CHEST_REACH_SQ)) {
            npc.display.report(npc, "Không có đường tới rương nghề");
            finished = true;
        }
    }

    @Override
    public boolean shouldContinue() {
        return !finished && !npc.sleeping.isSleeping();
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        npc.activeFarmPlot = null;
        plots.clear();
        farmland = null;
        chest = null;
        retryAt = npc.getWorld().getTime() + 40;
    }

    private boolean isPlantable(BlockPos plot) {
        return isEmptyFarmland(plot) && !npc.isWorkedByAnother(plot);
    }

    /** Đất cày trống (không hỏi entity — rẻ, dùng để lọc cả mảnh ruộng). */
    private boolean isEmptyFarmland(BlockPos plot) {
        return npc.getWorld().isChunkLoaded(plot)
                && npc.getWorld().getBlockState(plot).isOf(Blocks.FARMLAND)
                && npc.getWorld().getBlockState(plot.up()).isAir();
    }

    private boolean travelFailed() {
        return travelTicks >= 240 || npc.getNavigation().isIdle();
    }

    /** Tính đường tới target rồi đi; false nếu không có đường tới được tầm reachSq. */
    private boolean moveTo(BlockPos target, double reachSq) {
        travelTicks = 0;
        npc.getNavigation().stop();
        if (npc.squaredDistanceTo(Vec3d.ofCenter(target)) <= reachSq) return true; // đã đứng trong tầm
        var path = npc.findReachablePath(target, reachSq);
        return path != null && npc.getNavigation().startMovingAlong(path, 1.3);
    }
}
