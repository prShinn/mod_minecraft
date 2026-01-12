package com.example.ai.farmer;

import com.example.entity.FarmerNpcEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;

public class ReturnToFarmGoal extends Goal {

    private final FarmerNpcEntity npc;

    public ReturnToFarmGoal(FarmerNpcEntity npc) {
        this.npc = npc;
    }

    @Override
    public boolean canStart() {
        return npc.memory.shouldReturnToFarm()
                && (npc.memory.lastFarmPos != null || npc.memory.lastChestPos != null);
    }

    @Override
    public void start() {
        BlockPos target = npc.memory.lastFarmPos != null
                ? npc.memory.lastFarmPos
                : npc.memory.lastChestPos;

        npc.getNavigation().startMovingTo(
                target.getX(), target.getY(), target.getZ(), 1.0);
        npc.memory.resetIdle();
    }
}
