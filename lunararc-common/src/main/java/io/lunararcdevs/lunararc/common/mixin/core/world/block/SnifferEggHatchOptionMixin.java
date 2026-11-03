package io.lunararcdevs.lunararc.common.mixin.core.world.block;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SnifferEggBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Paper world options entities.sniffer.hatch-time (default 24000) and boosted-hatch-time (default 12000): ticks until a sniffer egg hatches. */
@Mixin(SnifferEggBlock.class)
public abstract class SnifferEggHatchOptionMixin {
    @ModifyExpressionValue(method = "onPlace", require = 0, at = @At(value = "CONSTANT", args = "intValue=24000"))
    private int lunararc$hatchTime(int vanilla, @Local(argsOnly = true) Level level) {
        return WorldConfiguration.forLevel(level).tuning.snifferHatchTime;
    }

    @ModifyExpressionValue(method = "onPlace", require = 0, at = @At(value = "CONSTANT", args = "intValue=12000"))
    private int lunararc$boostedHatchTime(int vanilla, @Local(argsOnly = true) Level level) {
        return WorldConfiguration.forLevel(level).tuning.snifferBoostedHatchTime;
    }
}
