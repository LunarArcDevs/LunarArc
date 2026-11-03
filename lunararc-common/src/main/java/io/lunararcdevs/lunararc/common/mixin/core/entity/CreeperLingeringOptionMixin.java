package io.lunararcdevs.lunararc.common.mixin.core.entity;

import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Paper world option entities.behavior.disable-creeper-lingering-effect: a potion-effect creeper leaves no lingering cloud. */
@Mixin(Creeper.class)
public abstract class CreeperLingeringOptionMixin {
    @Inject(method = "spawnLingeringCloud", at = @At("HEAD"), cancellable = true, require = 0)
    private void lunararc$noLingeringCloud(CallbackInfo ci) {
        if (WorldConfiguration.forLevel(((Creeper) (Object) this).level()).tuning.disableCreeperLingeringEffect) ci.cancel();
    }
}
