package com.example.ai;

import com.example.entity.SoldierNPCEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.BowItem;
import net.minecraft.util.Hand;

public class SoldierBowGoal extends Goal {
    private final SoldierNPCEntity npc;
    private LivingEntity target;
    private int attackCooldown;

    public SoldierBowGoal(SoldierNPCEntity npc) {
        this.npc = npc;
    }

    @Override
    public boolean canStart() {
        LivingEntity e = npc.getTarget();
        if (e == null || !e.isAlive()) return false;
        this.target = e;
        return npc.getMainHandStack().getItem() instanceof BowItem;
    }

    @Override
    public void tick() {
        if (target == null) return;

        npc.getLookControl().lookAt(target, 30.0F, 30.0F);

        double distanceSq = npc.squaredDistanceTo(target);

        if (distanceSq > 15*15) {
            //nếu xa hơn 15 block thì lại gần
            npc.getNavigation().startMovingTo(target, 1.0);
        } else {
            npc.getNavigation().stop();
        }

        if (attackCooldown <= 0) {
            shootArrow(target);
            attackCooldown = 30; // 1,5 giây cooldown
        } else attackCooldown--;
    }

    private void shootArrow(LivingEntity target) {
        if (npc.getWorld().isClient) return;

        ArrowEntity arrow = new ArrowEntity(npc.getWorld(), npc);
        double dx = target.getX() - npc.getX();
        double dy = target.getBodyY(0.333) - arrow.getY();
        double dz = target.getZ() - npc.getZ();
        double distance = Math.sqrt(dx*dx + dz*dz) * 0.2;

        arrow.setVelocity(dx, dy + distance, dz, 1.6F, 12.0F);
        npc.getWorld().spawnEntity(arrow);
        npc.swingHand(Hand.MAIN_HAND);
    }
}

