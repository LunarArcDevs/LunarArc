package io.lunararcdevs.lunararc.neoforge.mixin.compat;

import io.lunararcdevs.lunararc.common.compat.SableCompatibility;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class SableSpectatorTrackingClearMixin {

    @Inject(method = "setGameMode", at = @At("HEAD"))
    private void lunararc$clearSableTrackingOnSpectate(GameType gameType, CallbackInfoReturnable<Boolean> cir) {
        if (gameType == GameType.SPECTATOR) {
            SableCompatibility.clearTrackingSubLevelIfPresent((ServerPlayer) (Object) this);
        }
    }
}
