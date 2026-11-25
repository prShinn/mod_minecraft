package com.example.ai.rancher;

import com.example.config.ModConfig;
import com.example.entity.RancherNpcEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.event.GameEvent;

import java.util.Comparator;
import java.util.EnumSet;

/** Xén lông cừu trong vùng quanh rương; lông rơi ra đất để CollectAnimalDropsGoal nhặt. */
public class ShearSheepGoal extends Goal {
    private static final int MAX_TICKS = 200;
    private static final double SHEAR_RANGE_SQ = 2.5 * 2.5;

    private final RancherNpcEntity npc;
    private SheepEntity sheep;
    private long nextScan;
    private int ticks;
    private boolean done;

    public ShearSheepGoal(RancherNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (npc.sleeping.isSleeping() || npc.needsToDeposit()) return false;
        long now = npc.getWorld().getTime();
        if (now < nextScan) return false;
        nextScan = now + 40;
        BlockPos center = npc.findWorkChest();
        if (center == null) return false;
        int r = ModConfig.get().rancherWorkRadius;
        sheep = npc.getWorld().getEntitiesByClass(SheepEntity.class, new Box(center).expand(r, 4, r),
                        s -> s.isAlive() && s.isShearable() && !npc.isSkipped(s))
                .stream().min(Comparator.comparingDouble(npc::squaredDistanceTo)).orElse(null);
        return sheep != null;
    }

    @Override
    public void start() {
        ticks = 0;
        done = false;
        npc.getNavigation().startMovingTo(sheep, 1.0);
    }

    @Override
    public void tick() {
        ticks++;
        npc.getLookControl().lookAt(sheep, 30.0F, 30.0F);
        if (npc.canReachTarget(sheep, SHEAR_RANGE_SQ, ticks)) {
            npc.getNavigation().stop();
            npc.swingHand(Hand.MAIN_HAND);
            sheep.sheared(SoundCategory.NEUTRAL);
            npc.getWorld().emitGameEvent(npc, GameEvent.SHEAR, sheep.getPos());
            done = true;
        } else if (ticks % 20 == 0) {
            npc.getNavigation().startMovingTo(sheep, 1.0);
        }
        if (ticks >= MAX_TICKS) {
            npc.skipTarget(sheep, 600); // không tới được (rào, nước...) → bỏ qua 30 giây
            npc.display.report(npc, "Không tới được chỗ cừu");
            done = true;
        }
    }

    @Override
    public boolean shouldContinue() {
        return !done && !npc.sleeping.isSleeping() && sheep != null && sheep.isAlive() && sheep.isShearable();
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        sheep = null;
        npc.resetIdle();
    }
}
