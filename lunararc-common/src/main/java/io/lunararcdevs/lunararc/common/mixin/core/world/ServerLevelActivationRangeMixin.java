package io.lunararcdevs.lunararc.common.mixin.core.world;

import io.lunararcdevs.lunararc.common.event.LunarArcActivationRange;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** EXPERIMENTAL entity activation range (experimental.activation-range.* in lunararc.conf): a resting mob nobody is near skips its tick. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelActivationRangeMixin {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$activationRange(Entity entity, CallbackInfo callback) {
        if (LunarArcActivationRange.shouldSkip((ServerLevel) (Object) this, entity)) callback.cancel();
    }
}
