package io.lunararcdevs.lunararc.common.mixin.core.world.block;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.SugarCaneBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Paper world option max-growth-height.reeds: how tall sugar cane may grow (default 3). */
@Mixin(SugarCaneBlock.class)
public abstract class ReedsGrowthHeightOptionMixin {
    @ModifyExpressionValue(method = "randomTick", require = 0,
            at = @At(value = "CONSTANT", args = "intValue=3", ordinal = 0))
    private int lunararc$reedsHeight(int vanilla, @Local(argsOnly = true) ServerLevel level) {
        return WorldConfiguration.forLevel(level).tuning.maxGrowthHeightReeds;
    }
}
