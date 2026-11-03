package io.lunararcdevs.lunararc.common.mixin.core.world;

import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Paper world options environment.disable-thunder and environment.disable-ice-and-snow. */
@Mixin(ServerLevel.class)
public abstract class WorldEnvironmentOptionsMixin {
    @Shadow @Final private ServerLevelData serverLevelData;

    /** environment.disable-ice-and-snow: no freezing of water and no snow layers from precipitation. */
    @Inject(method = "tickPrecipitation", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$noIceAndSnow(BlockPos pos, CallbackInfo ci) {
        if (WorldConfiguration.forLevel((ServerLevel) (Object) this).tuning.disableIceAndSnow) ci.cancel();
    }

    /** environment.disable-thunder: a storm never turns into a thunderstorm. */
    @Inject(method = "advanceWeatherCycle", at = @At("RETURN"), require = 0)
    private void lunararc$noThunder(CallbackInfo ci) {
        if (WorldConfiguration.forLevel((ServerLevel) (Object) this).tuning.disableThunder && this.serverLevelData.isThundering()) {
            this.serverLevelData.setThundering(false);
        }
    }
}
