package com.example.entity.base;

import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.nbt.NbtCompound;

public class NpcFoodComponent {

    private static final int MAX_HUNGER = 20;
    private static final int FOOD_TICK = 1200;

    private int hunger = MAX_HUNGER;
    private int cooldown = FOOD_TICK;

    public void tick(PathAwareEntity npc) {
        if (--cooldown > 0) return;

        cooldown = FOOD_TICK;
        if (hunger > 0) hunger--;
        else npc.damage(npc.getWorld().getDamageSources().starve(), 1);
    }

    public void writeNbt(NbtCompound nbt) {
        nbt.putInt("Hunger", hunger);
    }

    public void readNbt(NbtCompound nbt) {
        hunger = nbt.getInt("Hunger");
    }
}
