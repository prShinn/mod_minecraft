package com.example.ai.soldier;

import com.example.entity.FarmerNpcEntity;
import com.example.entity.LumberjackNpcEntity;
import com.example.entity.SoldierNPCEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
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

    private static final double SEARCH_RANGE = 15.0D;
    private static final double HEAL_DISTANCE = 3.0D;
    private static final int HEAL_COOLDOWN_TICKS = 60; // 3s

    private int healCooldown = 0;
    /** Ngưỡng máu cần chữa — canStart và shouldContinue PHẢI dùng chung, nếu không goal bật/tắt liên tục. */
    private static final float HEAL_THRESHOLD = 0.7F;
    private static final int SCAN_INTERVAL_TICKS = 20;
    private int scanCooldown = 0;

    public HealAllyGoal(PathAwareEntity medic) {
        this.medic = medic;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    // =========================
    // TÌM MỤC TIÊU
    // =========================
    @Override
    public boolean canStart() {
        if (scanCooldown-- > 0) return false;
        scanCooldown = SCAN_INTERVAL_TICKS;

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
                && target.getHealth() < target.getMaxHealth() * HEAL_THRESHOLD;
    }

    @Override
    public void start() {
        medic.getNavigation().startMovingTo(target, 1.2D);
    }

    @Override
    public void tick() {
        if (target == null) return;

        if (healCooldown > 0) {
            healCooldown -= getCooldownReduction();
            if (healCooldown < 0) healCooldown = 0;
        }

        medic.getLookControl().lookAt(target, 30.0F, 30.0F);

        double distanceSq = medic.squaredDistanceTo(target);

        if (distanceSq <= HEAL_DISTANCE * HEAL_DISTANCE) {
            if (healCooldown <= 0) {
                healTarget();
                healCooldown = HEAL_COOLDOWN_TICKS;
            }
        } else {
            medic.getNavigation().startMovingTo(target, 1.2D);
        }
    }

    @Override
    public void stop() {
        medic.getNavigation().stop();
        target = null;
        healCooldown = 0;
    }

    // =========================
    // HỒI MÁU
    // =========================
    private void healTarget() {
        if (medic.getWorld().isClient) return;
        if (medic.getAttacker() != null) return;

        float healAmount = getFinalHealAmount();
        if (healAmount <= 0) return;

        if (target instanceof SoldierNPCEntity soldier) {
            soldier.requestHeal(healAmount);
        } else if (target instanceof com.example.entity.base.WorkerNpcEntity worker) {
            worker.display.requestHeal(healAmount); // mọi NPC nghề hồi máu dần qua display
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

    private float getFinalHealAmount() {
        ItemStack stack = medic.getMainHandStack();
        if (!(stack.getItem() instanceof AxeItem axe)) return 0f;

        float baseHeal = getBaseHealFromAxe(axe);
        if (baseHeal <= 0) return 0f;

        float bonusHeal = getEnchantHealBonus(stack);
        float totalHeal = baseHeal + bonusHeal;

        return Math.min(totalHeal, 8.0F); // 🔒 cap tối đa 4 tim
    }

    private float getEnchantHealBonus(ItemStack stack) {

        float bonus = 0f;

        // 1️⃣ UNBREAKING → +20% heal mỗi level
        int unbreaking = EnchantmentHelper.getLevel(Enchantments.UNBREAKING, stack);
        if (unbreaking > 0) {
            bonus += unbreaking * 0.5f;
        }

        // 2️⃣ MENDING → +1 tim mỗi level
        int mending = EnchantmentHelper.getLevel(Enchantments.MENDING, stack);
        if (mending > 0) {
            bonus += mending * 2.0f;
        }

        return bonus;
    }

    private float getCooldownReduction() {
        ItemStack stack = medic.getMainHandStack();
        int efficiency = EnchantmentHelper.getLevel(Enchantments.EFFICIENCY, stack);

        return efficiency > 1 ? efficiency : efficiency == 1 ? 1.5F : 1;
    }

    // =========================
    // HEAL THEO LEVEL RÌU
    // =========================
    private float getBaseHealFromAxe(AxeItem axe) {
        ToolMaterial material = axe.getMaterial();

        if (material == ToolMaterials.WOOD) return 1.0F;
        if (material == ToolMaterials.STONE) return 1.5F;
        if (material == ToolMaterials.IRON) return 2.5F;
        if (material == ToolMaterials.DIAMOND) return 3.0F;
        if (material == ToolMaterials.NETHERITE) return 4.0F;

        return 0.5F;
    }


    // =========================
    // LỌC ENTITY ĐƯỢC HEAL
    // =========================
    private boolean isValidHealTarget(LivingEntity entity) {

        if (entity == medic) return false;
        if (!entity.isAlive()) return false;
        if (entity.getHealth() >= entity.getMaxHealth() * HEAL_THRESHOLD) return false;

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
        if (entity instanceof com.example.entity.base.WorkerNpcEntity) return true;
        if (entity instanceof VillagerEntity) return true;
        return false;

    }
}
