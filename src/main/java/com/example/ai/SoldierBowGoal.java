package com.example.ai;

import com.example.entity.SoldierNPCEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

public class SoldierBowGoal extends Goal {
    private final SoldierNPCEntity npc;
    private LivingEntity target;
    private int attackCooldown;
    // Khoảng cách
    private static final double DETECTION_DISTANCE = 30.0; // phát hiện xa
    private static final double MAX_ATTACK_DISTANCE = 15.0;
    private static final double MIN_ATTACK_DISTANCE = 6.0;
    private static final float BASE_INACCURACY = 4.0F;      // Độ lệch cơ bản
    private static final float MAX_INACCURACY = 14.0F;      // Độ lệch tối đa
    private static final float INACCURACY_PER_BLOCK = 0.5F;

    public SoldierBowGoal(SoldierNPCEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public void start() {
        super.start();
        // ✅ Bật animation ngay khi có target
        npc.setCurrentHand(Hand.MAIN_HAND);
    }

    @Override
    public void stop() {
        super.stop();
        // ✅ Tắt animation khi mất target
        npc.clearActiveItem();
    }

    @Override
    public boolean canStart() {
        LivingEntity e = npc.getTarget();
        if (e == null || !e.isAlive()) return false;

        // Chỉ cần có target, chưa cần trong tầm bắn
        double distSq = npc.squaredDistanceTo(e);
        if (distSq > DETECTION_DISTANCE * DETECTION_DISTANCE) return false;

        this.target = e;
        return npc.getMainHandStack().getItem() instanceof BowItem;
    }


    @Override
    public void tick() {
        if (target == null) return;

        npc.getLookControl().lookAt(target, 30.0F, 30.0F);

        double distanceSq = npc.squaredDistanceTo(target);
        double maxDistSq = MAX_ATTACK_DISTANCE * MAX_ATTACK_DISTANCE;
        double minDistSq = MIN_ATTACK_DISTANCE * MIN_ATTACK_DISTANCE;

        if (distanceSq > maxDistSq) {
            // Xa hơn 15 → chạy lại gần
            npc.getNavigation().startMovingTo(target, 1.1);
        } else if (distanceSq < minDistSq) {
            // Quá gần → lùi
            moveAwayFromTarget();
        } else {
            // Trong tầm bắn
            npc.getNavigation().stop();
        }

        // ===== BẮN =====
        if (attackCooldown > 0) {
            attackCooldown--;
            return;
        }

        // Chỉ bắn khi nhìn thấy target
        if (npc.getVisibilityCache().canSee(target)) {
            shootArrow(target);
            attackCooldown = 20; // 1s
        }
    }

    // ===== LÙI RA =====
    private void moveAwayFromTarget() {
        Vec3d dir = npc.getPos().subtract(target.getPos()).normalize();
        Vec3d escapePos = npc.getPos().add(dir.multiply(5));
        Path path = npc.getNavigation().findPathTo(
                escapePos.x, escapePos.y, escapePos.z, 0
        );
        if (path != null && path.reachesTarget()) {
            npc.getNavigation().startMovingTo(
                    escapePos.x,
                    escapePos.y,
                    escapePos.z,
                    1.2
            );
        } else {
            moveToSideOfTarget();
        }
    }

    // ===== ĐI NGANG (MỚI) =====
    private void moveToSideOfTarget() {
        Vec3d toTarget = target.getPos().subtract(npc.getPos()).normalize();

        // Tạo vector vuông góc (đi ngang)
        // Quay 90 độ: (x, z) → (-z, x)
        Vec3d perpendicular = new Vec3d(-toTarget.z, 0, toTarget.x);

        // Random trái hoặc phải
        if (npc.getRandom().nextBoolean()) {
            perpendicular = perpendicular.multiply(-1);
        }

        // Tính điểm đích (đi ngang 6 blocks)
        Vec3d sidePos = npc.getPos().add(perpendicular.multiply(6));

        // Kiểm tra path
        Path path = npc.getNavigation().findPathTo(
                sidePos.x, sidePos.y, sidePos.z, 0
        );

        if (path != null && path.reachesTarget()) {
            // ✅ Đi ngang được
            npc.getNavigation().startMovingTo(
                    sidePos.x, sidePos.y, sidePos.z, 1.1
            );
        } else {
            // ❌ Không đi được → thử đi ngang hướng ngược lại
            perpendicular = perpendicular.multiply(-1);
            sidePos = npc.getPos().add(perpendicular.multiply(6));

            path = npc.getNavigation().findPathTo(
                    sidePos.x, sidePos.y, sidePos.z, 0
            );

            if (path != null && path.reachesTarget()) {
                npc.getNavigation().startMovingTo(
                        sidePos.x, sidePos.y, sidePos.z, 1.1
                );
            }
            // Nếu cả 2 hướng đều không đi được → đứng yên và cố bắn
        }
    }

    private void shootArrow(LivingEntity target) {
        if (npc.getWorld().isClient) return;
        ItemStack bow = npc.getMainHandStack();
        if (!(bow.getItem() instanceof BowItem)) return;
        ArrowEntity arrow = new ArrowEntity(npc.getWorld(), npc); // tạo arrow
        // ===== ÁP DỤNG ENCHANTMENTS =====
        // 1️⃣ POWER (I-V) - Tăng damage
        int powerLevel = EnchantmentHelper.getLevel(Enchantments.POWER, bow);
        if (powerLevel > 0) {
            // Công thức: +0.5 damage mỗi level (+25% per level)
            arrow.setDamage(arrow.getDamage() + (double) powerLevel * 0.5D + 0.5D);
        }
        // 2️⃣ PUNCH (I-II) - Knockback
        int punchLevel = EnchantmentHelper.getLevel(Enchantments.PUNCH, bow);
        if (punchLevel > 0) {
            arrow.setPunch(punchLevel);
        }

        // 3️⃣ FLAME (I) - Arrow bốc cháy
        if (EnchantmentHelper.getLevel(Enchantments.FLAME, bow) > 0) {
            arrow.setOnFireFor(100); // 5 giây
        }

        // 4️⃣ PIERCING (Bonus nếu có) - Xuyên qua entity
        int piercingLevel = EnchantmentHelper.getLevel(Enchantments.PIERCING, bow);
        if (piercingLevel > 0) {
            arrow.setPierceLevel((byte) piercingLevel);
        }
        double dx = target.getX() - npc.getX();
        double dy = target.getBodyY(0.155D) - arrow.getY();
        double dz = target.getZ() - npc.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz) * 0.2F;
        float inaccuracy = calculateInaccuracy(distance);

        arrow.setVelocity(dx, dy + distance, dz, 2.0F, inaccuracy);
        arrow.setOwner(npc);
        // ===== CRITICAL HIT (Random 20%) =====
        if (npc.getRandom().nextFloat() < 0.2F) {
            arrow.setCritical(true);
        }
        npc.getWorld().spawnEntity(arrow);
        npc.swingHand(Hand.MAIN_HAND);
        // Sound effect (optional)
        npc.playSound(
                net.minecraft.sound.SoundEvents.ENTITY_ARROW_SHOOT,
                1.0F,
                1.0F / (npc.getRandom().nextFloat() * 0.4F + 1.2F) * 0.5F
        );
    }

    @Override
    public boolean shouldContinue() {
        return target != null
                && target.isAlive()
                && npc.getMainHandStack().getItem() instanceof BowItem;
    }

    private float calculateInaccuracy(double distance) {
        // Công thức: độ lệch cơ bản + (khoảng cách × hệ số)
        float inaccuracy = BASE_INACCURACY + (float) (distance * INACCURACY_PER_BLOCK);

        // Giới hạn tối đa để không quá tệ
        return Math.min(inaccuracy, MAX_INACCURACY);
    }
}

