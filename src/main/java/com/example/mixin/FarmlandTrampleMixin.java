package com.example.mixin;

import com.example.entity.SoldierNPCEntity;
import com.example.entity.base.WorkerNpcEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * NPC của mod (nghề + Binh lính) không dẫm hỏng ruộng khi nhảy/rơi xuống đất cày.
 * Vanilla không có hook nên dùng mixin: chỉ bỏ lệnh biến đất cày thành đất,
 * vẫn giữ sát thương rơi (super.onLandedUpon gọi sau đó). Người chơi và mob khác không đổi.
 */
@Mixin(FarmlandBlock.class)
public abstract class FarmlandTrampleMixin {

    @Redirect(method = "onLandedUpon", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/FarmlandBlock;setToDirt(Lnet/minecraft/entity/Entity;Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V"))
    private void datnc$skipNpcTrample(Entity entity, BlockState state, World world, BlockPos pos) {
        if (entity instanceof WorkerNpcEntity || entity instanceof SoldierNPCEntity) return;
        FarmlandBlock.setToDirt(entity, state, world, pos);
    }
}
