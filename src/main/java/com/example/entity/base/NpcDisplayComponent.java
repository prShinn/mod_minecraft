package com.example.entity.base;

import com.example.entity.FarmerNpcEntity;
import com.example.entity.LumberjackNpcEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

public class NpcDisplayComponent {
    private static final int SHOW_NAME_DURATION = 20;
    private static final double VIEW_DISTANCE = 6.0;
    private static final int MAX_HUNGER = 20;
    private static final int FOOD_TICK = 1200; // 1p

    private int hunger = MAX_HUNGER;
    private int cooldown = FOOD_TICK;

    private int visibleTicks = 0;
    public String lastDisplayName = "";
    public String displayStr = "";

    public void tick(PathAwareEntity npc, SimpleInventory inventory) {
        World world = npc.getWorld();
        if (world.isClient) return;
        mangeHunger(npc);
        PlayerEntity player =
                world.getClosestPlayer(npc, VIEW_DISTANCE);


        if (player != null) {
            npc.setCustomNameVisible(true);
            visibleTicks = SHOW_NAME_DURATION;
            updateName(npc, inventory);
        } else if (visibleTicks-- <= 0) {
            npc.setCustomNameVisible(false);
        }
    }

    private void mangeHunger(PathAwareEntity npc) {
        if (--cooldown > 0) return;

        cooldown = FOOD_TICK;
        if (hunger > 0) hunger--;
        else npc.damage(npc.getWorld().getDamageSources().starve(), 1);
    }

    public void updateName(PathAwareEntity npc, SimpleInventory inventory) {

        int hp = Math.round(npc.getHealth());
        int maxHp = Math.round(npc.getMaxHealth());
        int foodCount = getTotalFoodCount(inventory);
        // Build name text
        String _nameNpc = "";
        if(npc instanceof FarmerNpcEntity){
            _nameNpc = "Nông dân";
        }else if(npc instanceof LumberjackNpcEntity){
            _nameNpc = "Tiều phu";
        }
        displayStr = _nameNpc + " [" + hp + "/" + maxHp + "] 🍖[" + hunger + "/" + MAX_HUNGER + "] x" + foodCount;

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
}
