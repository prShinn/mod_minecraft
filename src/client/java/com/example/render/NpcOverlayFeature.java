package com.example.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;

/**
 * Vẽ thêm các lớp quần áo của dân làng vanilla (biome + nghề) lên NPC, giống VillagerClothingFeatureRenderer,
 * để nhận ra nghề bằng mắt. Dùng lại đúng model của renderer nên khớp UV villager/zombie_villager.
 */
public class NpcOverlayFeature<T extends MobEntity, M extends BipedEntityModel<T>> extends FeatureRenderer<T, M> {
    private final Identifier[] textures;

    public NpcOverlayFeature(FeatureRendererContext<T, M> context, Identifier... textures) {
        super(context);
        this.textures = textures;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, T entity,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress,
                       float headYaw, float headPitch) {
        if (entity.isInvisible()) return;
        for (Identifier texture : textures) {
            renderModel(getContextModel(), texture, matrices, vertexConsumers, light, entity, 1.0F, 1.0F, 1.0F);
        }
    }
}
