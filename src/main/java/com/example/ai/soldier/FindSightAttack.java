package com.example.ai.soldier;

import com.example.entity.SoldierNPCEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class FindSightAttack {
    private int coolDown = 20; // 1s
    public void findLineOfSight(LivingEntity target, SoldierNPCEntity npc) {
        if (coolDown > 0) {
            coolDown--;
            return; // chưa đủ 1s → thoát
        }
        // Reset cooldown 20 tick (1 giây)
        coolDown = 20;
        Vec3d npcPos = npc.getPos();
        Vec3d targetPos = target.getPos();

        // Vector từ npc đến target
        Vec3d toTarget = targetPos.subtract(npcPos).normalize();

        // Tạo các vector lệch sang trái/phải để tìm góc bắn
        Vec3d perpendicular = new Vec3d(-toTarget.z, 0, toTarget.x);

        // Thử sang trái hoặc phải 1 khoảng 3–6 block
        double offsetDistance = 4.0;

        Vec3d leftPos = npcPos.add(perpendicular.multiply(offsetDistance));
        Vec3d rightPos = npcPos.add(perpendicular.multiply(offsetDistance));
        Path path = npc.getNavigation().findPathTo(leftPos.x, leftPos.y, leftPos.z, 0);
        float rightOfLeft = new Random().nextFloat(10) + 1;
        if(rightOfLeft <= 5)
        {
            // Kiểm tra đường đi tới leftPos
            if (path != null && path.reachesTarget()) {
                npc.getNavigation().startMovingTo(leftPos.x, leftPos.y, leftPos.z, 1.2);
            }
        }else{
            // Nếu trái không đi được → thử phải
            path = npc.getNavigation().findPathTo(rightPos.x, rightPos.y, rightPos.z, 0);
            if (path != null && path.reachesTarget()) {
                npc.getNavigation().startMovingTo(rightPos.x, rightPos.y, rightPos.z, 1.2);
            }
        }
        // Nếu cả hai không đi được → đứng yên (fallback)
    }

}
