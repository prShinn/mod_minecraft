package com.example.ai.rancher;

import com.example.config.ModConfig;
import com.example.entity.RancherNpcEntity;
import com.example.helper.NpcFeedback;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tỉa bớt vật nuôi trưởng thành để lấy thịt/da/lông khi đàn đạt ngưỡng tối đa trong cấu hình.
 * Luôn giữ lại ít nhất 2 con trưởng thành mỗi loài để duy trì đàn.
 */
public class CullAnimalsGoal extends Goal {
    private static final int MAX_TICKS = 300;
    private static final int ATTACK_COOLDOWN_TICKS = 20;
    private static final double ATTACK_RANGE_SQ = 2.5 * 2.5;
    private static final int MIN_ADULTS_TO_KEEP = 2;

    private final RancherNpcEntity npc;
    private AnimalEntity target;
    private long nextScan;
    private int ticks;
    private int attackCooldown;
    private boolean done;

    public CullAnimalsGoal(RancherNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
    }

    private static boolean isFarmAnimal(AnimalEntity animal) {
        return animal instanceof CowEntity || animal instanceof SheepEntity
                || animal instanceof PigEntity || animal instanceof ChickenEntity;
    }

    @Override
    public boolean canStart() {
        if (npc.sleeping.isSleeping() || npc.needsToDeposit()) return false;
        long now = npc.getWorld().getTime();
        if (now < nextScan) return false;
        nextScan = now + 100;

        BlockPos chest = npc.findWorkChest();
        if (chest == null) return false;

        int r = ModConfig.get().rancherWorkRadius;
        int maxPerType = ModConfig.get().rancherMaxAnimalsPerType;

        Map<EntityType<?>, List<AnimalEntity>> herds = new HashMap<>();
        for (AnimalEntity animal : npc.getWorld().getEntitiesByClass(AnimalEntity.class,
                new Box(chest).expand(r, 4, r), a -> a.isAlive() && isFarmAnimal(a))) {
            herds.computeIfAbsent(animal.getType(), t -> new ArrayList<>()).add(animal);
        }

        target = null;
        for (List<AnimalEntity> herd : herds.values()) {
            if (herd.size() < maxPerType) continue;

            long totalAdults = herd.stream().filter(a -> !a.isBaby()).count();
            if (totalAdults <= MIN_ADULTS_TO_KEEP) continue;

            AnimalEntity candidate = herd.stream()
                    .filter(a -> !a.isBaby() && !a.isInLove() && !npc.isSkipped(a))
                    .min(Comparator.comparingDouble(npc::squaredDistanceTo))
                    .orElse(null);
            if (candidate != null) {
                target = candidate;
                return true;
            }
        }
        return false;
    }

    @Override
    public void start() {
        ticks = 0;
        attackCooldown = 0;
        done = false;
        npc.getNavigation().startMovingTo(target, 1.0);
    }

    @Override
    public void tick() {
        ticks++;
        if (attackCooldown > 0) attackCooldown--;

        if (target == null || !target.isAlive()) {
            NpcFeedback.success(npc);
            done = true;
            return;
        }

        npc.getLookControl().lookAt(target, 30.0F, 30.0F);
        if (npc.squaredDistanceTo(target) <= ATTACK_RANGE_SQ) {
            npc.getNavigation().stop();
            if (attackCooldown <= 0) {
                npc.swingHand(Hand.MAIN_HAND);
                npc.tryAttack(target);
                attackCooldown = ATTACK_COOLDOWN_TICKS;
                if (!target.isAlive()) {
                    NpcFeedback.success(npc);
                    done = true;
                }
            }
        } else if (ticks % 20 == 0) {
            npc.getNavigation().startMovingTo(target, 1.0);
        }

        if (ticks >= MAX_TICKS) {
            npc.skipTarget(target, 600);
            done = true;
        }
    }

    @Override
    public boolean shouldContinue() {
        return !done && !npc.sleeping.isSleeping() && !npc.needsToDeposit()
                && target != null && target.isAlive();
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        target = null;
        npc.resetIdle();
    }
}
