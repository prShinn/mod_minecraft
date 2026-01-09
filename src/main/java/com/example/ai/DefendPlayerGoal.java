package com.example.ai;

import com.example.entity.SoldierNPCEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.EnumSet;

public class DefendPlayerGoal extends Goal {
    private final SoldierNPCEntity npc;
    private LivingEntity targetMob;
    private final float maxDistance;
    private int cooldown;

    public DefendPlayerGoal(SoldierNPCEntity npc, float maxDistance) {
        this.npc = npc;
        this.maxDistance = maxDistance;
        this.setControls(EnumSet.of(Control.TARGET, Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }

        PlayerEntity owner = this.npc.getOwner();
        if (owner == null || !owner.isAlive()) {
            return false;
        }

        // Kiểm tra xem chủ nhân có bị tấn công không
        LivingEntity attacker = owner.getAttacker();

        if (attacker != null &&
                attacker.isAlive() &&
                !(attacker instanceof PlayerEntity) && // Không đánh player khác
                this.npc.squaredDistanceTo(attacker) <= this.maxDistance * this.maxDistance) {

            this.targetMob = attacker;
            return true;
        }

        return false;
    }

    @Override
    public boolean shouldContinue() {
        PlayerEntity owner = this.npc.getOwner();
        if (owner == null || !owner.isAlive()) {
            return false;
        }

        if (this.targetMob == null || !this.targetMob.isAlive()) {
            return false;
        }

        // Dừng nếu quá xa chủ nhân
        return this.npc.squaredDistanceTo(owner) <= this.maxDistance * this.maxDistance;
    }

    @Override
    public void start() {
        this.npc.setTarget(this.targetMob);
        super.start();
    }

    @Override
    public void stop() {
        this.npc.setTarget(null);
        this.targetMob = null;
        this.cooldown = 20;
        super.stop();
    }
}
