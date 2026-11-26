package com.example;

import com.example.registry.ModEntities;
import com.example.render.NpcOverlayFeature;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;

/**
 * Renderer cho mọi NPC: thân dân làng + lớp áo biome đồng bằng + lớp áo nghề vanilla
 * (để nhận ra nghề bằng mắt) + giáp. BipedEntityRenderer tự vẽ đồ cầm tay (HeldItemFeatureRenderer).
 */
public class ModEntityRenderers {
    /** villager.png đã vá 4px cuối tay (v34..37) — model zombie villager có tay dài 12px, ảnh gốc chỉ vẽ 8px. */
    private static final Identifier BASE_TEXTURE = new Identifier("datnc_mod", "textures/entity/npc_base.png");
    private static final Identifier BIOME_TEXTURE = new Identifier("minecraft", "textures/entity/villager/type/plains.png");

    public static void register() {
        // Áo lính riêng (mũ vải, giáp da) thay áo Thợ rèn vũ khí có miếng bịt mắt
        registerNpc(ModEntities.SOLDIER_NPC, new Identifier("datnc_mod", "textures/entity/soldier.png"));
        registerNpc(ModEntities.FARMER_NPC, "farmer");
        registerNpc(ModEntities.LUMBERJACK_NPC, "fletcher");
        registerNpc(ModEntities.MINER_NPC, "toolsmith");
        registerNpc(ModEntities.FISHERMAN_NPC, "fisherman");
        registerNpc(ModEntities.RANCHER_NPC, "shepherd");
        registerNpc(ModEntities.COOK_NPC, "butcher");
        registerNpc(ModEntities.BUILDER_NPC, "mason");
    }

    /** @param profession tên file trong textures/entity/villager/profession của vanilla. */
    private static <T extends MobEntity> void registerNpc(EntityType<T> type, String profession) {
        registerNpc(type, new Identifier("minecraft", "textures/entity/villager/profession/" + profession + ".png"));
    }

    /** @param professionTexture lớp áo nghề 64×64 theo UV dân làng, vẽ trên thân + áo biome, dưới giáp. */
    private static <T extends MobEntity> void registerNpc(EntityType<T> type, Identifier professionTexture) {
        EntityRendererRegistry.register(
                type,
                ctx -> new BipedEntityRenderer<T, BipedEntityModel<T>>(
                        ctx,
                        new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE_VILLAGER)),
                        0.5f
                ) {
                    {
                        // Áo phải vẽ trước giáp để giáp nằm trên cùng
                        this.addFeature(new NpcOverlayFeature<>(this, BIOME_TEXTURE, professionTexture));
                        this.addFeature(new ArmorFeatureRenderer<>(
                                this,
                                new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE_VILLAGER_INNER_ARMOR)),
                                new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE_VILLAGER_OUTER_ARMOR)),
                                ctx.getModelManager()
                        ));
                    }

                    @Override
                    public Identifier getTexture(T entity) {
                        return BASE_TEXTURE;
                    }
                }
        );
    }
}
