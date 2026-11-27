package com.example.ai.fisherman;

import com.example.entity.FishermanNpcEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.EnumSet;
import java.util.Random;

public class FishermanFishGoal extends Goal {
    private static final int DEPOSIT_AFTER_TICKS = 700;

    private final FishermanNpcEntity npc;
    private final Random random = new Random();
    private int fishTimer;
    private int recastDelay;
    private BlockPos targetWater;
    
    private int catchCount = 0;
    private long depositAt;

    public FishermanFishGoal(FishermanNpcEntity npc) {
        this.npc = npc;
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (npc.sleeping.isSleeping()) return false;
        if (isInventoryFull() || (catchCount > 0 && npc.getWorld().getTime() >= depositAt)) requestDeposit();
        if (npc.needsToDeposit() || npc.findNearestChest() == null) return false;
        if (npc.memory.fishingSpot == null) return false;

        // Kểm tra đang đứng ở spot chưa
        double dist = npc.squaredDistanceTo(npc.memory.fishingSpot.getX() + 0.5, npc.memory.fishingSpot.getY(), npc.memory.fishingSpot.getZ() + 0.5);
        if (dist > 4.0) {
            // Đi lạc thì huỷ spot
            npc.memory.fishingSpot = null;
            return false;
        }

        targetWater = npc.findFishingWater(npc.memory.fishingSpot);
        if (targetWater == null) {
            npc.memory.fishingSpot = null;
            return false;
        }

        return true;
    }

    @Override
    public void start() {
        recastDelay = 0;
        castLine();
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    private void castLine() {
        fishTimer = 140 + random.nextInt(61);
        npc.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
        if (npc.getWorld() instanceof ServerWorld world) {
            world.playSound(null, npc.getBlockPos(), SoundEvents.ENTITY_FISHING_BOBBER_THROW,
                    SoundCategory.NEUTRAL, 0.5F, 1.0F);
            // Tia hạt nhẹ (5 điểm) mô phỏng dây câu quăng ra mặt nước, tránh spam gây lag
            if (targetWater != null) {
                Vec3d start = npc.getEyePos().add(0, -0.35, 0);
                Vec3d end = new Vec3d(targetWater.getX() + 0.5, targetWater.getY() + 1.0, targetWater.getZ() + 0.5);
                for (int i = 1; i <= 5; i++) {
                    Vec3d p = start.lerp(end, i / 5.0);
                    world.spawnParticles(ParticleTypes.BUBBLE_POP, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    @Override
    public void tick() {
        if (catchCount > 0 && npc.getWorld().getTime() >= depositAt) {
            requestDeposit();
            return;
        }
        if (recastDelay > 0) {
            if (--recastDelay == 0) castLine();
            return;
        }
        if (targetWater != null) {
            npc.getLookControl().lookAt(targetWater.getX() + 0.5, targetWater.getY() + 0.5, targetWater.getZ() + 0.5);
        }

        fishTimer--;
        if (fishTimer == 15) {
            npc.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
        }

        // Nhấp nháy particle trên mặt nước để giả làm phao
        if (fishTimer % 20 == 0 && npc.getWorld() instanceof ServerWorld sw && targetWater != null) {
            sw.spawnParticles(ParticleTypes.FISHING, targetWater.getX() + 0.5, targetWater.getY() + 1.0, targetWater.getZ() + 0.5, 1, 0.2, 0.0, 0.2, 0.0);
        }

        if (fishTimer <= 0) {
            catchFish();
            if (npc.memory.fishingSpot != null) recastDelay = 12;
        }
    }

    private void catchFish() {
        if (!(npc.getWorld() instanceof ServerWorld sw)) return;
        if (npc.memory.fishingSpot == null || npc.findNearestChest() == null
                || (targetWater = npc.findFishingWater(npc.memory.fishingSpot)) == null
                || npc.squaredDistanceTo(Vec3d.ofBottomCenter(npc.memory.fishingSpot)) > 4) {
            npc.display.report(npc, "Điểm câu không còn hợp lệ, đang tìm lại");
            npc.memory.fishingSpot = null;
            return;
        }

        // Vung tay giật cần
        npc.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
        
        // Âm thanh và particle cá đớp
        sw.playSound(null, targetWater, SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.NEUTRAL, 0.25F, 1.0F + (random.nextFloat() - random.nextFloat()) * 0.4F);
        sw.spawnParticles(ParticleTypes.BUBBLE_POP, targetWater.getX() + 0.5, targetWater.getY() + 1.0, targetWater.getZ() + 0.5, 5, 0.2, 0.0, 0.2, 0.0);
        sw.spawnParticles(ParticleTypes.FISHING, targetWater.getX() + 0.5, targetWater.getY() + 1.0, targetWater.getZ() + 0.5, 10, 0.2, 0.0, 0.2, 0.0);

        // Sinh loot
        LootTable lootTable = sw.getServer().getLootManager().getLootTable(new Identifier("minecraft", "gameplay/fishing/fish"));
        LootContextParameterSet.Builder builder = new LootContextParameterSet.Builder(sw)
                .add(LootContextParameters.ORIGIN, Vec3d.ofCenter(targetWater))
                .add(LootContextParameters.TOOL, npc.getMainHandStack())
                .add(LootContextParameters.THIS_ENTITY, npc);
                
        ObjectArrayList<ItemStack> loot = lootTable.generateLoot(builder.build(LootContextTypes.FISHING));

        if (loot.isEmpty()) return;
        for (ItemStack stack : loot) {
            boolean added = false;
            for (int i = 0; i < npc.getCargo().size(); i++) {
                ItemStack slot = npc.getCargo().getStack(i);
                if (slot.isEmpty()) {
                    npc.getCargo().setStack(i, stack.copy());
                    stack.setCount(0);
                    added = true;
                    break;
                }
                if (ItemStack.canCombine(slot, stack) && slot.getCount() < slot.getMaxCount()) {
                    int canMove = Math.min(stack.getCount(), slot.getMaxCount() - slot.getCount());
                    slot.increment(canMove);
                    stack.decrement(canMove);
                    if (stack.isEmpty()) {
                        added = true;
                        break;
                    }
                }
            }
            if (!added && !stack.isEmpty()) {
                // Rớt ra đất nếu túi đầy
                ItemEntity item = new ItemEntity(sw, targetWater.getX() + 0.5, targetWater.getY() + 1.0, targetWater.getZ() + 0.5, stack);
                double dx = npc.getX() - targetWater.getX() - 0.5;
                double dy = npc.getY() + 1.0 - targetWater.getY() - 1.0;
                double dz = npc.getZ() - targetWater.getZ() - 0.5;
                double mag = Math.sqrt(dx * dx + dy * dy + dz * dz);
                item.setVelocity(dx * 0.1, dy * 0.1 + Math.sqrt(mag) * 0.08, dz * 0.1);
                sw.spawnEntity(item);
            }
        }

        if (catchCount++ == 0) depositAt = sw.getTime() + DEPOSIT_AFTER_TICKS;
        if (catchCount >= 10 || isInventoryFull()) {
            requestDeposit();
        }
        
        npc.memory.resetIdle();
    }

    private void requestDeposit() {
        npc.setNeedsToDeposit(true);
        npc.memory.fishingSpot = null;
        catchCount = 0;
    }
    
    private boolean isInventoryFull() {
        for (int i = 0; i < npc.getCargo().size(); i++) {
            if (npc.getCargo().getStack(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public boolean shouldContinue() {
        if (npc.sleeping.isSleeping()) return false;
        if (npc.needsToDeposit()) return false;
        if (npc.memory.fishingSpot == null) return false;
        return true;
    }
}
