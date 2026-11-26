package com.example.entity.base;

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

    public void writeNbt(net.minecraft.nbt.NbtCompound nbt) {
        nbt.putInt("NpcHunger", hunger);
        nbt.putInt("NpcFoodTickCooldown", foodTickCooldown);
    }

    public void readNbt(net.minecraft.nbt.NbtCompound nbt) {
        if (nbt.contains("NpcHunger")) hunger = Math.max(0, Math.min(MAX_HUNGER, nbt.getInt("NpcHunger")));
        foodTickCooldown = Math.max(0, Math.min(FOOD_TICK_INTERVAL, nbt.getInt("NpcFoodTickCooldown")));
    }

    private String issue;
    private long issueUntil;
    private long issueAt;

    /** Sự cố gần nhất nếu xảy ra trong {@code windowTicks} tick qua (Sổ quản lý làng giữ lâu hơn tên trên đầu). */
    public String recentIssue(World world, int windowTicks) {
        return issue != null && world.getTime() - issueAt <= windowTicks ? issue : null;
    }

    public int getHunger() {
        return hunger;
    }

    public void report(PathAwareEntity npc, String message) {
        // Sự cố mới (khác sự cố đang hiển thị) → hạt giận dữ trên đầu
        if (!message.equals(issue) || npc.getWorld().getTime() >= issueUntil) {
            com.example.helper.NpcFeedback.problem(npc);
        }
        issue = message;
        issueAt = npc.getWorld().getTime();
        issueUntil = issueAt + 120;
    }

    public static void reportWorker(PathAwareEntity npc, String message) {
        if (npc instanceof WorkerNpcEntity worker) worker.display.report(npc, message);
    }

    public void clearReport() {
        issue = null;
    }

    public void tick(PathAwareEntity npc, SimpleInventory inventory, net.minecraft.entity.ai.goal.GoalSelector goals) {
        World world = npc.getWorld();
        if (world.isClient) return;
        handleFoodSystem(npc, inventory);
        handleGradualHeal(npc);
        if (npc.age % 10 == 0) {
            PlayerEntity player =
                    world.getClosestPlayer(npc, com.example.config.ModConfig.get().statusViewDistance);
            if (player != null) {
                npc.setCustomNameVisible(true);
                visibleTicks = SHOW_NAME_DURATION;
                updateName(npc, inventory, goals);
            }
        }
        if (visibleTicks > 0) {
            visibleTicks--;
        } else {
            npc.setCustomNameVisible(false);
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

    private void updateName(PathAwareEntity npc, SimpleInventory inventory, net.minecraft.entity.ai.goal.GoalSelector goals) {

        int hp = Math.round(npc.getHealth());
        int maxHp = Math.round(npc.getMaxHealth());
        int foodCount = getTotalFoodCount(inventory);
        // Build name text
        String _nameNpc = npc instanceof WorkerNpcEntity worker ? worker.professionName() : "NPC";
        displayStr = _nameNpc + " [" + hp + "/" + maxHp + "] 🍖[" + hunger + "/" + MAX_HUNGER + "] x" + foodCount;

        MutableText displayName = Text.literal(_nameNpc)
                .formatted(Formatting.WHITE)
                .append(Text.literal(" [" + hp + "/" + maxHp + "]")
                        .formatted(Formatting.GREEN))
                .append(Text.literal(" 🍖[" + hunger + "/" + MAX_HUNGER + "]")
                        .formatted(Formatting.GOLD))
                .append(Text.literal(" x" + foodCount)
                        .formatted(Formatting.GOLD));
        String currentIssue = npc.getWorld().getTime() < issueUntil ? issue : null;
        String label = workLabel(npc, goals, currentIssue);
        npc.setCustomName(displayName.append(statusText(npc, label, currentIssue)));

    }

    /** Biểu tượng + màu theo trạng thái: ⚠ sự cố (đỏ), ☾ ngủ, ⚔ chiến đấu, … chờ việc, ⚒ đang làm. */
    private static MutableText statusText(PathAwareEntity npc, String label, String currentIssue) {
        String icon;
        Formatting color;
        if (label.equals(currentIssue)) {
            icon = "⚠";
            color = Formatting.RED;
        } else if (npc.getPose() == net.minecraft.entity.EntityPose.SLEEPING || label.equals("Đang đi ngủ")) {
            icon = "☾";
            color = Formatting.AQUA;
        } else if (npc.getTarget() != null) {
            icon = "⚔";
            color = Formatting.RED;
        } else if (label.equals("Đang chờ việc")) {
            icon = "…";
            color = Formatting.GRAY;
        } else {
            icon = "⚒";
            color = Formatting.YELLOW;
        }
        return Text.literal(" " + icon + " " + label).formatted(color);
    }

    public static String workLabel(PathAwareEntity npc, net.minecraft.entity.ai.goal.GoalSelector goals, String issue) {
        if (npc.getPose() == net.minecraft.entity.EntityPose.SLEEPING) return "Đang ngủ";
        if (npc.getTarget() != null) return "Đang chiến đấu";
        return goals.getRunningGoals().map(entry -> describeGoal(entry.getGoal()))
                .filter(java.util.Objects::nonNull).findFirst()
                .orElseGet(() -> issue != null ? issue : "Đang chờ việc");
    }

    /** Có goal việc (hoặc ăn/ngủ/tránh nguy hiểm) đang chạy — tức NPC không rảnh. */
    public static boolean hasWorkGoal(net.minecraft.entity.ai.goal.GoalSelector goals) {
        return goals.getRunningGoals().anyMatch(entry -> describeGoal(entry.getGoal()) != null);
    }

    private static String describeGoal(net.minecraft.entity.ai.goal.Goal goal) {
        if (goal instanceof com.example.ai.FindAndSleepGoal) return "Đang đi ngủ";
        if (goal instanceof net.minecraft.entity.ai.goal.EscapeDangerGoal) return "Đang tránh nguy hiểm";
        if (goal instanceof com.example.ai.foodChest.FindAndEatFoodGoal) return "Đang đi lấy thức ăn";
        if (goal instanceof com.example.ai.farmer.DepositToChestGoal
                || goal instanceof com.example.ai.lumberjack.DepositWoodToChestGoal
                || goal instanceof com.example.ai.fisherman.DepositFishToChestGoal) return "Đang mang đồ về rương";
        if (goal instanceof com.example.ai.farmer.HarvestCropGoal) return "Đang thu hoạch";
        if (goal instanceof com.example.ai.farmer.PlantSeedGoal) return "Đang gieo hạt";
        if (goal instanceof com.example.ai.lumberjack.ChopTreeGoal) return "Đang chặt cây";
        if (goal instanceof com.example.ai.lumberjack.PlantSaplingGoal) return "Đang trồng mầm";
        if (goal instanceof com.example.ai.miner.MineQuarryGoal) return "Đang đào mỏ";
        if (goal instanceof com.example.ai.fisherman.FindFishingSpotGoal) return "Đang đi tới bờ câu";
        if (goal instanceof com.example.ai.fisherman.FishermanFishGoal) return "Đang câu cá";
        if (goal instanceof com.example.ai.worker.DepositCargoGoal) return "Đang mang đồ về rương";
        if (goal instanceof com.example.ai.rancher.ShearSheepGoal) return "Đang xén lông cừu";
        if (goal instanceof com.example.ai.rancher.BreedAnimalsGoal) return "Đang cho vật nuôi ăn";
        if (goal instanceof com.example.ai.rancher.CollectAnimalDropsGoal) return "Đang nhặt sản phẩm";
        if (goal instanceof com.example.ai.cook.CookFoodGoal) return "Đang nấu ăn";
        if (goal instanceof com.example.ai.builder.BuildStructureGoal build) return build.label();
        if (goal instanceof com.example.ai.soldier.SoldierPatrolGoal) return "Đang đi tuần";
        if (goal instanceof com.example.ai.soldier.CollectLootGoal) return "Đang nhặt chiến lợi phẩm";
        if (goal instanceof com.example.ai.soldier.DepositLootGoal) return "Đang cất chiến lợi phẩm";
        if (goal instanceof com.example.ai.soldier.FollowOwnerLikeGoal
                || goal instanceof com.example.ai.soldier.ReturnToPlayerGoal) return "Đang đi theo chủ";
        return null;
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
    public void feed(int nutrition) {
        hunger = Math.min(MAX_HUNGER, hunger + nutrition);
        eatCooldown = 100;
    }

    public boolean shouldEat() {
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
