package io.lunararcdevs.lunararc.common.mixin.core.entity;

import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Paper world option environment.void-damage-min-build-height-offset: how far below the build limit the void starts (default -64). */
@Mixin(Entity.class)
public abstract class VoidHeightOptionMixin {
    @ModifyConstant(method = "checkBelowWorld", constant = @Constant(intValue = 64), require = 0)
    private int lunararc$voidStartsBelow(int vanilla) {
        double offset = WorldConfiguration.forLevel(((Entity) (Object) this).level()).tuning.voidDamageMinBuildHeightOffset;
        return (int) -offset;
    }
}
