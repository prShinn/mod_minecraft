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
    private long retryAt;
    private boolean finished;

    public PlantSeedGoal(FarmerNpcEntity npc) {
        this.npc = npc;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (npc.sleeping.isSleeping() || npc.getWorld().getTime() < retryAt) return false;
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
            moveTo(chest, false);
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
            nextPlot();
        }
    }

    private void beginPlanting() {
        plots.clear();
        for (BlockPos pos : BlockPos.iterate(chest.add(-12, -2, -12), chest.add(12, 2, 12))) {
            if (isPlantable(pos)) plots.add(pos.toImmutable());
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
        plots.removeIf(plot -> !isPlantable(plot));
        if (!plots.isEmpty()) {
            plots.sort(Comparator.comparingDouble(npc.getBlockPos()::getSquaredDistance));
            farmland = plots.remove(0);
            moveTo(farmland, true);
        } else {
            npc.returningSeeds = true;
            moveTo(chest, false);
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
        return npc.getWorld().isChunkLoaded(plot)
                && npc.getWorld().getBlockState(plot).isOf(Blocks.FARMLAND)
                && npc.getWorld().getBlockState(plot.up()).isAir()
                && !npc.isWorkedByAnother(plot);
    }

    private boolean travelFailed() {
        return travelTicks >= 240 || npc.getNavigation().isIdle();
    }

    private void moveTo(BlockPos pos, boolean plot) {
        travelTicks = 0;
        npc.getNavigation().stop();
        npc.getNavigation().startMovingTo(pos.getX() + 0.5, pos.getY() + (plot ? 1 : 0), pos.getZ() + 0.5, 1.3);
    }
}
