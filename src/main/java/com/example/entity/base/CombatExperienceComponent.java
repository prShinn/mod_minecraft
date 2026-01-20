package com.example.entity.base;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.nbt.NbtCompound;

public class CombatExperienceComponent {
    private int level = 1;
    private double experience = 0;
    private double maxExperience = 100;

    private static final double XP_MULTIPLIER = 1.5;
    private static final int BASE_XP = 100;

    // HP stats
    private double baseMaxHealth = 20.0;
    private double healthPerLevel = 1.5;

    // Damage stats
    private double baseDamage = 2.0;
    private double damagePerLevel = 0.3;

    public CombatExperienceComponent() {
        recalculateRequiredXp();
    }

    /**
     * Thêm kinh nghiệm khi NPC giết một mob
     */
    public void addExperience(Entity killedEntity, double damageDealt) {
        if (killedEntity instanceof HostileEntity) {
            double xpGain = calculateXpReward(killedEntity, damageDealt);
            experience += xpGain;

            // Check level up
            while (experience >= maxExperience) {
                levelUp();
            }
        }
    }

    /**
     * Tính XP thưởng dựa trên loại mob và damage dealt
     */
    private double calculateXpReward(Entity entity, double damageDealt) {
        double baseXp = 10.0;

        if (entity instanceof HostileEntity hostile) {
            baseXp = hostile.getMaxHealth() * 1.5;
        }

        // Damage bonus (max 2x)
        double damageBonus = Math.min(damageDealt / 10.0, 2.0);

        return baseXp * damageBonus;
    }

    /**
     * Tăng cấp độ
     */
    private void levelUp() {
        level++;
        experience -= maxExperience;
        recalculateRequiredXp();

        System.out.println("✨ Soldier tăng lên cấp " + level);
    }

    /**
     * Tính XP cần để level up tiếp theo
     */
    private void recalculateRequiredXp() {
        maxExperience = BASE_XP * Math.pow(XP_MULTIPLIER, level - 1);
    }

    /**
     * Lấy MaxHealth hiện tại dựa trên level
     */
    public double getCurrentMaxHealth() {
        return baseMaxHealth + (healthPerLevel * (level - 1));
    }

    /**
     * Lấy Damage hiện tại dựa trên level
     */
    public double getCurrentDamage() {
        return baseDamage + (damagePerLevel * (level - 1));
    }

    public int getLevel() {
        return level;
    }

    public double getExperience() {
        return experience;
    }

    public double getMaxExperience() {
        return maxExperience;
    }

    /**
     * Lưu vào NBT
     */
    public void writeToNbt(NbtCompound nbt) {
        nbt.putInt("CombatLevel", level);
        nbt.putDouble("CombatExp", experience);
    }

    /**
     * Đọc từ NBT
     */
    public void readFromNbt(NbtCompound nbt) {
        if (nbt.contains("CombatLevel")) {
            level = nbt.getInt("CombatLevel");
        }
        if (nbt.contains("CombatExp")) {
            experience = nbt.getDouble("CombatExp");
        }
        recalculateRequiredXp();
    }
}

