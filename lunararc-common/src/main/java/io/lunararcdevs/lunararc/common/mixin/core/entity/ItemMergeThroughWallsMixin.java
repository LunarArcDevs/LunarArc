package io.lunararcdevs.lunararc.common.mixin.core.entity;

import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Paper world option fixes.fix-items-merging-through-walls: two dropped items with a block between them no longer merge. */
@Mixin(ItemEntity.class)
public abstract class ItemMergeThroughWallsMixin {
    @Inject(method = "tryToMerge", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$noMergeThroughWalls(ItemEntity other, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!WorldConfiguration.forLevel(self.level()).tuning.fixItemsMergingThroughWalls) return;
        ClipContext context = new ClipContext(self.position(), other.position(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self);
        if (self.level().clip(context).getType() != HitResult.Type.MISS) ci.cancel();
    }
}
