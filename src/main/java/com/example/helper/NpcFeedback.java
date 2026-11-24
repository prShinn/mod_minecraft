package com.example.helper;

import com.example.config.ModConfig;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/** Hiệu ứng phản hồi trực quan (hạt + âm thanh) cho NPC làm việc. */
public final class NpcFeedback {
    private NpcFeedback() {
    }

    /** NPC vừa hoàn thành một việc: hạt xanh trên đầu + tiếng dân làng đồng ý. */
    public static void success(Entity npc) {
        if (!ModConfig.get().feedbackEffects || !(npc.getWorld() instanceof ServerWorld world)) return;
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                npc.getX(), npc.getY() + npc.getHeight() + 0.3, npc.getZ(), 6, 0.3, 0.2, 0.3, 0.0);
        world.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 0.5F, 1.0F);
    }

    /** NPC gặp sự cố mới (rương đầy, không có đường...): hạt giận dữ trên đầu. */
    public static void problem(Entity npc) {
        if (!ModConfig.get().feedbackEffects || !(npc.getWorld() instanceof ServerWorld world)) return;
        world.spawnParticles(ParticleTypes.ANGRY_VILLAGER,
                npc.getX(), npc.getY() + npc.getHeight() + 0.3, npc.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
    }
}
