package com.example;

import com.example.entity.SoldierNPCEntity;
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


}
