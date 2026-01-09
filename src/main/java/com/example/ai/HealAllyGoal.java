package com.example.ai;

import com.example.entity.SoldierNPCEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.ToolMaterials;

import java.util.EnumSet;
import java.util.List;

public class HealAllyGoal extends Goal {

    private final PathAwareEntity medic;
    private LivingEntity target;

    private static final double SEARCH_RANGE = 12.0D;
    private static final double HEAL_DISTANCE = 4.0D;
    private static final int HEAL_COOLDOWN_TICKS = 60; // 3s

    private int healCooldown = 0;

    public HealAllyGoal(PathAwareEntity medic) {
        this.medic = medic;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    // =========================
    // TÌM MỤC TIÊU
    // =========================
    @Override
    public boolean canStart() {

        List<LivingEntity> targets = medic.getWorld().getEntitiesByClass(
                LivingEntity.class,
                medic.getBoundingBox().expand(SEARCH_RANGE),
                this::isValidHealTarget
        );

        if (targets.isEmpty()) return false;

        // Ưu tiên entity máu thấp nhất
        targets.sort((a, b) -> Float.compare(a.getHealth(), b.getHealth()));
        target = targets.get(0);

        return true;
    }

    @Override
    public boolean shouldContinue() {
        return target != null
                && target.isAlive()
                && target.getHealth() < target.getMaxHealth();
    }

    @Override
    public void start() {
        medic.getNavigation().startMovingTo(target, 1.1D);
    }

    @Override
    public void tick() {
        if (target == null) return;

        if (healCooldown > 0) {
            healCooldown--;
        }

        medic.getLookControl().lookAt(target, 30.0F, 30.0F);

        double distanceSq = medic.squaredDistanceTo(target);

        if (distanceSq <= HEAL_DISTANCE * HEAL_DISTANCE) {

            if (healCooldown <= 0) {
                healTarget();
                healCooldown = HEAL_COOLDOWN_TICKS;
            }

        } else {
            medic.getNavigation().startMovingTo(target, 1.1D);
        }
    }

    @Override
    public void stop() {
        target = null;
        healCooldown = 0;
    }

    // =========================
    // HỒI MÁU
    // =========================
    private void healTarget() {
        if (medic.getWorld().isClient) return;
        if (medic.getAttacker() != null) return;

        float healAmount = getHealAmountFromAxe();
        if (healAmount <= 0) return;

        if (target instanceof SoldierNPCEntity soldier) {
            soldier.requestHeal(healAmount);
        } else {
            target.heal(healAmount);
        }
        // ===== SOUND HEAL =====
        medic.getWorld().playSound(
                null, // null = tất cả player gần đó đều nghe
                target.getX(),
                target.getY(),
                target.getZ(),
                net.minecraft.sound.SoundEvents.ENTITY_PLAYER_LEVELUP,
                net.minecraft.sound.SoundCategory.NEUTRAL,
                0.6F,  // volume
                1.6F   // pitch cao → cảm giác heal
        );
    }


    // =========================
    // HEAL THEO LEVEL RÌU
    // =========================
    private float getHealAmountFromAxe() {

        ItemStack stack = medic.getMainHandStack();
        if (!(stack.getItem() instanceof AxeItem axe)) {
            return 0.0F;
        }

        ToolMaterial material = axe.getMaterial();

        if (material == ToolMaterials.WOOD) return 1.0F;        // 0.5 tim
        if (material == ToolMaterials.STONE) return 2.0F;       // 1 tim
        if (material == ToolMaterials.IRON) return 3.0F;        // 1.5 tim
        if (material == ToolMaterials.DIAMOND) return 4.0F;     // 2 tim
        if (material == ToolMaterials.NETHERITE) return 6.0F;   // 3 tim

        return 0.2F;
    }

    // =========================
    // LỌC ENTITY ĐƯỢC HEAL
    // =========================
    private boolean isValidHealTarget(LivingEntity entity) {

        if (entity == medic) return false;
        if (!entity.isAlive()) return false;
        if (entity.getHealth() >= entity.getMaxHealth()) return false;

        // ❌ Player
        if (entity instanceof PlayerEntity) return false;

        // ❌ Iron Golem
        if (entity instanceof IronGolemEntity) return false;

        // ❌ Mob thù địch
        if (entity instanceof HostileEntity) return false;

        // ❌ Động vật
        if (entity instanceof AnimalEntity) return false;

        // ✔ Villager + NPC custom (Soldier)
        if (entity instanceof SoldierNPCEntity) return true;
        if (entity instanceof VillagerEntity) return true;
        return false;

    }
}
