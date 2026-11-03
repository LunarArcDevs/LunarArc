package io.lunararcdevs.lunararc.common.mixin.core.entity;

import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Paper world option fixes.falling-block-height-nerf: the entity vanishes above the configured height (0 means off). */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockHeightNerfMixin {
    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void lunararc$heightNerf(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        int limit = WorldConfiguration.forLevel(self.level()).tuning.fallingBlockHeightNerf;
        if (limit != 0 && self.getY() > limit) self.discard();
    }
}
