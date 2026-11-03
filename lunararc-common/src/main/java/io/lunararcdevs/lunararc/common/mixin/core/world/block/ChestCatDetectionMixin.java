package io.lunararcdevs.lunararc.common.mixin.core.world.block;

import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.ChestBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Paper world option entities.behavior.disable-chest-cat-detection: a cat sitting on a chest no longer blocks it. */
@Mixin(ChestBlock.class)
public abstract class ChestCatDetectionMixin {
    @Inject(method = "isCatSittingOnChest", at = @At("HEAD"), cancellable = true, require = 0)
    private static void lunararc$ignoreCats(LevelAccessor level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof Level real && WorldConfiguration.forLevel(real).tuning.disableChestCatDetection) cir.setReturnValue(false);
    }
}
