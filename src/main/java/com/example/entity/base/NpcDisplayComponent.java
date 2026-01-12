package com.example.entity.base;

import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

public class NpcDisplayComponent {
    private static final int SHOW_NAME_DURATION = 20;
    private static final double VIEW_DISTANCE = 6.0;

    private int visibleTicks = 0;

    public void tick(PathAwareEntity npc) {
        World world = npc.getWorld();
        if (world.isClient) return;

        PlayerEntity player =
                world.getClosestPlayer(npc, VIEW_DISTANCE);

        if (player != null) {
            npc.setCustomNameVisible(true);
            visibleTicks = SHOW_NAME_DURATION;
            updateName(npc);
        } else if (visibleTicks-- <= 0) {
            npc.setCustomNameVisible(false);
        }
    }

    public void updateName(PathAwareEntity npc) {
        int hp = Math.round(npc.getHealth());
        int maxHp = Math.round(npc.getMaxHealth());

        npc.setCustomName(
                Text.literal("NPC")
                        .append(Text.literal(" [" + hp + "/" + maxHp + "]")
                                .formatted(Formatting.GREEN))
        );
    }

}
