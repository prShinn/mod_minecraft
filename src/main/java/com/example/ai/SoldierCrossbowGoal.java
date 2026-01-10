package com.example.ai;

import com.example.entity.SoldierNPCEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;

public class SoldierCrossbowGoal extends Goal {
    private final SoldierNPCEntity npc;
    private FindSightAttack mapAI;
    private LivingEntity target;
    private int attackCooldown;
    private int chargingTime;
    private boolean isCharged = false;

    private static final double DETECTION_DISTANCE = 40.0;
    private static final double MAX_ATTACK_DISTANCE = 25.0;
    private static final double MIN_ATTACK_DISTANCE = 8.0;

    private static final float BASE_INACCURACY = 2.0F;
    private static final float MAX_INACCURACY = 6.0F;
    private static final float INACCURACY_PER_BLOCK = 0.2F;

    private static final int BASE_CHARGE_TIME = 25;

    public SoldierCrossbowGoal(SoldierNPCEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public void start() {
        super.start();
        chargingTime = 0;
        isCharged = false;
        npc.setCurrentHand(Hand.MAIN_HAND);
    }

    @Override
    public void stop() {
        super.stop();
        npc.clearActiveItem();
        isCharged = false;
        chargingTime = 0;
    }

    @Override
    public boolean canStart() {
        LivingEntity e = npc.getTarget();
        if (e == null || !e.isAlive()) return false;

        double distSq = npc.squaredDistanceTo(e);
        if (distSq > DETECTION_DISTANCE * DETECTION_DISTANCE) return false;

        this.target = e;
        return npc.getMainHandStack().getItem() instanceof CrossbowItem;
    }

    @Override
    public void tick() {
        if (target == null) return;

        npc.getLookControl().lookAt(target, 30.0F, 30.0F);

        double distanceSq = npc.squaredDistanceTo(target);
        double maxDistSq = MAX_ATTACK_DISTANCE * MAX_ATTACK_DISTANCE;
        double minDistSq = MIN_ATTACK_DISTANCE * MIN_ATTACK_DISTANCE;

        // DI CHUYỂN
        if (distanceSq > maxDistSq) {
            npc.getNavigation().startMovingTo(target, 1.1);
        } else if (distanceSq < minDistSq) {
            moveAwayFromTarget();
        } else {
            npc.getNavigation().stop();
        }

        // XỬ LÝ NẠP ĐẠN & BẮN
        if (attackCooldown > 0) {
            attackCooldown--;
            return;
        }
        if (!npc.getVisibilityCache().canSee(target)) {
            mapAI.findLineOfSight(target, npc); // không nhìn thấy tìm đường khác
            return;
        }
        ItemStack crossbow = npc.getMainHandStack();

        if (isCharged || CrossbowItem.isCharged(crossbow)) {
            shootCrossbowBolt(target);
            isCharged = false;
            attackCooldown = getReloadTime();
            chargingTime = 0;
        } else {
            chargingTime++;
            int requiredChargeTime = getChargeTime();

            if (chargingTime >= requiredChargeTime) {
                isCharged = true;
                npc.playSound(SoundEvents.ITEM_CROSSBOW_LOADING_END, 1.0F, 1.0F);
            } else if (chargingTime == 1) {
                npc.playSound(SoundEvents.ITEM_CROSSBOW_LOADING_START, 1.0F, 1.0F);
            } else if (chargingTime % 5 == 0) {
                npc.playSound(SoundEvents.ITEM_CROSSBOW_LOADING_MIDDLE, 1.0F, 1.0F);
            }
        }
    }

    private void moveAwayFromTarget() {
        Vec3d dir = npc.getPos().subtract(target.getPos()).normalize();
        Vec3d escapePos = npc.getPos().add(dir.multiply(6));
        Path path = npc.getNavigation().findPathTo(escapePos.x, escapePos.y, escapePos.z, 0);
        if (path != null && path.reachesTarget()) {
            npc.getNavigation().startMovingTo(escapePos.x, escapePos.y, escapePos.z, 1.3);
        } else {
            moveToSideOfTarget();
        }
    }

    private void moveToSideOfTarget() {
        Vec3d toTarget = target.getPos().subtract(npc.getPos()).normalize();
        Vec3d perpendicular = new Vec3d(-toTarget.z, 0, toTarget.x);
        if (npc.getRandom().nextBoolean()) perpendicular = perpendicular.multiply(-1);
        Vec3d sidePos = npc.getPos().add(perpendicular.multiply(8));

        Path path = npc.getNavigation().findPathTo(sidePos.x, sidePos.y, sidePos.z, 0);
        if (path != null && path.reachesTarget()) {
            npc.getNavigation().startMovingTo(sidePos.x, sidePos.y, sidePos.z, 1.2);
        } else {
            perpendicular = perpendicular.multiply(-1);
            sidePos = npc.getPos().add(perpendicular.multiply(8));
            path = npc.getNavigation().findPathTo(sidePos.x, sidePos.y, sidePos.z, 0);
            if (path != null && path.reachesTarget()) {
                npc.getNavigation().startMovingTo(sidePos.x, sidePos.y, sidePos.z, 1.2);
            }
        }
    }

    private void shootCrossbowBolt(LivingEntity target) {
        if (npc.getWorld().isClient) return;

        ItemStack crossbow = npc.getMainHandStack();
        if (!(crossbow.getItem() instanceof CrossbowItem)) return;

        // Tạo arrow trực tiếp từ ArrowItem
        ArrowItem arrowItem = (ArrowItem) Items.ARROW;
        PersistentProjectileEntity arrow = arrowItem.createArrow(npc.getWorld(), Items.ARROW.getDefaultStack(), npc);

        arrow.setDamage(5.5F);

        int piercingLevel = EnchantmentHelper.getLevel(Enchantments.PIERCING, crossbow);
        if (piercingLevel > 0) arrow.setPierceLevel((byte) piercingLevel);

        int multishotLevel = EnchantmentHelper.getLevel(Enchantments.MULTISHOT, crossbow);
        boolean hasMultishot = multishotLevel > 0;

        int powerLevel = EnchantmentHelper.getLevel(Enchantments.POWER, crossbow);
        if (powerLevel > 0) arrow.setDamage(arrow.getDamage() + powerLevel * 0.5 + 0.5);

        if (EnchantmentHelper.getLevel(Enchantments.FLAME, crossbow) > 0) {
            arrow.setOnFireFor(100);
        }

        int punchLevel = EnchantmentHelper.getLevel(Enchantments.PUNCH, crossbow);
        if (punchLevel > 0) arrow.setPunch(punchLevel);

        // Tính toán vector
        double dx = target.getX() - npc.getX();
        double dy = target.getBodyY(0.33D) - arrow.getY();
        double dz = target.getZ() - npc.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        float inaccuracy = calculateInaccuracy(distance);
        double heightAdjust = distance * 0.05D;

        arrow.setVelocity(dx, dy + heightAdjust, dz, 3.15F, inaccuracy);
        arrow.setOwner(npc);
        if (npc.getRandom().nextFloat() < 0.3F) arrow.setCritical(true);

        npc.getWorld().spawnEntity(arrow);

        if (hasMultishot) {
            shootMultishotArrow(target, -10.0F, distance, heightAdjust, inaccuracy);
            shootMultishotArrow(target, 10.0F, distance, heightAdjust, inaccuracy);
        }

        npc.swingHand(Hand.MAIN_HAND);
        npc.playSound(SoundEvents.ITEM_CROSSBOW_SHOOT, 1.0F,
                1.0F / (npc.getRandom().nextFloat() * 0.4F + 1.2F) + 0.5F);
    }

    private void shootMultishotArrow(LivingEntity target, float angleOffset,
                                     double distance, double heightAdjust, float inaccuracy) {
        ArrowItem arrowItem = (ArrowItem) Items.ARROW;
        PersistentProjectileEntity arrow = arrowItem.createArrow(npc.getWorld(), Items.ARROW.getDefaultStack(), npc);
        arrow.setDamage(2.4);

        double radians = Math.toRadians(angleOffset);
        Vec3d lookVec = npc.getRotationVec(1.0F);
        double dx = lookVec.x * Math.cos(radians) - lookVec.z * Math.sin(radians);
        double dz = lookVec.x * Math.sin(radians) + lookVec.z * Math.cos(radians);
        double dy = target.getBodyY(0.33D) - arrow.getY();

        arrow.setVelocity(dx, dy + heightAdjust, dz, 3.15F, inaccuracy + 2.0F);
        arrow.setOwner(npc);

        npc.getWorld().spawnEntity(arrow);
    }

    private int getChargeTime() {
        ItemStack crossbow = npc.getMainHandStack();
        int quickChargeLevel = EnchantmentHelper.getLevel(Enchantments.QUICK_CHARGE, crossbow);
        return Math.max(5, BASE_CHARGE_TIME - quickChargeLevel * 5);
    }

    private int getReloadTime() {
        ItemStack crossbow = npc.getMainHandStack();
        int quickChargeLevel = EnchantmentHelper.getLevel(Enchantments.QUICK_CHARGE, crossbow);
        return Math.max(10, 40 - quickChargeLevel * 8);
    }

    @Override
    public boolean shouldContinue() {
        return target != null && target.isAlive() && npc.getMainHandStack().getItem() instanceof CrossbowItem;
    }

    private float calculateInaccuracy(double distance) {
        return Math.min(BASE_INACCURACY + (float) (distance * INACCURACY_PER_BLOCK), MAX_INACCURACY);
    }
}
