package io.lunararcdevs.lunararc.common.mixin.core.world.block;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CactusBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Paper world option max-growth-height.cactus: how tall a cactus may grow (default 3). */
@Mixin(CactusBlock.class)
public abstract class CactusGrowthHeightOptionMixin {
    @ModifyExpressionValue(method = "randomTick", require = 0,
            at = @At(value = "CONSTANT", args = "intValue=3", ordinal = 0))
    private int lunararc$cactusHeight(int vanilla, @Local(argsOnly = true) ServerLevel level) {
        return WorldConfiguration.forLevel(level).tuning.maxGrowthHeightCactus;
    }
}
