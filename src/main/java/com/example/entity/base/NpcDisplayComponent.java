package com.example.entity.base;

import com.example.entity.FarmerNpcEntity;
import com.example.entity.LumberjackNpcEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

public class NpcDisplayComponent {
    private static final int SHOW_NAME_DURATION = 20;
    private static final double VIEW_DISTANCE = 6.0;
    private static final int MAX_HUNGER = 20;
    private static final int FOOD_TICK_INTERVAL = 1200; // 1p

    private int hunger = MAX_HUNGER;
    private int foodTickCooldown = 0;
    private int eatCooldown = 0;

    // ===== HEALING =====
    private float pendingHeal = 0.0F;
    private int healTickCooldown = 0;

    private int visibleTicks = 0;
    public String lastDisplayName = "";
    public String displayStr = "";

    public void tick(PathAwareEntity npc, SimpleInventory inventory) {
        World world = npc.getWorld();
        if (world.isClient) return;
        handleFoodSystem(npc, inventory);
        handleGradualHeal(npc);
        PlayerEntity player =
                world.getClosestPlayer(npc, VIEW_DISTANCE);


        if (player != null) {
            if (!npc.isCustomNameVisible()) {
                npc.setCustomNameVisible(true);
                lastDisplayName = ""; // 🔥 ép sync lại
            }
            visibleTicks = SHOW_NAME_DURATION;
            updateName(npc, inventory);
        } else if (visibleTicks-- <= 0) {
            npc.setCustomNameVisible(false);
            lastDisplayName = "";
        }
    }

    public void requestHeal(float amount) {
        if (amount <= 0) return;
        this.pendingHeal += amount;
    }

    private void handleGradualHeal(PathAwareEntity npc) {
        // ===== ƯU TIÊN HEAL TỪ MEDIC (KHÔNG TỐN HUNGER) =====
        if (pendingHeal > 0 && npc.getHealth() < npc.getMaxHealth()) {
            float heal = Math.min(pendingHeal, npc.getMaxHealth() - npc.getHealth());
            npc.heal(heal);
            pendingHeal -= heal;
            return;
        }
        if (npc.getHealth() >= npc.getMaxHealth()) return;
        if (hunger <= 0) {
            hunger = 0;
            return;
        }
        if (healTickCooldown > 0) {
            healTickCooldown--;
            return;
        }
        float healAmount = 2.0F; // 0.5 tim
        npc.heal(healAmount);
        hunger--;                // 🔥 Heal tốn Hunger
        healTickCooldown = 30;   // 1.5 giây
    }

    private void handleFoodSystem(PathAwareEntity npc, SimpleInventory inventory) {
        // Decrease hunger over time
        if (foodTickCooldown > 0) {
            foodTickCooldown--;
        } else {
            decreaseHunger(npc);
            foodTickCooldown = FOOD_TICK_INTERVAL;
        }
        if (eatCooldown > 0) {
            eatCooldown--;
        }
        if (shouldEat()) {
            this.npcEatFood(npc, inventory);
        }
    }

    private void decreaseHunger(PathAwareEntity npc) {
        if (hunger > 0) {
            hunger--;
        } else {
            // Starve damage
            npc.damage(npc.getWorld().getDamageSources().starve(), 1.0F);
        }
    }

    public void updateName(PathAwareEntity npc, SimpleInventory inventory) {

        int hp = Math.round(npc.getHealth());
        int maxHp = Math.round(npc.getMaxHealth());
        int foodCount = getTotalFoodCount(inventory);
        // Build name text
        String _nameNpc = "NPC";
        if (npc instanceof FarmerNpcEntity) {
            _nameNpc = "Nông dân";
        } else if (npc instanceof LumberjackNpcEntity) {
            _nameNpc = "Tiều phu";
        }
        displayStr = _nameNpc + " [" + hp + "/" + maxHp + "] 🍖[" + hunger + "/" + MAX_HUNGER + "] x" + foodCount;
        if (displayStr.equals(lastDisplayName)) {
            return;
        }
        lastDisplayName = displayStr;
        MutableText displayName = Text.literal(_nameNpc)
                .formatted(Formatting.WHITE)
                .append(Text.literal(" [" + hp + "/" + maxHp + "]")
                        .formatted(Formatting.GREEN))
                .append(Text.literal(" 🍖[" + hunger + "/" + MAX_HUNGER + "]")
                        .formatted(Formatting.GOLD))
                .append(Text.literal(" x" + foodCount)
                        .formatted(Formatting.GOLD));
        npc.setCustomName(displayName);

    }

    public int getTotalFoodCount(SimpleInventory foodInventory) {
        int total = 0;
        for (int i = 0; i < foodInventory.size(); i++) {
            ItemStack stack = foodInventory.getStack(i);
            if (!stack.isEmpty() && stack.isFood()) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private boolean shouldEat() {
        // Chỉ ăn khi food thấp hơn 50%
        return eatCooldown <= 0 && hunger <= MAX_HUNGER * 0.5f;
    }

    private void npcEatFood(PathAwareEntity npc, SimpleInventory foodInventory) {
        if (npc.getWorld().isClient) return; // Chỉ server xử lý
        for (int i = 0; i < foodInventory.size(); i++) {
            ItemStack stack = foodInventory.getStack(i);
            if (stack.isEmpty() || !stack.isFood()) continue;
            FoodComponent food = stack.getItem().getFoodComponent();
            if (food == null) continue;

            // Tăng food/hunger
            hunger = Math.min(MAX_HUNGER, hunger + food.getHunger());

            stack.decrement(1);
            eatCooldown = 100; // 5 giây cooldown
            return;
        }
    }
}
