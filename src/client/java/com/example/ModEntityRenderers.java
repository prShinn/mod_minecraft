package com.example;

import com.example.entity.FarmerNpcEntity;
import com.example.entity.LumberjackNpcEntity;
import com.example.entity.FishermanNpcEntity;
import com.example.entity.MinerNpcEntity;
import com.example.entity.SoldierNPCEntity;
import com.example.entity.base.WorkerNpcEntity;
import net.minecraft.entity.EntityType;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;

import com.example.registry.ModEntities;
import net.minecraft.util.Identifier;
public class ModEntityRenderers {
    public static void register() {

        EntityRendererRegistry.register(
                ModEntities.SOLDIER_NPC,
                ctx -> new BipedEntityRenderer<
                        SoldierNPCEntity,
                        BipedEntityModel<SoldierNPCEntity>
                        >(
                        ctx,
                        new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE_VILLAGER)),
                        0.5f
                ) {

                    @Override
                    public Identifier getTexture(SoldierNPCEntity entity) {
                        return new Identifier(
                                "minecraft",
                                "textures/entity/villager/villager.png"
                        );
                    }

                    {
                        this.addFeature(new ArmorFeatureRenderer<>(
                                this,
                                new BipedEntityModel<>(ctx.getPart(
                                        EntityModelLayers.ZOMBIE_VILLAGER_INNER_ARMOR)),
                                new BipedEntityModel<>(ctx.getPart(
                                        EntityModelLayers.ZOMBIE_VILLAGER_OUTER_ARMOR)),
                                ctx.getModelManager()
                        ));
                    }
                }
        );
        EntityRendererRegistry.register(
                ModEntities.FARMER_NPC,
                ctx -> new BipedEntityRenderer<
                        FarmerNpcEntity,
                        BipedEntityModel<FarmerNpcEntity>
                        >(
                        ctx,
                        new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE_VILLAGER)),
                        0.5f
                ) {

                    @Override
                    public Identifier getTexture(FarmerNpcEntity entity) {
                        return new Identifier(
                                "minecraft",
                                "textures/entity/villager/villager.png"
                        );
                    }

                    {
                        this.addFeature(new ArmorFeatureRenderer<>(
                                this,
                                new BipedEntityModel<>(ctx.getPart(
                                        EntityModelLayers.ZOMBIE_VILLAGER_INNER_ARMOR)),
                                new BipedEntityModel<>(ctx.getPart(
                                        EntityModelLayers.ZOMBIE_VILLAGER_OUTER_ARMOR)),
                                ctx.getModelManager()
                        ));
                    }
                }
        );

        EntityRendererRegistry.register(
                ModEntities.LUMBERJACK_NPC,
                ctx -> new BipedEntityRenderer<
                        LumberjackNpcEntity,
                        BipedEntityModel<LumberjackNpcEntity>
                        >(
                        ctx,
                        new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE_VILLAGER)),
                        0.5f
                ) {

                    @Override
                    public Identifier getTexture(LumberjackNpcEntity entity) {
                        return new Identifier(
                                "minecraft",
                                "textures/entity/villager/villager.png"
                        );
                    }

                    {
                        this.addFeature(new ArmorFeatureRenderer<>(
                                this,
                                new BipedEntityModel<>(ctx.getPart(
                                        EntityModelLayers.ZOMBIE_VILLAGER_INNER_ARMOR)),
                                new BipedEntityModel<>(ctx.getPart(
                                        EntityModelLayers.ZOMBIE_VILLAGER_OUTER_ARMOR)),
                                ctx.getModelManager()
                        ));
                    }
                }
        );

        EntityRendererRegistry.register(
                ModEntities.MINER_NPC,
                ctx -> new BipedEntityRenderer<
                        MinerNpcEntity,
                        BipedEntityModel<MinerNpcEntity>
                        >(
                        ctx,
                        new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE_VILLAGER)),
                        0.5f
                ) {

                    @Override
                    public Identifier getTexture(MinerNpcEntity entity) {
                        return new Identifier(
                                "minecraft",
                                "textures/entity/villager/villager.png"
                        );
                    }

                    {
                        this.addFeature(new ArmorFeatureRenderer<>(
                                this,
                                new BipedEntityModel<>(ctx.getPart(
                                        EntityModelLayers.ZOMBIE_VILLAGER_INNER_ARMOR)),
                                new BipedEntityModel<>(ctx.getPart(
                                        EntityModelLayers.ZOMBIE_VILLAGER_OUTER_ARMOR)),
                                ctx.getModelManager()
                        ));
                    }
                }
        );
        // ===== FISHERMAN NPC =====
        EntityRendererRegistry.register(
                ModEntities.FISHERMAN_NPC,
                ctx -> new BipedEntityRenderer<
                        FishermanNpcEntity,
                        BipedEntityModel<FishermanNpcEntity>
                        >(
                        ctx,
                        new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE_VILLAGER)),
                        0.5f
                ) {
                    @Override
                    public Identifier getTexture(FishermanNpcEntity entity) {
                        return new Identifier(
                                "minecraft",
                                "textures/entity/villager/villager.png"
                        );
                    }

                    {
                        this.addFeature(new ArmorFeatureRenderer<>(
                                this,
                                new BipedEntityModel<>(ctx.getPart(
                                        EntityModelLayers.ZOMBIE_VILLAGER_INNER_ARMOR)),
                                new BipedEntityModel<>(ctx.getPart(
                                        EntityModelLayers.ZOMBIE_VILLAGER_OUTER_ARMOR)),
                                ctx.getModelManager()
                        ));
                    }
                }
        );

        // ===== RANCHER & COOK NPC =====
        registerWorker(ModEntities.RANCHER_NPC);
        registerWorker(ModEntities.COOK_NPC);

//        EntityRendererRegistry.register(
//                ModEntities.SOLDIER_NPC,
//                ctx -> new BipedEntityRenderer<>(
//                        ctx,
//                        new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE_VILLAGER)),
//                        0.5f
//                ) {
//
//                    @Override
//                    public Identifier getTexture(SoldierNPCEntity entity) {
//                        return new Identifier("minecraft", "textures/entity/villager/villager.png");
////                        return new Identifier("minecraft", "textures/entity/villager/villager.png");
//                    }
//
//                }
//        );

    }

    /** Renderer dân làng + giáp dùng chung cho các NPC kế thừa WorkerNpcEntity. */
    private static <T extends WorkerNpcEntity> void registerWorker(EntityType<T> type) {
        EntityRendererRegistry.register(
                type,
                ctx -> new BipedEntityRenderer<T, BipedEntityModel<T>>(
                        ctx,
                        new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE_VILLAGER)),
                        0.5f
                ) {
                    @Override
                    public Identifier getTexture(T entity) {
                        return new Identifier("minecraft", "textures/entity/villager/villager.png");
                    }

                    {
                        this.addFeature(new ArmorFeatureRenderer<>(
                                this,
                                new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE_VILLAGER_INNER_ARMOR)),
                                new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE_VILLAGER_OUTER_ARMOR)),
                                ctx.getModelManager()
                        ));
                    }
                }
        );
    }
}
